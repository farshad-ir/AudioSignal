from flask import Flask, request, jsonify
import base64
import os
import uuid
from datetime import datetime
import threading
import subprocess

from vosk import Model, KaldiRecognizer
import wave
import json


app = Flask(__name__)

# مسیر ذخیره فایل‌های دریافتی
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

MAX_TOKENS = 8
active_tokens = {}  # token_id -> job_id
status_store = {}   # job_id -> status dict




def acquire_token(job_id):
    if len(active_tokens) >= MAX_TOKENS:
        return None
    token_id = str(uuid.uuid4())
    active_tokens[token_id] = job_id
    return token_id

def release_token(token_id):
    active_tokens.pop(token_id, None)

def update_status(job_id, status, extra=None):
    if job_id not in status_store:
        return
    status_store[job_id]["status"] = status
    if extra:
        status_store[job_id].update(extra)

@app.route("/kill_job/<job_id>", methods=["POST"])
def kill_job(job_id):
    # پیدا کردن توکن مرتبط با job_id
    token_to_remove = None
    for token, jid in active_tokens.items():
        if jid == job_id:
            token_to_remove = token
            break

    # آزاد کردن توکن (اگر پیدا شد)
    if token_to_remove:
        release_token(token_to_remove)

    # پاک‌کردن وضعیت job از status_store (یا تنظیم به حالت خاص)
    if job_id in status_store:
        status_store[job_id]["status"] = "killed"
        return jsonify({"status": "killed", "job_id": job_id}), 200
    else:
        return jsonify({"status": "not_found", "message": "Job ID not found"}), 404



@app.route('/log_from_client', methods=['POST'])
def log_from_client():
    try:
        log_dir = 'logs'
        os.makedirs(log_dir, exist_ok=True)

        log_text = request.get_data(as_text=True)
        log_file = os.path.join(log_dir, 'mobile_log.txt')

        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(log_text + '\n')

        return 'ok', 200
    except Exception as e:
        return str(e), 500
        
        
        

@app.route("/upload_text", methods=["POST"])
def upload_text():
    data = request.get_json()
    user_text = data.get("text")

    if not user_text:
        return jsonify({"status": "error", "message": "No text received"}), 400

    print(f"[✓] Text received: {user_text}")
    return jsonify({"status": "ok", "message": "Text received"}), 200


@app.route("/test_server", methods=["POST"])
def test_server():
    print("[✓] Test server called")
    return jsonify({"status": "ok", "message": "Server is reachable"}), 200


@app.route("/job_status/<job_id>", methods=["POST"])
def job_status(job_id):
    info = status_store.get(job_id)
    if not info:
        return jsonify({"status": "not_found", "message": "Job ID not found"}), 404

    # اضافه کردن زمان فعلی سرور
    info_with_time = dict(info)
    info_with_time["server_time"] = datetime.utcnow().isoformat()

    return jsonify({"status": "ok", "job": info_with_time}), 200
    
    
    
       
