package com.example.vibraspeak;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private TextView loadingText;
    private int dotCount = 0;
    private final Handler handler = new Handler();
    private final Runnable dotRunnable = new Runnable() {
        @Override
        public void run() {
            dotCount = (dotCount + 1) % 4;
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) {
                dots.append(".");
            }
            loadingText.setText("Loading" + dots);
            handler.postDelayed(this, 500);
        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        loadingText = findViewById(R.id.loadingText);
        handler.post(dotRunnable);

        new Handler().postDelayed(() -> {
            handler.removeCallbacks(dotRunnable);
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 3000);
    }
}