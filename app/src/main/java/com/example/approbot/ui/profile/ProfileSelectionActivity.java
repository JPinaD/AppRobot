package com.example.approbot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.approbot.R;
import com.example.approbot.data.model.LocalStudentProfile;

import java.util.Arrays;
import java.util.List;

public class ProfileSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_selection);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            // Si hay actividad anterior, vuelve atrás; si no, navega a RobotSplashActivity
            if (isTaskRoot()) {
                Intent intent = new Intent(ProfileSelectionActivity.this, com.example.approbot.ui.splash.RobotSplashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                finish();
            }
        });

        // Perfiles ficticios
        List<LocalStudentProfile> profiles = Arrays.asList(
                new LocalStudentProfile("Ana", "Perfil para niños pequeños"),
                new LocalStudentProfile("Luis", "Perfil para adolescentes"),
                new LocalStudentProfile("Sofía", "Perfil para adultos mayores"),
                new LocalStudentProfile("Carlos", "Perfil para adultos activos")
        );

        RecyclerView recyclerView = findViewById(R.id.profileRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ProfileSelectionAdapter(profiles, profile -> {
            Intent intent = new Intent(ProfileSelectionActivity.this, com.example.approbot.ui.waiting.WaitingSessionActivity.class);
            intent.putExtra("selectedProfile", profile.name);
            startActivity(intent);
            // Quitar finish() para que la actividad permanezca en la pila
        }));
    }
}