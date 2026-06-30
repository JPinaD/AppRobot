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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.SequenceViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity de Secuencias Visuales.
 *
 * Feedback estandarizado TEA:
 * - Acierto: fondo verde + CELEBRATE → CELEBRATE_DONE → ejecución física bonus → siguiente
 * - Fallo: sin color rojo + DENY → DENY_DONE → reintento (muestra misma secuencia)
 * - Completitud (3 secuencias correctas): DANCE → DANCE_DONE → pantalla felicitación
 */
public class SequenceActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_SEQUENCE_LENGTH = "sequence_length";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;
    private static final long SHOW_STEP_DELAY = 1200;

    // Timeouts de seguridad por si no llega DONE del Arduino
    private static final long CELEBRATE_TIMEOUT_MS = 3000;
    private static final long DENY_TIMEOUT_MS = 2500;
    private static final long DANCE_TIMEOUT_MS = 5000;

    private SequenceViewModel viewModel;
    private TextView tvInstruction;
    private TextView tvProgress;
    private LinearLayout layoutSequence;
    private LinearLayout layoutOptions;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Runnables de timeout ---
    private final Runnable celebrateTimeout = () -> viewModel.onCelebrateDone();
    private final Runnable denyTimeout = () -> viewModel.onDenyDone();
    private final Runnable danceTimeout = () -> viewModel.onDanceDone();
    private Runnable bonusTimeout; // Dinámico según duración de secuencia

    // --- BroadcastReceivers ---

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };
    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setInputEnabled(false); }
    };
    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setInputEnabled(true); }
    };

    private final BroadcastReceiver celebrateDoneReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(celebrateTimeout);
            viewModel.onCelebrateDone();
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
        int seqLength = getIntent().getIntExtra(EXTRA_SEQUENCE_LENGTH, 2);

        buildLayout();

        viewModel = new ViewModelProvider(this).get(SequenceViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items, seqLength);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    tvInstruction.setText(R.string.sequence_watch);
                    updateProgressText();
                    showSequenceAnimation(viewModel.getSequence());
                    break;

                case INPUT:
                    layoutSequence.setVisibility(View.GONE);
                    tvInstruction.setText(R.string.sequence_repeat);
                    showOptions(viewModel.getShuffledOptions().getValue());
                    break;

                case CORRECT:
                    // Feedback visual de acierto: fondo verde
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvInstruction.setText(R.string.sequence_correct);
                    layoutOptions.setVisibility(View.GONE);
                    updateProgressText();
                    // Timeout de seguridad por si no llega CELEBRATE_DONE
                    handler.postDelayed(celebrateTimeout, CELEBRATE_TIMEOUT_MS);
                    break;

                case EXECUTING_BONUS:
                    // Ejecutando secuencia física como bonus
                    tvInstruction.setText(R.string.sequence_correct);
                    // Timeout para esperar a que termine el bonus
                    long bonusDuration = viewModel.getBonusDurationMs();
                    bonusTimeout = () -> viewModel.onBonusFinished();
                    handler.postDelayed(bonusTimeout, bonusDuration);
                    break;

                case WRONG:
                    // Sin color rojo (principio TEA: ausencia de feedback negativo)
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    tvInstruction.setText(R.string.sequence_watch);
                    layoutOptions.setVisibility(View.GONE);
                    // Timeout de seguridad por si no llega DENY_DONE
                    handler.postDelayed(denyTimeout, DENY_TIMEOUT_MS);
                    break;

                case COMPLETING:
                    // DANCE enviado, esperando DANCE_DONE
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvInstruction.setText(R.string.sequence_correct);
                    tvInstruction.setTextSize(28f);
                    layoutOptions.setVisibility(View.GONE);
                    layoutSequence.setVisibility(View.GONE);
                    handler.postDelayed(danceTimeout, DANCE_TIMEOUT_MS);
                    break;

                case COMPLETED:
                    // DANCE_DONE recibido — felicitación final
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvInstruction.setText(R.string.sequence_correct);
                    tvInstruction.setTextSize(28f);
                    layoutOptions.setVisibility(View.GONE);
                    layoutSequence.setVisibility(View.GONE);
                    handler.postDelayed(this::finish, 2500);
                    break;
            }
        });

        // Registrar broadcasts
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(celebrateDoneReceiver, new IntentFilter(AppConstants.ACTION_CELEBRATE_DONE));
        lbm.registerReceiver(denyDoneReceiver, new IntentFilter(AppConstants.ACTION_DENY_DONE));
        lbm.registerReceiver(danceDoneReceiver, new IntentFilter(AppConstants.ACTION_DANCE_DONE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(celebrateDoneReceiver);
        lbm.unregisterReceiver(denyDoneReceiver);
        lbm.unregisterReceiver(danceDoneReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

        tvProgress = new TextView(this);
        tvProgress.setTextSize(14f);
        tvProgress.setTextColor(Color.parseColor("#616161"));
        tvProgress.setGravity(Gravity.CENTER);
        tvProgress.setPadding(0, 0, 0, 8);
        root.addView(tvProgress);

        tvInstruction = new TextView(this);
        tvInstruction.setText(R.string.sequence_watch);
        tvInstruction.setTextSize(22f);
        tvInstruction.setTextColor(Color.parseColor("#212121"));
        tvInstruction.setGravity(Gravity.CENTER);
        tvInstruction.setPadding(0, 0, 0, 24);
        root.addView(tvInstruction);

        layoutSequence = new LinearLayout(this);
        layoutSequence.setOrientation(LinearLayout.HORIZONTAL);
        layoutSequence.setGravity(Gravity.CENTER);
        root.addView(layoutSequence);

        layoutOptions = new LinearLayout(this);
        layoutOptions.setOrientation(LinearLayout.HORIZONTAL);
        layoutOptions.setGravity(Gravity.CENTER);
        layoutOptions.setVisibility(View.GONE);
        root.addView(layoutOptions);
    }

    private void updateProgressText() {
        tvProgress.setText(viewModel.getCompletedSequences() + " / "
                + viewModel.getTotalSequencesToComplete());
    }

    private void showSequenceAnimation(List<String> sequence) {
        layoutSequence.setVisibility(View.VISIBLE);
        layoutOptions.setVisibility(View.GONE);
        layoutSequence.removeAllViews();

        // Show each step with delay
        for (int i = 0; i < sequence.size(); i++) {
            final String step = sequence.get(i);
            final int index = i;
            handler.postDelayed(() -> {
                TextView tv = new TextView(this);
                tv.setText(stepLabel(step));
                tv.setTextSize(20f);
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundColor(stepColor(step));
                tv.setPadding(24, 16, 24, 16);
                tv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                p.setMargins(8, 0, 8, 0);
                tv.setLayoutParams(p);
                layoutSequence.addView(tv);
            }, (long) index * SHOW_STEP_DELAY);
        }

        // After showing all, transition to input
        long totalShowTime = (long) sequence.size() * SHOW_STEP_DELAY + 1000;
        handler.postDelayed(() -> viewModel.onShowingFinished(), totalShowTime);
    }

    private void showOptions(List<String> options) {
        if (options == null) return;
        layoutOptions.setVisibility(View.VISIBLE);
        layoutOptions.removeAllViews();

        for (String item : options) {
            Button btn = new Button(this);
            btn.setText(stepLabel(item));
            btn.setTextSize(18f);
            btn.setMinHeight(80);
            btn.setBackgroundColor(stepColor(item));
            btn.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> viewModel.onItemSelected(item));
            layoutOptions.addView(btn);
        }
    }

    private void setInputEnabled(boolean enabled) {
        for (int i = 0; i < layoutOptions.getChildCount(); i++) {
            layoutOptions.getChildAt(i).setEnabled(enabled);
            layoutOptions.getChildAt(i).setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private String stepLabel(String step) {
        switch (step) {
            case "FORWARD": return "↑";
            case "LEFT":    return "←";
            case "RIGHT":   return "→";
            case "SERVO":   return "★";
            default:        return step;
        }
    }

    private int stepColor(String step) {
        switch (step) {
            case "FORWARD": return 0xFF4CAF50;
            case "LEFT":    return 0xFF2196F3;
            case "RIGHT":   return 0xFFFF9800;
            case "SERVO":   return 0xFF9C27B0;
            default:        return 0xFF757575;
        }
    }
}