@app.route("/upload_audio_chunk", methods=["POST"])
def upload_audio_chunk():
    data = request.get_json()
    chunk_base64 = data.get("chunk_base64")
    filename = data.get("filename")
    chunk_index = data.get("chunk_index")
    is_last_chunk = data.get("is_last_chunk")
    job_id = data.get("job_id")

    if chunk_base64 is None or filename is None or chunk_index is None or is_last_chunk is None:
        return jsonify({"status": "error", "message": "Missing parameters"}), 400

    new_job = False
    
    
    if job_id is None or chunk_index == 0:
        job_id = str(uuid.uuid4())
        token_id = acquire_token(job_id)
        if not token_id:
            return jsonify({"status": "error", "message": "Server is busy, try later"}), 429
        
        # وضعیت را ذخیره می‌کنیم
        status_store[job_id] = {
            "status": "receiving",
            "filename": filename,
            "token_id": token_id,
            "timestamp": datetime.utcnow().isoformat(),
        }
        print(f"[✓] New job started: {job_id}")
        new_job = True
        

        
    else:
        token_id = status_store.get(job_id, {}).get("token_id")
        if not token_id:
            return jsonify({"status": "error", "message": "Invalid job ID"}), 400





    try:
        audio_chunk = base64.b64decode(chunk_base64)
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        with open(filepath, "ab") as f:
            f.write(audio_chunk)

        print(f"[✓] Chunk {chunk_index} appended to {filepath}")
        
        # 🔹 اگر آخرین چانک است، عملیات را در ترد جداگانه انجام بده
        if is_last_chunk:
            def process_job():
                try:
                    print(f"[✓] Received last chunk for file {filename}")
                    print(f"[✓] Starting processing job: {job_id}, file: {filename}")

                    input_path = filepath
                    base_name = os.path.splitext(filename)[0]
                    output_filename = base_name + ".wav"
                    output_path = os.path.join("processed", output_filename)
                    os.makedirs("processed", exist_ok=True)
                    
                    status_store[job_id]["status"] = "converting wav"
                    
                    command = [
                            "ffmpeg", 
                            "-y", 
                            "-i", 
                            input_path, 
                            "-ar", "16000", "-ac", "1", 
                            output_path
                            ]
                            
                    #ffmpeg        
                    subprocess.run(command, check=True)
                    print(f"[✓] Converted to WAV: {output_path}")
                    
                    status_store[job_id]["status"] = "transcribing farsi"
                    
                    # اجرای ووسک
                    status_store[job_id]["status"] = "transcribing farsi"

                    # مسیر مدل فارسی vosk
                    vosk_model_path = "models/fa/vosk-model-small-fa-0.5"
                    model = Model(vosk_model_path)

                    # باز کردن فایل WAV
                    wf = wave.open(output_path, "rb")
                    
                    words = [
                        "خرید", "فروش", "یورودلار", "سطح", "بالا", "پایین", "قیمت",
                        "تایید", "برداشت", "سود", "اول", "دوم", "سوم", 
                        "استاپلاس", "زیر", "بزار"
                    ]

                    
                    
                    # تبدیل لیست به JSON string
                    limited_vocab = json.dumps(words, ensure_ascii=False)
                    
                    rec = KaldiRecognizer(model, wf.getframerate())
                    # recognizer با واژگان محدود
                    # rec = KaldiRecognizer(model, wf.getframerate(), limited_vocab)

                    transcribed_text = ""
                    while True:
                        data = wf.readframes(4000)
                        if len(data) == 0:
                            break
                        if rec.AcceptWaveform(data):
                            result = json.loads(rec.Result())
                            transcribed_text += result.get("text", "") + " "
                    # نتیجه نهایی (برای کلمات باقی‌مانده در انتها)
                    final_result = json.loads(rec.FinalResult())
                    transcribed_text += final_result.get("text", "")

                    wf.close()

                    # چاپ وضعیت
                    print(f"[✓] Vosk done for job {job_id}")


                    # مسیر ذخیره ترنسکریپت
                    os.makedirs("transcripts", exist_ok=True)
                    transcript_path = os.path.join("transcripts", base_name + ".txt")

                    # ذخیره فایل متنی
                    with open(transcript_path, "w", encoding="utf-8") as f:
                        f.write(transcribed_text.strip())

                    # خواندن و قرار دادن در status_store
                    if os.path.exists(transcript_path):
                        with open(transcript_path, "r", encoding="utf-8") as f:
                            transcript_text = f.read()

                        status_store[job_id]["status"] = "done"
                        status_store[job_id]["transcript"] = transcript_text
                    else:
                        status_store[job_id]["status"] = "error"
                        status_store[job_id]["error"] = "Transcript file not found"




                   

                except Exception as e:
                    import traceback
                    print(f"[✗] Error during background processing: {e}")
                    traceback.print_exc()



            threading.Thread(target=process_job).start()

    # 🔹 برگشت به موبایل در هر صورت
    


        
    except Exception as e:
        print(f"[✗] Error in upload_audio_chunk: {e}")
        return jsonify({"status": "error", "message": "Internal server error"}), 500



    if new_job:        
        return jsonify({
            "status": "ok",
            "message": f"Chunk {chunk_index} received",
            "job_id": job_id
        }), 200
    else:
        return jsonify({
            "status": "ok", 
            "message": "Chunk received"
        }), 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
