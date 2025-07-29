package com.example.AudioSignal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import android.content.pm.PackageManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class MainActivity extends Activity {

    EditText txtBox;
    Button btnSend, btnClear, btnRecord, btnSettings;
    LinearLayout rootLayout;

    boolean isRecording = false;
    MediaRecorder recorder;
    String audioPath;

	private String currentFileName;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ساخت کل طرح‌بندی
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(32, 48, 32, 32);  // فاصله داخلی بیشتر برای ظاهر بهتر

        // عنوان بالا
        TextView titleText = new TextView(this);
        titleText.setText("ارسال ویس برای متاتریدر");
        titleText.setTextSize(24);
        titleText.setGravity(Gravity.CENTER_HORIZONTAL);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setPadding(0, 0, 0, 32);
        rootLayout.addView(titleText);

        // باکس متنی
        txtBox = new EditText(this);
        txtBox.setHint("اینجا بنویسید یا پیام سرور را ببینید...");
        txtBox.setMinLines(5);
        txtBox.setGravity(Gravity.TOP);
        txtBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        txtBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f));
        rootLayout.addView(txtBox);

        // ردیف دکمه‌های پاک‌کردن و ارسال
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnClear = new Button(this);
        btnClear.setText("پاک‌کردن");
        btnClear.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnClear.setOnClickListener(v -> txtBox.setText(""));

        btnSend = new Button(this);
        btnSend.setText("ارسال متن");
        btnSend.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnSend.setOnClickListener(v -> sendTextToServer(txtBox.getText().toString()));

        buttonRow.addView(btnClear);
        buttonRow.addView(btnSend);
        rootLayout.addView(buttonRow);

        // فاصله
        Space spacer = new Space(this);
        spacer.setMinimumHeight(32);
        rootLayout.addView(spacer);

        // دکمه ضبط ویس
        btnRecord = new Button(this);
        btnRecord.setText("ضبط ویس");
        btnRecord.setBackgroundColor(Color.LTGRAY);
        btnRecord.setTextSize(18);
        btnRecord.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btnRecord.setOnClickListener(v -> toggleRecording());
        rootLayout.addView(btnRecord);

        // دکمه تنظیمات
        btnSettings = new Button(this);
        btnSettings.setText("تنظیمات");
        btnSettings.setTextSize(18);
        btnSettings.setBackgroundColor(Color.LTGRAY);
        btnSettings.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        rootLayout.addView(btnSettings);

        setContentView(rootLayout);
    }

    private void sendTextToServer(String text) {
        String serverUrl = PrefManager.getServerUrl(this);
        if (serverUrl.isEmpty()) {
            txtBox.setText("آدرس سرور تنظیم نشده است.");
            return;
        }

        String fullUrl = serverUrl + "/upload_text";

        new Thread(() -> {
            try {
                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                String jsonBody = "{\"text\": \"" + text.replace("\"", "\\\"") + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                Scanner s = new Scanner((responseCode == 200) ? conn.getInputStream() : conn.getErrorStream()).useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";

                runOnUiThread(() -> txtBox.setText("پاسخ سرور:\n" + response));
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> txtBox.setText("خطا در ارسال متن:\n" + e.getMessage()));
            }
        }).start();
    }

    private void toggleRecording() {
        if (!isRecording) {
            checkPermissionAndStartRecording();
        } else {
            stopRecordingAndSend();
        }
    }






private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

private void checkPermissionAndStartRecording() {
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        // مجوز وجود ندارد، درخواست مجوز بده
        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    } else {
        // مجوز وجود دارد، شروع به ضبط کن
        startRecording();
    }
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // مجوز داده شده، ضبط رو شروع کن
            startRecording();
        } else {
            // مجوز داده نشده، پیام خطا یا هشدار به کاربر بده
            txtBox.setText("مجوز ضبط صدا داده نشده است.");
        }
    }
}













    private void startRecording() {
        audioPath = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/recorded_audio.3gp";

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(audioPath);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            btnRecord.setText("در حال ضبط");
            btnRecord.setBackgroundColor(Color.BLUE);
            txtBox.setText("در حال ضبط صدا...");
        } catch (IOException e) {
            txtBox.setText("خطا در ضبط صدا: " + e.getMessage());
        }
    }

    private void stopRecordingAndSend() {
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            btnRecord.setText("ضبط ویس");
            btnRecord.setBackgroundColor(Color.LTGRAY);
            txtBox.setText("در حال ارسال ویس...");

            File audioFile = new File(audioPath);
            sendAudioInChunks(audioFile);
        } catch (Exception e) {
            txtBox.setText("خطا در پایان ضبط: " + e.getMessage());
        }
    }

    private void sendAudioInChunks(File file) {
        new Thread(() -> {
            try {
                byte[] fullData = readFileToByteArray(file);
                int chunkSize = 500 * 1024;
                int totalChunks = (int) Math.ceil(fullData.length / (double) chunkSize);
				
				currentFileName = generateFileName();
				
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(fullData.length, (i + 1) * chunkSize);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(fullData, start, chunk, 0, end - start);

                    String base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP);
                    boolean isLastChunk = (i == totalChunks - 1);

                    boolean isSent = sendAudioChunkToServer(base64Chunk, currentFileName, i, isLastChunk);
					if (!isSent) {
						throw new IOException("خطا در ارسال قطعه شماره " + i);
					}
					
					
					//if (isLastChunk) {
					//	file.delete();
					//}

                    int percent = (int) (((i + 1) / (float) totalChunks) * 100);
                    int finalI = i;
                    runOnUiThread(() -> txtBox.setText("ارسال قطعه " + (finalI + 1) + "/" + totalChunks + " (" + percent + "%)"));
                }

                runOnUiThread(() -> txtBox.append("\nهمه قطعات ارسال شدند."));
            } catch (Exception e) {
                runOnUiThread(() -> txtBox.setText("خطا در ارسال ویس:\n" + e.getMessage()));
            }
        }).start();
    }

	private boolean sendAudioChunkToServer(String base64Audio, String filename, int chunkIndex, boolean isLastChunk) {
		String serverUrl = PrefManager.getServerUrl(this);
		if (serverUrl.isEmpty()) return false;

		String fullUrl = serverUrl + "/upload_audio_chunk";

		try {
			URL url = new URL(fullUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");

			String jsonBody = String.format(
					"{\"chunk_base64\":\"%s\", \"filename\":\"%s\", \"chunk_index\":%d, \"is_last_chunk\":%s}",
					base64Audio, filename, chunkIndex, isLastChunk ? "true" : "false"
			);

			OutputStream os = conn.getOutputStream();
			os.write(jsonBody.getBytes("UTF-8"));
			os.close();

			int responseCode = conn.getResponseCode();
			conn.disconnect();

			return (responseCode == 200);  // فرض می‌کنیم 200 یعنی موفق
		} catch (Exception e) {
			// اگر می‌خواهید می‌توانید اینجا لاگ هم بگیرید
			e.printStackTrace();
			return false;
		}
	}

	
	private String generateFileName() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
		String dateTime = sdf.format(new Date());
		int randomNum = new Random().nextInt(1000);  // عدد تصادفی بین 0 تا 999
		return "audio_" + dateTime + "_" + randomNum + ".3gp";
	}
	
    private byte[] readFileToByteArray(File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        fis.close();
        return bos.toByteArray();
    }
}
