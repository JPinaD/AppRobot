package com.example.approbot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.approbot.R;
import com.example.approbot.ui.waiting.WaitingSessionActivity;
import com.example.approbot.viewmodel.ProfileSelectionViewModel;

public class ProfileSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_selection);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            if (isTaskRoot()) {
                Intent intent = new Intent(this, com.example.approbot.ui.splash.RobotSplashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            finish();
        });

        RecyclerView rvProfiles = findViewById(R.id.rvProfiles);
        rvProfiles.setLayoutManager(new LinearLayoutManager(this));

        ProfileSelectionAdapter adapter = new ProfileSelectionAdapter(profile -> {
            Intent intent = new Intent(this, WaitingSessionActivity.class);
            intent.putExtra("profile_id", profile.id);
            intent.putExtra("profile_name", profile.name);
            intent.putExtra("profile_description", profile.description);
            startActivity(intent);
        });
        rvProfiles.setAdapter(adapter);

        ProfileSelectionViewModel viewModel = new ViewModelProvider(this).get(ProfileSelectionViewModel.class);
        viewModel.profiles.observe(this, adapter::setProfiles);
    }
}
