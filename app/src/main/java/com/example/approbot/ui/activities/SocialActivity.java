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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.data.model.SessionConfig;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.SocialViewModel;

public class SocialActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID          = "session_id";
    public static final String EXTRA_SESSION_CONFIG_JSON = "session_config_json";
    public static final String EXTRA_STUDENT_PROFILE     = "student_profile_json";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_WRONG   = 0xFFFFCDD2;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;

    private SocialViewModel viewModel;
    private LinearLayout root;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView tvDescription;
    private Button btnOptionA;
    private Button btnOptionB;
    private TextView tvFeedback;
    private TextView tvCorrectHint;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };
    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setOptionsEnabled(false); }
    };
    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { setOptionsEnabled(true); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        String configJson = getIntent().getStringExtra(EXTRA_SESSION_CONFIG_JSON);
        SessionConfig config = SessionConfig.fromJson(configJson);
        if (config == null || config.socialScenarios.isEmpty()) { finish(); return; }

        buildLayout();

        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, config.socialScenarios);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        progressBar.setMax(viewModel.getTotalSquares());

        viewModel.getUiState().observe(this, state -> {
            if (state == null) return;

            progressBar.setProgress(state.currentSquare);
            tvProgress.setText("Casilla " + state.currentSquare + " / " + viewModel.getTotalSquares());

            switch (state.state) {
                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    tvDescription.setText(state.scenario.description);
                    btnOptionA.setText(state.scenario.optionA);
                    btnOptionB.setText(state.scenario.optionB);
                    btnOptionA.setVisibility(android.view.View.VISIBLE);
                    btnOptionB.setVisibility(android.view.View.VISIBLE);
                    setOptionsEnabled(true);
                    tvFeedback.setVisibility(android.view.View.GONE);
                    tvCorrectHint.setVisibility(android.view.View.GONE);
                    break;

                case CORRECT:
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    tvFeedback.setText(state.feedbackText);
                    tvFeedback.setVisibility(android.view.View.VISIBLE);
                    tvCorrectHint.setVisibility(android.view.View.GONE);
                    // Espera a que el robot termine el movimiento y luego avanza
                    handler.postDelayed(() -> viewModel.advanceToNext(), 2000);
                    break;

                case WRONG:
                    root.setBackgroundColor(COLOR_WRONG);
                    setOptionsEnabled(false);
                    tvFeedback.setText(state.feedbackText);
                    tvFeedback.setVisibility(android.view.View.VISIBLE);
                    tvCorrectHint.setText("La respuesta correcta era: " + state.correctText);
                    tvCorrectHint.setVisibility(android.view.View.VISIBLE);
                    // Tras mostrar feedback, nuevo escenario sin avanzar
                    handler.postDelayed(() -> viewModel.retryAfterWrong(), 3000);
                    break;

                case COMPLETED:
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvDescription.setText("¡Actividad completada!");
                    tvDescription.setTextSize(28f);
                    btnOptionA.setVisibility(android.view.View.GONE);
                    btnOptionB.setVisibility(android.view.View.GONE);
                    tvFeedback.setVisibility(android.view.View.GONE);
                    tvCorrectHint.setVisibility(android.view.View.GONE);
                    break;
            }
        });

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

        tvProgress = new TextView(this);
        tvProgress.setTextSize(16f);
        tvProgress.setTextColor(Color.parseColor("#616161"));
        tvProgress.setGravity(Gravity.CENTER);
        root.addView(tvProgress);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16);
        pbParams.setMargins(32, 8, 32, 24);
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        tvDescription = new TextView(this);
        tvDescription.setTextSize(20f);
        tvDescription.setTextColor(Color.parseColor("#212121"));
        tvDescription.setGravity(Gravity.CENTER);
        tvDescription.setPadding(16, 0, 16, 32);
        root.addView(tvDescription);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        root.addView(btnRow);

        btnOptionA = new Button(this);
        btnOptionA.setTextSize(18f);
        btnOptionA.setMinHeight(80);
        LinearLayout.LayoutParams pA = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pA.setMargins(8, 0, 8, 0);
        btnOptionA.setLayoutParams(pA);
        btnOptionA.setOnClickListener(v -> viewModel.onOptionSelected("A"));
        btnRow.addView(btnOptionA);

        btnOptionB = new Button(this);
        btnOptionB.setTextSize(18f);
        btnOptionB.setMinHeight(80);
        LinearLayout.LayoutParams pB = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pB.setMargins(8, 0, 8, 0);
        btnOptionB.setLayoutParams(pB);
        btnOptionB.setOnClickListener(v -> viewModel.onOptionSelected("B"));
        btnRow.addView(btnOptionB);

        tvFeedback = new TextView(this);
        tvFeedback.setTextSize(18f);
        tvFeedback.setTextColor(Color.parseColor("#212121"));
        tvFeedback.setGravity(Gravity.CENTER);
        tvFeedback.setPadding(16, 24, 16, 8);
        tvFeedback.setVisibility(android.view.View.GONE);
        root.addView(tvFeedback);

        tvCorrectHint = new TextView(this);
        tvCorrectHint.setTextSize(16f);
        tvCorrectHint.setTextColor(Color.parseColor("#1B5E20"));
        tvCorrectHint.setGravity(Gravity.CENTER);
        tvCorrectHint.setPadding(16, 8, 16, 16);
        tvCorrectHint.setVisibility(android.view.View.GONE);
        root.addView(tvCorrectHint);
    }

    private void setOptionsEnabled(boolean enabled) {
        btnOptionA.setEnabled(enabled);
        btnOptionB.setEnabled(enabled);
    }
}
