package com.example.vibraspeak;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private VideoView splashVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashVideo = findViewById(R.id.splashVideoView);

        // Set video path
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.logo);
        splashVideo.setVideoURI(videoUri);
        splashVideo.start();

        // Transition after 3 seconds
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 3000);
    }
}
