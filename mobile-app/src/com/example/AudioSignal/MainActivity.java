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
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONObject;

import java.util.TimeZone;


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
	private String currentJobId;
	private Boolean weAreReady;
	
	private Timer pollingTimer;
	private long jobStartTime;
	
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
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	private int pollingCounter = 0;  // تعداد دفعات اجرای پولینگ
	private final int MAX_POLLING_COUNT = 300;  // 5 دقیقه = 60 بار × 5 ثانیه

	private void startPollingJobStatusWithTimer() {
		if (pollingTimer != null) return;  // جلوگیری از اجرای مجدد

		pollingCounter = 0;

		pollingTimer = new Timer();
		pollingTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				pollingCounter++;


				if (currentJobId != null && !currentJobId.isEmpty() && weAreReady) {
					pullServer_for_Status(currentJobId);
				}

				if (pollingCounter >= MAX_POLLING_COUNT) {
					stopPollingJobStatusWithTimer();
				}
			}
		}, 0, 10000);  // اجرا هر ۵ ثانیه
	}

	private void stopPollingJobStatusWithTimer() {
		if (pollingTimer != null) {
			pollingTimer.cancel();
			pollingTimer = null;
			sendClientLog("Polling Timer stopped.");
		}
	}



	
	
	
	
	
	
	
	
	





	private void pullServer_for_Status(String jobID) {
		new Thread(() -> {
			try {
				String serverUrl = PrefManager.getServerUrl(this);
				if (serverUrl.isEmpty()) {
					runOnUiThread(() -> txtBox.setText("آدرس سرور تنظیم نشده."));
					return;
				}

				String urlStr = serverUrl + "/job_status/" + jobID;
				URL url = new URL(urlStr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/json");

				// چون سرور انتظار ندارد دیتایی ارسال شود، فقط کانکشن باز است و POST می‌شود بدون بادی

				int responseCode = conn.getResponseCode();

				InputStream is;
				if (responseCode >= 200 && responseCode < 300) {
					is = conn.getInputStream();
				} else {
					is = conn.getErrorStream();
				}

				BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				StringBuilder responseBuilder = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					responseBuilder.append(line);
				}
				reader.close();
				conn.disconnect();

				String response = responseBuilder.toString();


				// 2. پردازش پاسخ JSON
				JSONObject json = new JSONObject(response);
				if (!json.optString("status").equals("ok")) {
					runOnUiThread(() -> txtBox.setText("پاسخ نامعتبر از سرور"));
					return;
				}

				JSONObject job = json.getJSONObject("job");
				String jobStatus = job.optString("status", "نامشخص");
				String serverTimeStr = job.optString("server_time", null);

				// محاسبه اختلاف زمان
				long elapsedSeconds = (System.currentTimeMillis() - jobStartTime) / 1000;


				// ساخت پیام برای نمایش
				StringBuilder msg = new StringBuilder();
				msg.append("وضعیت: ").append(jobStatus);
				if (elapsedSeconds >= 0 && elapsedSeconds < 20000) {
					msg.append("\nزمان سپری شده: ").append(elapsedSeconds).append(" ثانیه");
				}

				// اگر پیاده‌سازی کامل شده:
				if ("done".equalsIgnoreCase(jobStatus)) {
					String transcript = job.optString("transcript", "متنی یافت نشد.");
					msg.append("\n\n--- متن پیاده‌شده ---\n").append(transcript);

					// متوقف کردن پولینگ
					stopPollingJobStatusWithTimer();
					sendClientLog("Polling Timer stopped by Server"); 
					jobStartTime = 0;
					
					// درخواست kill job
					new Thread(() -> {
						try {
							String killUrl = serverUrl + "/kill_job/" + jobID;
							HttpURLConnection killConn = (HttpURLConnection) new URL(killUrl).openConnection();
							killConn.setRequestMethod("POST");
							killConn.setDoOutput(true);
							killConn.getResponseCode(); // فقط ارسال
							killConn.disconnect();
						} catch (Exception ignored) {}
					}).start();
				}

				// نمایش در UI
				runOnUiThread(() -> txtBox.setText(msg.toString()));

















			} catch (Exception e) {
				e.printStackTrace();
				runOnUiThread(() -> txtBox.setText("خطا در دریافت وضعیت:\n" + e.getMessage()));
			}
		}).start();
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
			weAreReady 		= false ;
			currentJobId 	= null ;
            
			sendAudioInChunks(audioFile);
        } catch (Exception e) {
            txtBox.setText("خطا در پایان ضبط: " + e.getMessage());
        }
    }

    private void sendAudioInChunks(File file) {
        new Thread(() -> {
            try {
                byte[] fullData = readFileToByteArray(file);
                int chunkSize = 500 * 1024 ;
                int totalChunks = (int) Math.ceil(fullData.length / (double) chunkSize);
				
				currentFileName = generateFileName();
				
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(fullData.length, (i + 1) * chunkSize);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(fullData, start, chunk, 0, end - start);

                    String base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP);
                    boolean isLastChunk = (i == totalChunks - 1);

                    boolean isSent = sendAudioChunkToServer(base64Chunk, currentFileName, i, isLastChunk, file);
					if (!isSent) {
						throw new IOException("خطا در ارسال قطعه شماره " + i);
					}
					
					


                    int percent = (int) (((i + 1) / (float) totalChunks) * 100);
                    int finalI = i;
                    runOnUiThread(() -> txtBox.setText("ارسال قطعه " + (finalI + 1) + "/" + totalChunks + " (" + percent + "%)"));
                }

                runOnUiThread(() -> txtBox.append("\nهمه قطعات ارسال شدند."));
				weAreReady = true ;
				
				//new Thread(() -> sendClientLog("We want to Start WeAReReady, was it successfull?")).start();
				jobStartTime = System.currentTimeMillis();
				
				startPollingJobStatusWithTimer();
				
				
            } catch (Exception e) {
                runOnUiThread(() -> txtBox.setText("خطا در ارسال ویس:\n" + e.getMessage()));
            }
        }).start();
    }

	private boolean sendAudioChunkToServer(String base64Audio, String filename, int chunkIndex, boolean isLastChunk, File orig) {
		String serverUrl = PrefManager.getServerUrl(this);
		if (serverUrl.isEmpty()) return false;

		String fullUrl = serverUrl + "/upload_audio_chunk";

		try {
			URL url = new URL(fullUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");

			String jobIdToSend = (currentJobId == null || currentJobId.isEmpty()) ? "null" : "\"" + currentJobId + "\"";

			String jsonBody = String.format(
				"{\"chunk_base64\":\"%s\", \"filename\":\"%s\", \"chunk_index\":%d, \"is_last_chunk\":%s, \"job_id\":%s}",
				base64Audio, filename, chunkIndex, isLastChunk ? "true" : "false", jobIdToSend
			);


			OutputStream os = conn.getOutputStream();
			os.write(jsonBody.getBytes("UTF-8"));
			os.close();




			if (isLastChunk) {
				orig.delete();
			}
			
			int responseCode = conn.getResponseCode();

			// new: خواندن پاسخ سرور حتی در حالت خطا
			InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			StringBuilder responseBuilder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				responseBuilder.append(line);
			}
			reader.close();
			conn.disconnect();
			
			
			
			
			String response = responseBuilder.toString();


			if (responseCode == 200) {
				JSONObject jsonResponse = new JSONObject(response);
				if (jsonResponse.has("job_id")) {
					currentJobId = jsonResponse.getString("job_id");
				}
				//sendClientLog("Good or BAD", false);

				return true;
			}
			else {
				// new: اگر سرور مشغوله یا خطا داد، فایل اصلی را حذف کن و ارسال رو متوقف کن
				JSONObject jsonResponse = new JSONObject(response);
				String message = jsonResponse.optString("message", "");
				runOnUiThread(() -> txtBox.append("\nخطا از سرور: " + message));
				
				orig.delete();  // new: حذف فایل در هر حالت خطا (مثل مشغول بودن)

				//sendClientLog(chunkIndex, currentJobId, responseCode, response);

				return false;    // ارسال ناموفق
			}
			
			
		
		
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
	
	private void sendClientLog(String message) {
		try {
			String logText = "[ClientLog] time=" + System.currentTimeMillis()
				+ ", thread=" + Thread.currentThread().getName()
				+ ", job_id=" + currentJobId
				+ ", weAreReady=" + weAreReady
				+ ", message=" + message;

			URL logUrl = new URL(PrefManager.getServerUrl(this) + "/log_from_client");
			HttpURLConnection logConn = (HttpURLConnection) logUrl.openConnection();
			logConn.setRequestMethod("POST");
			logConn.setDoOutput(true);
			logConn.setRequestProperty("Content-Type", "text/plain");

			OutputStream osLog = logConn.getOutputStream();
			osLog.write(logText.getBytes("UTF-8"));
			osLog.close();

			logConn.getResponseCode(); // دریافت و رد پاسخ
			logConn.disconnect();
		} catch (Exception e) {
			// چون Logcat نداری، باید حتی خطاها رو هم بفرستی
			try {
				URL logUrl = new URL(PrefManager.getServerUrl(this) + "/log_from_client");
				HttpURLConnection logConn = (HttpURLConnection) logUrl.openConnection();
				logConn.setRequestMethod("POST");
				logConn.setDoOutput(true);
				logConn.setRequestProperty("Content-Type", "text/plain");

				String errorText = "[ClientLogError] time=" + System.currentTimeMillis()
					+ ", message=" + e.getMessage();

				OutputStream osLog = logConn.getOutputStream();
				osLog.write(errorText.getBytes("UTF-8"));
				osLog.close();

				logConn.getResponseCode();
				logConn.disconnect();
			} catch (Exception ignored) {}
		}
	}


	
	
}
