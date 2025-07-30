from flask import Flask, request, jsonify
import base64
import os

app = Flask(__name__)

# مسیر ذخیره فایل‌های دریافتی
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.route("/test_server", methods=["POST"])
def test_server():
    print("[✓] Test server called")
    return jsonify({"status": "ok", "message": "Server is reachable"}), 200


@app.route("/upload_audio_chunk", methods=["POST"])
def upload_audio_chunk():
    data = request.get_json()
    chunk_base64 = data.get("chunk_base64")
    filename = data.get("filename")
    chunk_index = data.get("chunk_index")
    is_last_chunk = data.get("is_last_chunk")

    if chunk_base64 is None or filename is None or chunk_index is None or is_last_chunk is None:
        return jsonify({"status": "error", "message": "Missing parameters"}), 400

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


@app.route("/upload_text", methods=["POST"])
def upload_text():
    data = request.get_json()
    user_text = data.get("text")

    if not user_text:
        return jsonify({"status": "error", "message": "No text received"}), 400

    print(f"[✓] Text received: {user_text}")
    return jsonify({"status": "ok", "message": "Text received"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
