from flask import Flask, request, jsonify
import base64
import os
import uuid
from datetime import datetime
import threading


app = Flask(__name__)

# مسیر ذخیره فایل‌های دریافتی
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

MAX_TOKENS = 5
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


@app.route("/job_status/<job_id>", methods=["GET"])
def job_status(job_id):
    info = status_store.get(job_id)
    if not info:
        return jsonify({"status": "not_found", "message": "Job ID not found"}), 404
    return jsonify({"status": "ok", "job": info}), 200


       
@app.route("/upload_audio_chunk", methods=["POST"])
def upload_audio_chunk():
    data = request.get_json()
    chunk_base64 = data.get("chunk_base64")
    filename = data.get("filename")
    chunk_index = data.get("chunk_index")
    is_last_chunk = data.get("is_last_chunk")
    
    # 👇 اضافه کردن job_id (از موبایل میاد، یا اولین بار جنریت میشه)
    job_id = data.get("job_id")

    if chunk_base64 is None or filename is None or chunk_index is None or is_last_chunk is None:
        return jsonify({"status": "error", "message": "Missing parameters"}), 400

    # 👇 اولین chunk؟ پس job_id بساز و token بگیر
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
    else:
        token_id = status_store.get(job_id, {}).get("token_id")
        if not token_id:
            return jsonify({"status": "error", "message": "Invalid job ID"}), 400





    try:
        audio_chunk = base64.b64decode(chunk_base64)
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        # به صورت append فایل را باز می‌کنیم و قطعه را اضافه می‌کنیم
        with open(filepath, "ab") as f:
            f.write(audio_chunk)

        print(f"[✓] Chunk {chunk_index} appended to {filepath}")

        if is_last_chunk:
            print(f"[✓] Received last chunk for file {filename}")

        return jsonify({"status": "ok", "message": f"Chunk {chunk_index} received"}), 200
    except Exception as e:
        print("[!] Error decoding or saving chunk:", e)
        return jsonify({"status": "error", "message": str(e)}), 500




if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
