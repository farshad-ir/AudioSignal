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
import difflib

from dotenv import load_dotenv
import openai

openai.api_key = "sk-xxxxxxxxxxxxxxxxxxxxxxxx"


app = Flask(__name__)

# مسیر ذخیره فایل‌های دریافتی
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

MAX_TOKENS = 8
active_tokens = {}  # token_id -> job_id
status_store = {}   # job_id -> status dict






def merge_transcripts(limited_text, free_text, vocab):
    limited_words = limited_text.strip().split()
    free_words = free_text.strip().split()
    merged_words = []
    
    system_prompt = "شما یک ویرایشگر هوشمند فارسی هستید که می‌خواهید از دو نسخه‌ی ترنسکریپت، یک نسخه‌ی نهایی و دقیق بسازید."
    
    user_prompt = f"""
شما دو نسخه ترنسکریپت صوتی دارید:

نسخه با واژگان محدود (ممکن است ناقص باشد):
\"\"\"{limited_text}\"\"\"

نسخه آزاد (ممکن است اشتباهاتی داشته باشد):
\"\"\"{free_text}\"\"\"

واژگان پیشنهادی برای دقت بیشتر:
{', '.join(vocab)}

لطفاً یک نسخه نهایی، اصلاح‌شده، روان و دقیق تولید کنید که از هر دو استفاده کرده باشد و معنی را منتقل کند.
"""

    response = openai.ChatCompletion.create(
        model="gpt-3.5-turbo",
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ],
        temperature=0.4,
        max_tokens=1000
    )
    
    final_text = response['choices'][0]['message']['content'].strip()
    
    merged_words = final_text.split()
    return " ".join(merged_words)


def merge_transcripts(limited_text, free_text, vocab):
    limited_words = limited_text.strip().split()
    free_words = free_text.strip().split()
    merged_words = []

    for word in free_words:
        if word in vocab:
            merged_words.append(word)
        else:
            matches = difflib.get_close_matches(word, limited_words, n=1, cutoff=0.8)
            if matches:
                merged_words.append(matches[0])
            else:
                merged_words.append(word)

    return " ".join(merged_words)
                    
                    
def transcribe_with_vosk(wav_path, model_path, grammar=None):
    wf = wave.open(wav_path, "rb")
    if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getcomptype() != "NONE":
        raise ValueError("Audio file must be WAV mono PCM.")
    
    model = Model(model_path)
    rec = KaldiRecognizer(model, wf.getframerate(), grammar) if grammar else KaldiRecognizer(model, wf.getframerate())
    rec.SetWords(True)

    result_text = ""
    while True:
        data = wf.readframes(4000)
        if len(data) == 0:
            break
        if rec.AcceptWaveform(data):
            res = json.loads(rec.Result())
            result_text += res.get("text", "") + " "
    res = json.loads(rec.FinalResult())
    result_text += res.get("text", "")
    wf.close()
    
    return result_text.strip()


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





                    
                    
                    # شروع اجرای ووسک 
                    #
                    
                    status_store[job_id]["status"] = "transcribing farsi"

                    # مسیر مدل فارسی vosk
                    vosk_model_path = "models/fa/vosk-model-small-fa-0.5"
                   

                    # نام مسیر ها
                    base_name = os.path.splitext(filename)[0]
                    transcript_dir = "transcripts"
                    os.makedirs(transcript_dir, exist_ok=True)

                    vocab_list = [
                        "خرید", "فروش", "یورودلار", "سطح", "بالا", "پایین", "قیمت", "یورو", "دلار",
                        "تایید", "برداشت", "سود", "اول", "دوم", "سوم", "نماد", 
                        "استاپلاس", "زیر", "بزار"
                    ]    
                    
                    # ----------------- Phase 1: With grammar -----------------
                    
                    grammar = '["' + '", "'.join(vocab_list) + '"]'

                    phase1_text = transcribe_with_vosk(output_path, vosk_model_path, grammar)
                    phase1_file = os.path.join(transcript_dir, f"{base_name}_phase1.txt")
                    with open(phase1_file, "w", encoding="utf-8") as f:
                        f.write(phase1_text)
                    print(f"[✓] Phase 1 done: grammar-based transcript created.")

                    # ----------------- Phase 2: Free transcription -----------------
                    phase2_text = transcribe_with_vosk(output_path, vosk_model_path, grammar=None)
                    phase2_file = os.path.join(transcript_dir, f"{base_name}_phase2.txt")
                    with open(phase2_file, "w", encoding="utf-8") as f:
                        f.write(phase2_text)
                    print(f"[✓] Phase 2 done: free transcript created.")

                    # ----------------- Phase 3: Merge results -----------------
                    final_text = merge_transcripts(phase1_text, phase2_text, vocab_list)
                    final_file = os.path.join(transcript_dir, f"{base_name}_final.txt")
                    with open(final_file, "w", encoding="utf-8") as f:
                        f.write(final_text)
                    print(f"[✓] Phase 3 done: final transcript merged.")

                    # ذخیره در job status
                    status_store[job_id]["status"] = "done"
                    status_store[job_id]["transcript"] = final_text

                    


                    
                    





                   

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
