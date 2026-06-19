package com.example.approbot.ui.sessionend;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.approbot.R;
import com.example.approbot.ui.waiting.WaitingSessionActivity;

public class SessionEndActivity extends AppCompatActivity {

    private static final long AUTO_FINISH_DELAY_MS = 3000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_end);

        // Toque en cualquier zona → volver
        findViewById(android.R.id.content).setOnClickListener(v -> goBack());

        // Auto-cierre tras 3 segundos
        handler.postDelayed(this::goBack, AUTO_FINISH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goBack();
    }

    private void goBack() {
        if (finished) return;
        finished = true;
        // Volver a WaitingSession limpiando el back stack de PictogramActivity
        Intent intent = new Intent(this, WaitingSessionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
