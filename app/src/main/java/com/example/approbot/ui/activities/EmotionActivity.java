package com.example.approbot.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.EmotionViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity de Reconocimiento Emocional.
 *
 * Feedback estandarizado TEA:
 * - Acierto: fondo verde + CELEBRATE → CELEBRATE_DONE → MOVE_TIMED → MOVE_DONE → siguiente
 * - Fallo: sin color negativo + DENY → DENY_DONE → reintento
 * - Completitud: DANCE → DANCE_DONE → pantalla de felicitación
 */
public class EmotionActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_ITEMS = "activity_items";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";
    public static final String EXTRA_STEPS = "emotion_steps";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;

    // Timeout de seguridad: si no llega DONE del Arduino, avanzar igualmente
    private static final long CELEBRATE_TIMEOUT_MS = 3000;
    private static final long MOVE_TIMEOUT_MS = 2500;
    private static final long DENY_TIMEOUT_MS = 2500;
    private static final long DANCE_TIMEOUT_MS = 5000;

    private EmotionViewModel viewModel;
    private LinearLayout root;
    private ImageView ivExample;
    private GridLayout gridOptions;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvQuestion;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Runnables de timeout (por si no llega el DONE del Arduino) ---
    private final Runnable celebrateTimeout = () -> viewModel.onCelebrateDone();
    private final Runnable moveTimeout = () -> viewModel.onMoveDone();
    private final Runnable denyTimeout = () -> viewModel.onDenyDone();
    private final Runnable danceTimeout = () -> viewModel.onDanceDone();

    // --- BroadcastReceivers ---

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };
    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setOptionsEnabled(false); }
    };
    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setOptionsEnabled(true); }
    };

    private final BroadcastReceiver celebrateDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(celebrateTimeout);
            viewModel.onCelebrateDone();
        }
    };
    private final BroadcastReceiver moveDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(moveTimeout);
            viewModel.onMoveDone();
        }
    };
    private final BroadcastReceiver denyDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(denyTimeout);
            viewModel.onDenyDone();
        }
    };
    private final BroadcastReceiver danceDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(danceTimeout);
            viewModel.onDanceDone();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        int steps = getIntent().getIntExtra(EXTRA_STEPS, 3);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "ES"));
                ttsReady = true;
            }
        });

        buildLayout();

        viewModel = new ViewModelProvider(this).get(EmotionViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items, steps);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        progressBar.setMax(viewModel.getTotalRounds());
        updateProgress();
        showRound();

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case CORRECT:
                    // Feedback visual de acierto: fondo verde suave
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    // Timeout de seguridad por si no llega CELEBRATE_DONE
                    handler.postDelayed(celebrateTimeout, CELEBRATE_TIMEOUT_MS);
                    break;

                case CORRECT_ADVANCING:
                    // Robot avanzando casilla (MOVE_TIMED enviado)
                    // Timeout de seguridad por si no llega MOVE_DONE
                    handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);
                    break;

                case WRONG:
                    // Sin color negativo (principio TEA: ausencia de feedback negativo)
                    setOptionsEnabled(false);
                    // Timeout de seguridad por si no llega DENY_DONE
                    handler.postDelayed(denyTimeout, DENY_TIMEOUT_MS);
                    break;

                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    showRound();
                    setOptionsEnabled(true);
                    break;

                case COMPLETING:
                    // DANCE enviado, esperando DANCE_DONE
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    tvQuestion.setText(R.string.emotion_completed);
                    tvQuestion.setTextSize(28f);
                    gridOptions.removeAllViews();
                    ivExample.setVisibility(View.GONE);
                    handler.postDelayed(danceTimeout, DANCE_TIMEOUT_MS);
                    break;

                case COMPLETED:
                    // DANCE_DONE recibido o timeout — felicitación final
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvQuestion.setText(R.string.emotion_completed);
                    tvQuestion.setTextSize(28f);
                    gridOptions.removeAllViews();
                    ivExample.setVisibility(View.GONE);
                    handler.postDelayed(this::finish, 2500);
                    break;
            }
        });

        viewModel.getProgress().observe(this, p -> {
            progressBar.setProgress(p);
            updateProgress();
        });

        // Registrar broadcasts
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(celebrateDoneReceiver, new IntentFilter(AppConstants.ACTION_CELEBRATE_DONE));
        lbm.registerReceiver(moveDoneReceiver, new IntentFilter(AppConstants.ACTION_MOVE_DONE));
        lbm.registerReceiver(denyDoneReceiver, new IntentFilter(AppConstants.ACTION_DENY_DONE));
        lbm.registerReceiver(danceDoneReceiver, new IntentFilter(AppConstants.ACTION_DANCE_DONE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(celebrateDoneReceiver);
        lbm.unregisterReceiver(moveDoneReceiver);
        lbm.unregisterReceiver(denyDoneReceiver);
        lbm.unregisterReceiver(danceDoneReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 32, 32, 32);
        setContentView(root);

        // Progress
        tvProgress = new TextView(this);
        tvProgress.setTextSize(16f);
        tvProgress.setTextColor(Color.parseColor("#616161"));
        tvProgress.setGravity(Gravity.CENTER);
        root.addView(tvProgress);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16);
        pbParams.setMargins(32, 8, 32, 16);
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        // Question
        tvQuestion = new TextView(this);
        tvQuestion.setText(R.string.emotion_question);
        tvQuestion.setTextSize(22f);
        tvQuestion.setTextColor(Color.parseColor("#212121"));
        tvQuestion.setGravity(Gravity.CENTER);
        tvQuestion.setPadding(0, 0, 0, 16);
        root.addView(tvQuestion);

        // Example pictogram (large, tappable for TTS)
        ivExample = new ImageView(this);
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(280, 280);
        exParams.gravity = Gravity.CENTER_HORIZONTAL;
        exParams.bottomMargin = 32;
        ivExample.setLayoutParams(exParams);
        ivExample.setContentDescription(getString(R.string.emotion_question));
        root.addView(ivExample);

        // Grid for 4 options (2x2)
        gridOptions = new GridLayout(this);
        gridOptions.setColumnCount(2);
        gridOptions.setRowCount(2);
        gridOptions.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridOptions.setLayoutParams(gridParams);
        root.addView(gridOptions);
    }

    private void showRound() {
        String correctId = viewModel.getCorrectEmotionId();

        // Set example image
        int resId = getDrawableId(correctId);
        if (resId != 0) ivExample.setImageResource(resId);
        ivExample.setVisibility(View.VISIBLE);
        ivExample.setOnClickListener(v -> speakEmotion(correctId));

        // Build options grid
        List<String> options = viewModel.buildOptions();
        gridOptions.removeAllViews();
        for (String optionId : options) {
            ImageView iv = new ImageView(this);
            int optRes = getDrawableId(optionId);
            if (optRes != 0) iv.setImageResource(optRes);

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 200;
            gp.height = 200;
            gp.setMargins(16, 16, 16, 16);
            iv.setLayoutParams(gp);
            iv.setContentDescription(emotionLabel(optionId));
            iv.setBackgroundColor(Color.WHITE);
            iv.setPadding(12, 12, 12, 12);

            iv.setOnClickListener(v -> {
                viewModel.onOptionSelected(optionId);
                if (!optionId.equals(viewModel.getCorrectEmotionId())) {
                    speakEmotion(optionId);
                }
            });
            gridOptions.addView(iv);
        }
    }

    private void updateProgress() {
        tvProgress.setText(getString(R.string.emotion_progress,
                viewModel.getCurrentRound() + 1, viewModel.getTotalRounds()));
    }

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < gridOptions.getChildCount(); i++) {
            gridOptions.getChildAt(i).setEnabled(enabled);
            gridOptions.getChildAt(i).setAlpha(enabled ? 1f : 0.5f);
        }
        ivExample.setEnabled(enabled);
    }

    private void speakEmotion(String emotionId) {
        if (!ttsReady) return;
        String label = emotionLabel(emotionId);
        tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, emotionId);
    }

    private int getDrawableId(String emotionId) {
        return getResources().getIdentifier(emotionId, "drawable", getPackageName());
    }

    private String emotionLabel(String emotionId) {
        switch (emotionId) {
            case "emotion_happy": return getString(R.string.emotion_happy);
            case "emotion_sad": return getString(R.string.emotion_sad);
            case "emotion_angry": return getString(R.string.emotion_angry);
            case "emotion_surprised": return getString(R.string.emotion_surprised);
            case "emotion_scared": return getString(R.string.emotion_scared);
            case "emotion_disgusted": return getString(R.string.emotion_disgusted);
            case "emotion_calm": return getString(R.string.emotion_calm);
            case "emotion_love": return getString(R.string.emotion_love);
            default: return emotionId;
        }
    }
}
