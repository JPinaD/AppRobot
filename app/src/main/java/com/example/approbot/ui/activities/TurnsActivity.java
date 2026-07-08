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
import com.example.approbot.viewmodel.TurnsViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Actividad de Turnos Sociales cooperativos (modo MULTI exclusivamente).
 *
 * Requiere 2+ robots. Siempre coordinado por AppTerapeuta.
 * Al iniciar, muestra pantalla de espera hasta que llegue el primer TURN_SIGNAL.
 *
 * Feedback: acierto conjunto → MOVE_TIMED FORWARD 800ms, fallo → visual suave,
 * última ronda → DANCE.
 */
public class TurnsActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";
    public static final String EXTRA_STEPS           = "turns_steps";

    private static final int COLOR_CORRECT   = 0xFFC8E6C9; // green pastel
    private static final int COLOR_NEUTRAL   = 0xFFFAFAFA;
    private static final int COLOR_HIGHLIGHT = 0xFFB3E5FC; // blue light for correct option highlight
    private static final int COLOR_WAITING   = 0xFFFFF9C4; // yellow pastel for waiting

    private static final long MOVE_TIMEOUT_MS = 2500;
    private static final long DANCE_TIMEOUT_MS = 5000;

    private TurnsViewModel viewModel;
    private LinearLayout root;
    private ImageView ivExample;
    private GridLayout gridOptions;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvQuestion;
    private TextView tvWrongMessage;
    private TextView tvWaitingPartner;

    private StudentProfile studentProfile;
    private CalmFabHelper calmFabHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
    private final BroadcastReceiver turnSignalReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            try {
                JSONObject obj = new JSONObject(payload);
                viewModel.onTurnSignalReceived(obj);
            } catch (JSONException ignored) {}
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
        int steps = getIntent().getIntExtra(EXTRA_STEPS, 5);

        buildLayout();

        // Parse student profile for calm FAB
        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { studentProfile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }
        calmFabHelper = CalmFabHelper.attachToContent(this, studentProfile);

        viewModel = new ViewModelProvider(this).get(TurnsViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, steps);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        progressBar.setMax(viewModel.getTotalRounds());

        // Observe state
        viewModel.getState().observe(this, this::onStateChanged);

        // Observe highlight signal for wrong answers
        viewModel.getHighlightCorrect().observe(this, highlight -> {
            if (highlight != null && highlight) highlightCorrectOption();
        });

        viewModel.getProgress().observe(this, p -> {
            progressBar.setProgress(p);
            updateProgress();
        });

        // Register broadcasts
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(moveDoneReceiver, new IntentFilter(AppConstants.ACTION_MOVE_DONE));
        lbm.registerReceiver(danceDoneReceiver, new IntentFilter(AppConstants.ACTION_DANCE_DONE));
        lbm.registerReceiver(turnSignalReceiver, new IntentFilter(AppConstants.ACTION_TURN_SIGNAL));
        lbm.registerReceiver(tiltAlertReceiver, new IntentFilter(AppConstants.ACTION_TILT_ALERT));

        // Activity starts in WAITING_ROUND state — waits for first TURN_SIGNAL from terapeuta
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
        lbm.unregisterReceiver(turnSignalReceiver);
        lbm.unregisterReceiver(tiltAlertReceiver);
    }

    // =========================================================================
    // State handler
    // =========================================================================

    private void onStateChanged(TurnsViewModel.State state) {
        switch (state) {
            case WAITING_ROUND:
                showWaitingForRound();
                break;
            case SHOWING:
                showRound();
                break;
            case WAITING_PARTNER:
                showWaitingForPartner();
                break;
            case CORRECT_ADVANCING:
                root.setBackgroundColor(COLOR_CORRECT);
                setOptionsEnabled(false);
                tvWrongMessage.setVisibility(View.GONE);
                tvWaitingPartner.setVisibility(View.GONE);
                handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);
                break;
            case COMPLETING:
                root.setBackgroundColor(COLOR_CORRECT);
                setOptionsEnabled(false);
                tvWrongMessage.setVisibility(View.GONE);
                tvWaitingPartner.setVisibility(View.GONE);
                handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);
                break;
            case WRONG_SHOWING:
                root.setBackgroundColor(COLOR_NEUTRAL);
                setOptionsEnabled(false);
                tvWaitingPartner.setVisibility(View.GONE);
                tvWrongMessage.setText(R.string.wrong_try_again);
                tvWrongMessage.setVisibility(View.VISIBLE);
                TtsHelper.getInstance().speak(getString(R.string.wrong_try_again));
                break;
            case DANCING:
                root.setBackgroundColor(COLOR_CORRECT);
                setOptionsEnabled(false);
                tvQuestion.setText(R.string.emotion_completed);
                tvQuestion.setTextSize(28f);
                gridOptions.removeAllViews();
                ivExample.setVisibility(View.GONE);
                tvWrongMessage.setVisibility(View.GONE);
                tvWaitingPartner.setVisibility(View.GONE);
                handler.postDelayed(danceTimeout, DANCE_TIMEOUT_MS);
                break;
            case COMPLETED:
                root.setBackgroundColor(COLOR_CORRECT);
                tvQuestion.setText(R.string.emotion_completed);
                tvQuestion.setTextSize(28f);
                gridOptions.removeAllViews();
                ivExample.setVisibility(View.GONE);
                tvWrongMessage.setVisibility(View.GONE);
                tvWaitingPartner.setVisibility(View.GONE);
                handler.postDelayed(this::finish, 2500);
                break;
        }
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void showWaitingForRound() {
        root.setBackgroundColor(COLOR_NEUTRAL);
        gridOptions.removeAllViews();
        ivExample.setVisibility(View.GONE);
        tvWrongMessage.setVisibility(View.GONE);
        tvWaitingPartner.setVisibility(View.GONE);
        tvQuestion.setText(R.string.turns_wait);
        tvQuestion.setTextSize(22f);
        setOptionsEnabled(false);
    }

    private void showWaitingForPartner() {
        root.setBackgroundColor(COLOR_WAITING);
        setOptionsEnabled(false);
        tvWrongMessage.setVisibility(View.GONE);
        tvWaitingPartner.setVisibility(View.VISIBLE);
        tvWaitingPartner.setText(R.string.turns_waiting_partner);
    }

    private void showRound() {
        root.setBackgroundColor(COLOR_NEUTRAL);
        tvWrongMessage.setVisibility(View.GONE);
        tvWaitingPartner.setVisibility(View.GONE);
        tvQuestion.setText(R.string.emotion_question);
        tvQuestion.setTextSize(22f);

        String correctId = viewModel.getCorrectEmotionId();
        if (correctId == null) return;

        // Set example image
        int resId = getDrawableId(correctId);
        if (resId != 0) ivExample.setImageResource(resId);
        ivExample.setVisibility(View.VISIBLE);
        ivExample.setOnClickListener(v -> speakEmotion(correctId));

        // Build options grid
        List<String> options = viewModel.getCurrentOptions();
        if (options == null) return;

        gridOptions.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            String optionId = options.get(i);
            ImageView iv = new ImageView(this);
            int optRes = getDrawableId(optionId);
            if (optRes != 0) iv.setImageResource(optRes);

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
            iv.setTag(optionId);

            iv.setOnClickListener(v -> viewModel.onOptionSelected(optionId));
            gridOptions.addView(iv);
        }
        setOptionsEnabled(true);
        updateProgress();
    }

    private void highlightCorrectOption() {
        String correctId = viewModel.getCorrectEmotionId();
        for (int i = 0; i < gridOptions.getChildCount(); i++) {
            View child = gridOptions.getChildAt(i);
            if (correctId != null && correctId.equals(child.getTag())) {
                child.setBackgroundColor(COLOR_HIGHLIGHT);
                break;
            }
        }
    }

    private void updateProgress() {
        int round = viewModel.getCurrentRound() + 1;
        int total = viewModel.getTotalRounds();
        tvProgress.setText(getString(R.string.emotion_progress, Math.min(round, total), total));
    }

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < gridOptions.getChildCount(); i++) {
            gridOptions.getChildAt(i).setEnabled(enabled);
            gridOptions.getChildAt(i).setAlpha(enabled ? 1f : 0.5f);
        }
        if (ivExample != null) ivExample.setEnabled(enabled);
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

    // =========================================================================
    // Layout building (programmatic, same style as EmotionActivity)
    // =========================================================================

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, dpToPx(52), 32, 32);
        setContentView(root);

        // --- Top section (~35%): progress + question + example ---
        LinearLayout topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.VERTICAL);
        topSection.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 35f);
        topSection.setLayoutParams(topParams);

        // Progress text
        tvProgress = new TextView(this);
        tvProgress.setTextSize(16f);
        tvProgress.setTextColor(Color.parseColor("#616161"));
        tvProgress.setGravity(Gravity.CENTER);
        topSection.addView(tvProgress);

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8));
        pbParams.setMargins(32, 8, 32, 12);
        progressBar.setLayoutParams(pbParams);
        topSection.addView(progressBar);

        // Question text
        tvQuestion = new TextView(this);
        tvQuestion.setText(R.string.turns_wait);
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

        // Waiting for partner message (initially hidden)
        tvWaitingPartner = new TextView(this);
        tvWaitingPartner.setTextSize(22f);
        tvWaitingPartner.setTextColor(Color.parseColor("#1565C0"));
        tvWaitingPartner.setGravity(Gravity.CENTER);
        tvWaitingPartner.setPadding(16, 16, 16, 16);
        tvWaitingPartner.setVisibility(View.GONE);
        topSection.addView(tvWaitingPartner);

        // Example pictogram
        ivExample = new ImageView(this);
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1.3f);
        exParams.gravity = Gravity.CENTER_HORIZONTAL;
        exParams.bottomMargin = 8;
        ivExample.setLayoutParams(exParams);
        ivExample.setAdjustViewBounds(true);
        ivExample.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivExample.setContentDescription(getString(R.string.emotion_question));
        // Blue soft border
        android.graphics.drawable.GradientDrawable exBorder = new android.graphics.drawable.GradientDrawable();
        exBorder.setColor(0xFFFFFFFF);
        exBorder.setStroke(dpToPx(3), 0xFF4A90D9);
        exBorder.setCornerRadius(dpToPx(12));
        ivExample.setBackground(exBorder);
        ivExample.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        ivExample.setClipToOutline(true);
        ivExample.setVisibility(View.GONE);
        topSection.addView(ivExample);

        root.addView(topSection);

        // --- Bottom section (~65%): options grid ---
        LinearLayout bottomSection = new LinearLayout(this);
        bottomSection.setOrientation(LinearLayout.VERTICAL);
        bottomSection.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams botParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 65f);
        bottomSection.setLayoutParams(botParams);

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
}
