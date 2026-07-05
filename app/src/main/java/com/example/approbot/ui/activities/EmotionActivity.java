package com.example.approbot.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.util.TtsHelper;
import com.example.approbot.viewmodel.EmotionViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity de Reconocimiento Emocional.
 *
 * Feedback para actividades con casillas:
 * - Acierto: fondo verde + MOVE_TIMED FORWARD (avanza casilla) → MOVE_DONE → siguiente
 * - Último acierto: MOVE_TIMED FORWARD → MOVE_DONE → DANCE → DANCE_DONE → felicitación
 * - Fallo: mensaje suave + TTS + resaltar correcta tras 2s → avanza tras 4s (sin movimiento robot)
 */
public class EmotionActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_ITEMS = "activity_items";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";
    public static final String EXTRA_STEPS = "emotion_steps";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;
    /** Color neutro para resaltar la opción correcta en fallo (sin rojo). */
    private static final int COLOR_HIGHLIGHT_CORRECT = 0xFFB3E5FC;

    // Timeout de seguridad: si no llega DONE del Arduino, avanzar igualmente
    private static final long MOVE_TIMEOUT_MS = 2500;
    private static final long DANCE_TIMEOUT_MS = 5000;

    private EmotionViewModel viewModel;
    private LinearLayout root;
    private ImageView ivExample;
    private GridLayout gridOptions;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvQuestion;
    private TextView tvWrongMessage;

    private StudentProfile studentProfile;
    private CalmFabHelper calmFabHelper;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Runnables de timeout (por si no llega el DONE del Arduino) ---
    private final Runnable moveTimeout = () -> viewModel.advanceAfterCorrect();
    private final Runnable danceTimeout = () -> viewModel.onDanceDone();

    // --- BroadcastReceivers ---

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };
    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setOptionsEnabled(false);
            if (calmFabHelper != null) calmFabHelper.hide();
        }
    };
    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setOptionsEnabled(true);
            if (calmFabHelper != null) calmFabHelper.show();
        }
    };

    private final BroadcastReceiver moveDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(moveTimeout);
            viewModel.onMoveDone();
        }
    };
    private final BroadcastReceiver danceDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(danceTimeout);
            viewModel.onDanceDone();
        }
    };

    private final BroadcastReceiver tiltAlertReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (getSupportFragmentManager().findFragmentByTag(TiltAlertDialogFragment.TAG) == null) {
                new TiltAlertDialogFragment().show(getSupportFragmentManager(), TiltAlertDialogFragment.TAG);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        int steps = getIntent().getIntExtra(EXTRA_STEPS, 3);

        buildLayout();

        // Parse student profile for calm FAB
        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { studentProfile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }

        // Attach calm FAB (uses android.R.id.content since root is LinearLayout)
        calmFabHelper = CalmFabHelper.attachToContent(this, studentProfile);

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
                case CORRECT_ADVANCING:
                    // Acierto: fondo verde, opciones deshabilitadas, robot avanzando
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    tvWrongMessage.setVisibility(View.GONE);
                    handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);
                    break;

                case COMPLETING:
                    // Última casilla avanzando (MOVE_TIMED enviado, espera MOVE_DONE para DANCE)
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    tvWrongMessage.setVisibility(View.GONE);
                    handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);
                    break;

                case DANCING:
                    // DANCE enviado tras última casilla, esperando DANCE_DONE
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    tvQuestion.setText(R.string.emotion_completed);
                    tvQuestion.setTextSize(28f);
                    gridOptions.removeAllViews();
                    ivExample.setVisibility(View.GONE);
                    tvWrongMessage.setVisibility(View.GONE);
                    handler.postDelayed(danceTimeout, DANCE_TIMEOUT_MS);
                    break;

                case WRONG_SHOWING:
                    // Fallo: sin color negativo, mensaje suave, opciones deshabilitadas
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    setOptionsEnabled(false);
                    tvWrongMessage.setText(R.string.wrong_try_again);
                    tvWrongMessage.setVisibility(View.VISIBLE);
                    // Speak soft feedback via TTS
                    TtsHelper.getInstance().speak(getString(R.string.wrong_try_again));
                    break;

                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    tvWrongMessage.setVisibility(View.GONE);
                    showRound();
                    setOptionsEnabled(true);
                    break;

                case COMPLETED:
                    // DANCE_DONE recibido o timeout — felicitación final
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvQuestion.setText(R.string.emotion_completed);
                    tvQuestion.setTextSize(28f);
                    gridOptions.removeAllViews();
                    ivExample.setVisibility(View.GONE);
                    tvWrongMessage.setVisibility(View.GONE);
                    handler.postDelayed(this::finish, 2500);
                    break;
            }
        });

        // Observe highlight signal for wrong answers
        viewModel.getHighlightCorrect().observe(this, highlight -> {
            if (highlight != null && highlight) {
                highlightCorrectOption();
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
        lbm.registerReceiver(moveDoneReceiver, new IntentFilter(AppConstants.ACTION_MOVE_DONE));
        lbm.registerReceiver(danceDoneReceiver, new IntentFilter(AppConstants.ACTION_DANCE_DONE));
        lbm.registerReceiver(tiltAlertReceiver, new IntentFilter(AppConstants.ACTION_TILT_ALERT));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(moveDoneReceiver);
        lbm.unregisterReceiver(danceDoneReceiver);
        lbm.unregisterReceiver(tiltAlertReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(COLOR_NEUTRAL);
        // Extra top padding to avoid camera/notch clipping
        root.setPadding(32, dpToPx(52), 32, 32);
        setContentView(root);

        // --- Top section (~35% of screen): progress + question + example image ---
        LinearLayout topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 35f);
        topSection.setLayoutParams(topParams);

        // Progress
        tvProgress = new TextView(this);
        tvProgress.setTextSize(16f);
        tvProgress.setTextColor(Color.parseColor("#616161"));
        tvProgress.setGravity(Gravity.CENTER);
        topSection.addView(tvProgress);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8));
        pbParams.setMargins(32, 8, 32, 12);
        progressBar.setLayoutParams(pbParams);
        topSection.addView(progressBar);

        // Question
        tvQuestion = new TextView(this);
        tvQuestion.setText(R.string.emotion_question);
        tvQuestion.setTextSize(22f);
        tvQuestion.setTextColor(Color.parseColor("#212121"));
        tvQuestion.setGravity(Gravity.CENTER);
        tvQuestion.setPadding(0, 0, 0, 8);
        topSection.addView(tvQuestion);

        // Wrong message (initially hidden)
        tvWrongMessage = new TextView(this);
        tvWrongMessage.setTextSize(20f);
        tvWrongMessage.setTextColor(Color.parseColor("#5D4037"));
        tvWrongMessage.setGravity(Gravity.CENTER);
        tvWrongMessage.setPadding(16, 8, 16, 8);
        tvWrongMessage.setVisibility(View.GONE);
        topSection.addView(tvWrongMessage);

        // Example pictogram (fills remaining top space, visually highlighted)
        ivExample = new ImageView(this);
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1.3f);
        exParams.gravity = Gravity.CENTER_HORIZONTAL;
        exParams.bottomMargin = 8;
        ivExample.setLayoutParams(exParams);
        ivExample.setAdjustViewBounds(true);
        ivExample.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivExample.setContentDescription(getString(R.string.emotion_question));
        // Blue soft border to distinguish from grid options
        android.graphics.drawable.GradientDrawable exBorder = new android.graphics.drawable.GradientDrawable();
        exBorder.setColor(0xFFFFFFFF); // white background
        exBorder.setStroke(dpToPx(3), 0xFF4A90D9); // 3dp blue border
        exBorder.setCornerRadius(dpToPx(12)); // rounded corners
        ivExample.setBackground(exBorder);
        ivExample.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        ivExample.setClipToOutline(true);
        topSection.addView(ivExample);

        root.addView(topSection);

        // --- Bottom section (~65% of screen): options grid that fills available space ---
        LinearLayout bottomSection = new LinearLayout(this);
        bottomSection.setOrientation(LinearLayout.VERTICAL);
        bottomSection.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams botParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 65f);
        bottomSection.setLayoutParams(botParams);

        // Grid for 4 options (2x2) — fills the bottom section
        gridOptions = new GridLayout(this);
        gridOptions.setColumnCount(2);
        gridOptions.setRowCount(2);
        gridOptions.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        gridOptions.setLayoutParams(gridParams);
        bottomSection.addView(gridOptions);

        root.addView(bottomSection);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showRound() {
        String correctId = viewModel.getCorrectEmotionId();

        // Set example image
        int resId = getDrawableId(correctId);
        if (resId != 0) ivExample.setImageResource(resId);
        ivExample.setVisibility(View.VISIBLE);
        ivExample.setOnClickListener(v -> speakEmotion(correctId));

        // Build options grid (2x2, each cell fills equally)
        List<String> options = viewModel.buildOptions();
        gridOptions.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            String optionId = options.get(i);
            ImageView iv = new ImageView(this);
            int optRes = getDrawableId(optionId);
            if (optRes != 0) iv.setImageResource(optRes);

            // Use GridLayout specs with weight to fill space equally
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.rowSpec = GridLayout.spec(i / 2, 1f);
            gp.columnSpec = GridLayout.spec(i % 2, 1f);
            gp.width = 0;
            gp.height = 0;
            gp.setMargins(12, 12, 12, 12);
            iv.setLayoutParams(gp);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription(emotionLabel(optionId));
            iv.setBackgroundColor(Color.WHITE);
            iv.setPadding(16, 16, 16, 16);
            iv.setTag(optionId); // Tag for highlighting later

            iv.setOnClickListener(v -> {
                viewModel.onOptionSelected(optionId);
            });
            gridOptions.addView(iv);
        }
    }

    /**
     * Highlights the correct option with a neutral blue border/background.
     * Called when highlightCorrect LiveData becomes true (2s after wrong answer).
     */
    private void highlightCorrectOption() {
        String correctId = viewModel.getCorrectEmotionId();
        for (int i = 0; i < gridOptions.getChildCount(); i++) {
            View child = gridOptions.getChildAt(i);
            if (correctId.equals(child.getTag())) {
                child.setBackgroundColor(COLOR_HIGHLIGHT_CORRECT);
                break;
            }
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
        String label = emotionLabel(emotionId);
        TtsHelper.getInstance().speak(label);
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
            case "emotion_shy": return getString(R.string.emotion_shy);
            case "emotion_bored": return getString(R.string.emotion_bored);
            case "emotion_tired": return getString(R.string.emotion_tired);
            case "emotion_excited": return getString(R.string.emotion_excited);
            case "emotion_terror": return getString(R.string.emotion_terror);
            default: return emotionId;
        }
    }
}
