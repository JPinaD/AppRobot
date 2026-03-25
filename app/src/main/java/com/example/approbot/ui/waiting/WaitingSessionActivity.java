package com.example.approbot.ui.waiting;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.approbot.R;

public class WaitingSessionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_session);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        TextView tvSelectedProfileName = findViewById(R.id.tvSelectedProfileName);
        TextView tvSelectedProfileDescription = findViewById(R.id.tvSelectedProfileDescription);

        String profileName = getIntent().getStringExtra("profile_name");
        String profileDescription = getIntent().getStringExtra("profile_description");

        tvSelectedProfileName.setText(profileName);
        tvSelectedProfileDescription.setText(profileDescription);
    }
}