package com.example.approbot.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.approbot.R;
import com.example.approbot.ui.profile.ProfileSelectionActivity;

public class RobotSplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_robot_splash);

        View mainView = findViewById(R.id.main);
        mainView.setOnClickListener(v -> {
            Intent intent = new Intent(RobotSplashActivity.this, ProfileSelectionActivity.class);
            startActivity(intent);
            finish();
        });
    }
}