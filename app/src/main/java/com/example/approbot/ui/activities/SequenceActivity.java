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
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.util.TtsHelper;
import com.example.approbot.viewmodel.SequenceViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity de Secuencias Visuales.
 *
 * Flujo:
 * - SHOWING: muestra 3 flechas en fila durante 3 segundos + robot ejecuta la secuencia.
 * - INPUT: muestra 4 botones grandes (↑ ↓ ← →). El alumno debe pulsarlos en orden.
 *   Barra de progreso muestra 0/3, 1/3, 2/3, 3/3.
 * - Acierto: muestra mensaje positivo durante 4s, luego avanza a siguiente ronda.
 * - Fallo: mensaje suave + TTS, espera 4s, vuelve a mostrar la MISMA secuencia.
 * - Última ronda: DANCE de celebración final.
 */
public class SequenceActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_SEQUENCE_LENGTH = "sequence_length";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;

    /** Duration to show the sequence (ms). */
    private static final long SHOW_DURATION_MS = 3000;
    /** Timeout per MOVE_TIMED step in case MOVE_DONE doesn't arrive. */
    private static final long MOVE_TIMEOUT_MS = 2500;
    /** Timeout for DANCE_DONE. */
    private static final long DANCE_TIMEOUT_MS = 5000;

    private SequenceViewModel viewModel;
    private LinearLayout root;
    private TextView tvRoundProgress;
    private TextView tvInstruction;
    private LinearLayout layoutSequenceDisplay;
    private GridLayout layoutButtons;
    private TextView tvInputProgress;
    private TextView tvWrongMessage;
    private Button btnForward, btnBackward, btnLeft, btnRight;
    private StudentProfile studentProfile;
    private CalmFabHelper calmFabHelper;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Timeouts
    private final Runnable moveTimeout = () -> viewModel.forceMoveDone();
    private final Runnable danceTimeout = () -> viewModel.forceDanceDone();

    // --- BroadcastReceivers ---

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };
    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setButtonsEnabled(false);
            if (calmFabHelper != null) calmFabHelper.hide();
        }
    };
    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            setButtonsEnabled(true);
            if (calmFabHelper != null) calmFabHelper.show();
        }
    };
    private final BroadcastReceiver moveDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(moveTimeout);
            viewModel.onMoveDone();
            // If still in SHOWING demo, set a new timeout for the next step.
            // Add extra time to account for the inter-move delay in ViewModel (150ms).
            SequenceViewModel.State currentState = viewModel.getState().getValue();
            if (currentState == SequenceViewModel.State.SHOWING) {
                handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS + 200);
            }
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
        int steps = getIntent().getIntExtra(EXTRA_SEQUENCE_LENGTH, 3);

        buildLayout();

        // Parse student profile for calm FAB
        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { studentProfile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }
        calmFabHelper = CalmFabHelper.attachToContent(this, studentProfile);

        viewModel = new ViewModelProvider(this).get(SequenceViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items, steps);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        // Observe state changes
        viewModel.getState().observe(this, this::onStateChanged);

        // Observe input progress (0/3, 1/3, etc.)
        viewModel.getInputProgress().observe(this, progress -> {
            if (progress != null) {
                tvInputProgress.setText(progress + " / 3");
            }
        });

        // Register broadcasts
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

    // --- State handling ---

    private void onStateChanged(SequenceViewModel.State newState) {
        if (newState == null) return;

        // Update round progress on every state change (cap at totalRounds)
        int displayRound = Math.min(viewModel.getCompletedRounds() + 1, viewModel.getTotalRounds());
        tvRoundProgress.setText("Ronda " + displayRound
                + " / " + viewModel.getTotalRounds());

        switch (newState) {
            case SHOWING:
                showSequencePhase();
                break;
            case INPUT:
                showInputPhase();
                break;
            case CORRECT_SHOWING:
                showCorrectPhase();
                break;
            case WRONG_SHOWING:
                showWrongPhase();
                break;
            case DANCING:
                showDancingPhase();
                break;
            case COMPLETED:
                showCompletedPhase();
                break;
        }
    }

    private void showSequencePhase() {
        root.setBackgroundColor(COLOR_NEUTRAL);
        tvInstruction.setText(R.string.sequence_watch);
        tvInstruction.setTextSize(24f);
        tvWrongMessage.setVisibility(View.GONE);
        tvInputProgress.setVisibility(View.GONE);
        layoutButtons.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.VISIBLE);

        // Display the 3 arrows
        layoutSequenceDisplay.removeAllViews();
        List<String> sequence = viewModel.getSequence();
        for (String dir : sequence) {
            TextView arrow = new TextView(this);
            arrow.setText(directionToArrow(dir));
            arrow.setTextSize(48f);
            arrow.setTextColor(Color.WHITE);
            arrow.setBackgroundColor(directionToColor(dir));
            arrow.setGravity(Gravity.CENTER);
            arrow.setPadding(32, 24, 32, 24);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(12, 0, 12, 0);
            arrow.setLayoutParams(p);
            layoutSequenceDisplay.addView(arrow);
        }

        // Set timeout for the demo move (robot executes physically during SHOWING)
        handler.postDelayed(moveTimeout, MOVE_TIMEOUT_MS);

        // After SHOW_DURATION_MS, notify ViewModel that visual timer is done
        handler.postDelayed(() -> viewModel.onShowingFinished(), SHOW_DURATION_MS);
    }

    private void showInputPhase() {
        root.setBackgroundColor(COLOR_NEUTRAL);
        tvInstruction.setText(R.string.sequence_repeat);
        tvInstruction.setTextSize(24f);
        tvWrongMessage.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.GONE);
        layoutButtons.setVisibility(View.VISIBLE);
        tvInputProgress.setVisibility(View.VISIBLE);
        setButtonsEnabled(true);
    }

    private void showCorrectPhase() {
        root.setBackgroundColor(COLOR_CORRECT);
        tvInstruction.setText(R.string.sequence_correct_next);
        tvInstruction.setTextSize(28f);
        layoutButtons.setVisibility(View.GONE);
        tvInputProgress.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.GONE);
        tvWrongMessage.setVisibility(View.GONE);
        TtsHelper.getInstance().speak(getString(R.string.sequence_correct_next));
        // No timeout needed — ViewModel handles the 4s delay internally
    }

    private void showWrongPhase() {
        root.setBackgroundColor(COLOR_NEUTRAL);
        tvInstruction.setText(R.string.sequence_watch);
        layoutButtons.setVisibility(View.GONE);
        tvInputProgress.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.GONE);
        tvWrongMessage.setText(R.string.wrong_try_again);
        tvWrongMessage.setVisibility(View.VISIBLE);
        TtsHelper.getInstance().speak(getString(R.string.wrong_try_again));
    }

    private void showDancingPhase() {
        root.setBackgroundColor(COLOR_CORRECT);
        tvInstruction.setText(R.string.sequence_correct);
        tvInstruction.setTextSize(28f);
        layoutButtons.setVisibility(View.GONE);
        tvInputProgress.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.GONE);
        tvWrongMessage.setVisibility(View.GONE);
        handler.postDelayed(danceTimeout, DANCE_TIMEOUT_MS);
    }

    private void showCompletedPhase() {
        root.setBackgroundColor(COLOR_CORRECT);
        tvInstruction.setText(R.string.sequence_correct);
        tvInstruction.setTextSize(28f);
        layoutButtons.setVisibility(View.GONE);
        tvInputProgress.setVisibility(View.GONE);
        layoutSequenceDisplay.setVisibility(View.GONE);
        tvWrongMessage.setVisibility(View.GONE);
        // Auto-finish after 2.5s
        handler.postDelayed(this::finish, 2500);
    }

    // --- Layout ---

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

        // Round progress ("Ronda 1 / 3")
        tvRoundProgress = new TextView(this);
        tvRoundProgress.setTextSize(16f);
        tvRoundProgress.setTextColor(Color.parseColor("#616161"));
        tvRoundProgress.setGravity(Gravity.CENTER);
        tvRoundProgress.setPadding(0, 0, 0, 8);
        root.addView(tvRoundProgress);

        // Instruction text
        tvInstruction = new TextView(this);
        tvInstruction.setText(R.string.sequence_watch);
        tvInstruction.setTextSize(24f);
        tvInstruction.setTextColor(Color.parseColor("#212121"));
        tvInstruction.setGravity(Gravity.CENTER);
        tvInstruction.setPadding(0, 0, 0, 32);
        root.addView(tvInstruction);

        // Wrong message (initially hidden)
        tvWrongMessage = new TextView(this);
        tvWrongMessage.setTextSize(22f);
        tvWrongMessage.setTextColor(Color.parseColor("#5D4037"));
        tvWrongMessage.setGravity(Gravity.CENTER);
        tvWrongMessage.setPadding(16, 24, 16, 24);
        tvWrongMessage.setVisibility(View.GONE);
        root.addView(tvWrongMessage);

        // Sequence display area (3 arrows in a row)
        layoutSequenceDisplay = new LinearLayout(this);
        layoutSequenceDisplay.setOrientation(LinearLayout.HORIZONTAL);
        layoutSequenceDisplay.setGravity(Gravity.CENTER);
        layoutSequenceDisplay.setPadding(0, 16, 0, 32);
        root.addView(layoutSequenceDisplay);

        // Input progress indicator ("1 / 3")
        tvInputProgress = new TextView(this);
        tvInputProgress.setTextSize(18f);
        tvInputProgress.setTextColor(Color.parseColor("#424242"));
        tvInputProgress.setGravity(Gravity.CENTER);
        tvInputProgress.setPadding(0, 0, 0, 16);
        tvInputProgress.setVisibility(View.GONE);
        root.addView(tvInputProgress);

        // Direction buttons (2x2 grid)
        layoutButtons = new GridLayout(this);
        layoutButtons.setColumnCount(2);
        layoutButtons.setRowCount(3);
        layoutButtons.setVisibility(View.GONE);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.gravity = Gravity.CENTER;
        layoutButtons.setLayoutParams(gridParams);

        // Row 0: Forward button (spans 2 columns, centered)
        btnForward = createDirectionButton("↑", "FORWARD", 0xFF4CAF50);
        GridLayout.LayoutParams fwdParams = new GridLayout.LayoutParams();
        fwdParams.columnSpec = GridLayout.spec(0, 2, GridLayout.CENTER);
        fwdParams.rowSpec = GridLayout.spec(0);
        fwdParams.setMargins(8, 8, 8, 8);
        btnForward.setLayoutParams(fwdParams);
        layoutButtons.addView(btnForward);

        // Row 1: Left and Right buttons
        btnLeft = createDirectionButton("←", "LEFT", 0xFF2196F3);
        GridLayout.LayoutParams leftParams = new GridLayout.LayoutParams();
        leftParams.columnSpec = GridLayout.spec(0);
        leftParams.rowSpec = GridLayout.spec(1);
        leftParams.setMargins(8, 8, 8, 8);
        btnLeft.setLayoutParams(leftParams);
        layoutButtons.addView(btnLeft);

        btnRight = createDirectionButton("→", "RIGHT", 0xFFFF9800);
        GridLayout.LayoutParams rightParams = new GridLayout.LayoutParams();
        rightParams.columnSpec = GridLayout.spec(1);
        rightParams.rowSpec = GridLayout.spec(1);
        rightParams.setMargins(8, 8, 8, 8);
        btnRight.setLayoutParams(rightParams);
        layoutButtons.addView(btnRight);

        // Row 2: Backward button (spans 2 columns, centered)
        btnBackward = createDirectionButton("↓", "BACKWARD", 0xFF9C27B0);
        GridLayout.LayoutParams bwdParams = new GridLayout.LayoutParams();
        bwdParams.columnSpec = GridLayout.spec(0, 2, GridLayout.CENTER);
        bwdParams.rowSpec = GridLayout.spec(2);
        bwdParams.setMargins(8, 8, 8, 8);
        btnBackward.setLayoutParams(bwdParams);
        layoutButtons.addView(btnBackward);

        root.addView(layoutButtons);
    }

    private Button createDirectionButton(String label, String direction, int bgColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(32f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(bgColor);
        btn.setMinHeight(dpToPx(96));
        btn.setMinWidth(dpToPx(120));
        btn.setOnClickListener(v -> viewModel.onDirectionSelected(direction));
        return btn;
    }

    private void setButtonsEnabled(boolean enabled) {
        btnForward.setEnabled(enabled);
        btnBackward.setEnabled(enabled);
        btnLeft.setEnabled(enabled);
        btnRight.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.5f;
        btnForward.setAlpha(alpha);
        btnBackward.setAlpha(alpha);
        btnLeft.setAlpha(alpha);
        btnRight.setAlpha(alpha);
    }

    // --- Helpers ---

    private String directionToArrow(String dir) {
        switch (dir) {
            case "FORWARD":  return "↑";
            case "BACKWARD": return "↓";
            case "LEFT":     return "←";
            case "RIGHT":    return "→";
            default:         return "?";
        }
    }

    private int directionToColor(String dir) {
        switch (dir) {
            case "FORWARD":  return 0xFF4CAF50;
            case "BACKWARD": return 0xFF9C27B0;
            case "LEFT":     return 0xFF2196F3;
            case "RIGHT":    return 0xFFFF9800;
            default:         return 0xFF757575;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
