package com.example.AudioSignal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SettingsActivity extends Activity {

    EditText txtServerUrl;
    Button btnSaveAndBack, btnTestServer;
    LinearLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_VERTICAL);
        rootLayout.setPadding(16, 16, 16, 16);

        txtServerUrl = new EditText(this);
        txtServerUrl.setHint("آدرس سرور را وارد کنید...");
        txtServerUrl.setTextSize(16);
        txtServerUrl.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rootLayout.addView(txtServerUrl);

        // لایه افقی برای دکمه‌ها
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        // فاصله عمودی از بالا (می‌تونید کم یا زیادش کنید)
        buttonRowParams.setMargins(0, 24, 0, 0);
        buttonRow.setLayoutParams(buttonRowParams);

        // پارامترهای یکسان برای هر دکمه: عرض 0 با وزن 1 برای تقسیم مساوی فضای افقی
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        // فاصله بین دکمه‌ها (مثلا 12 پیکسل)
        btnParams.setMargins(0, 0, 12, 0);

        btnSaveAndBack = new Button(this);
        btnSaveAndBack.setText("ذخیره و بازگشت");
        btnSaveAndBack.setBackgroundColor(Color.LTGRAY);
        btnSaveAndBack.setLayoutParams(btnParams);
        btnSaveAndBack.setOnClickListener(v -> {
            String url = txtServerUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                saveServerUrl(url);
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "آدرس نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show();
            }
        });

        btnTestServer = new Button(this);
        btnTestServer.setText("تست سرور");
        btnTestServer.setBackgroundColor(Color.LTGRAY);
        // برای دکمه دوم حاشیه راست صفر چون آخرین دکمه است
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnTestServer.setLayoutParams(btnParams2);
		btnTestServer.setOnClickListener(v -> {
			String url = txtServerUrl.getText().toString().trim();
				if (!url.isEmpty()) {
					new Thread(() -> testServer(url)).start();
				}
		});

        buttonRow.addView(btnSaveAndBack);
        buttonRow.addView(btnTestServer);

        rootLayout.addView(buttonRow);

        setContentView(rootLayout);

        loadSavedUrl();
    }

    private void saveServerUrl(String url) {
        PrefManager.setServerUrl(this, url);
    }

    private void loadSavedUrl() {
        String url = PrefManager.getServerUrl(this);
        txtServerUrl.setText(url);
    }

	private void testServer(String baseUrl) {
		try {
			URL url = new URL(baseUrl + "/test_server");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.connect();

			int responseCode = conn.getResponseCode();
			InputStream inputStream = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}

			String response = sb.toString();
			reader.close();

			JSONObject json = new JSONObject(response);
			String status = json.getString("status");

			runOnUiThread(() -> {
				if (responseCode == 200 && status.equals("ok")) {
					btnTestServer.setBackgroundColor(Color.GREEN);
					Toast.makeText(this, "اتصال موفقیت‌آمیز بود", Toast.LENGTH_SHORT).show();
				} else {
					btnTestServer.setBackgroundColor(Color.LTGRAY);
					Toast.makeText(this, "اتصال ناموفق یا پاسخ نامعتبر", Toast.LENGTH_SHORT).show();
				}
			});
		} catch (Exception e) {
			runOnUiThread(() -> {
				btnTestServer.setBackgroundColor(Color.LTGRAY);
				Toast.makeText(this, "خطا در اتصال: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			});
		}
	}

}
