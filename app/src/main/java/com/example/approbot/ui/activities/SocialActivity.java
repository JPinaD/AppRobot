package com.example.approbot.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
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

    private static final int COLOR_OUTCOME_A = 0xFFB3E5FC;
    private static final int COLOR_OUTCOME_B = 0xFFE1BEE7;
    private static final int COLOR_NEUTRAL   = 0xFFFAFAFA;

    private SocialViewModel viewModel;
    private TextView tvDescription;
    private TextView tvOutcome;
    private TextView tvProgress;
    private Button btnOptionA;
    private Button btnOptionB;
    private Button btnNext;
    private LinearLayout root;

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
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

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            tvProgress.setText((state.currentIndex + 1) + " / " + state.total);

            if (state.outcomeText == null) {
                root.setBackgroundColor(COLOR_NEUTRAL);
                tvDescription.setText(state.scenario.description);
                btnOptionA.setText(state.scenario.optionA);
                btnOptionB.setText(state.scenario.optionB);
                tvOutcome.setVisibility(android.view.View.GONE);
                btnOptionA.setEnabled(true);
                btnOptionB.setEnabled(true);
                btnNext.setVisibility(android.view.View.GONE);
            } else {
                boolean isA = state.outcomeText.equals(state.scenario.outcomeA);
                root.setBackgroundColor(isA ? COLOR_OUTCOME_A : COLOR_OUTCOME_B);
                tvOutcome.setText(state.outcomeText);
                tvOutcome.setVisibility(android.view.View.VISIBLE);
                btnOptionA.setEnabled(false);
                btnOptionB.setEnabled(false);
                btnNext.setVisibility(android.view.View.VISIBLE);
            }
        });

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndReceiver);
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
        tvProgress.setPadding(0, 0, 0, 16);
        root.addView(tvProgress);

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

        tvOutcome = new TextView(this);
        tvOutcome.setTextSize(18f);
        tvOutcome.setTextColor(Color.parseColor("#212121"));
        tvOutcome.setGravity(Gravity.CENTER);
        tvOutcome.setPadding(16, 24, 16, 16);
        tvOutcome.setVisibility(android.view.View.GONE);
        root.addView(tvOutcome);

        btnNext = new Button(this);
        btnNext.setText("Siguiente");
        btnNext.setTextSize(16f);
        btnNext.setVisibility(android.view.View.GONE);
        btnNext.setOnClickListener(v -> viewModel.nextScenario());
        root.addView(btnNext);
    }
}
