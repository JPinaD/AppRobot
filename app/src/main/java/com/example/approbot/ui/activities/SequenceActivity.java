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

public class SequenceActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_SEQUENCE_LENGTH = "sequence_length";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;
    private static final long SHOW_STEP_DELAY = 1200;

    private SequenceViewModel viewModel;
    private TextView tvInstruction;
    private LinearLayout layoutSequence;
    private LinearLayout layoutOptions;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
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
                    showSequenceAnimation(viewModel.getSequence());
                    break;
                case INPUT:
                    layoutSequence.setVisibility(android.view.View.GONE);
                    tvInstruction.setText(R.string.sequence_repeat);
                    showOptions(viewModel.getShuffledOptions().getValue());
                    break;
                case CORRECT:
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvInstruction.setText(R.string.sequence_correct);
                    layoutOptions.setVisibility(android.view.View.GONE);
                    // Auto-restart with new sequence after 2s
                    handler.postDelayed(() -> viewModel.restart(), 2500);
                    break;
                case WRONG:
                    handler.postDelayed(() -> viewModel.restart(), 1000);
                    break;
            }
        });

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

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
        layoutOptions.setVisibility(android.view.View.GONE);
        root.addView(layoutOptions);
    }

    private void showSequenceAnimation(List<String> sequence) {
        layoutSequence.setVisibility(android.view.View.VISIBLE);
        layoutOptions.setVisibility(android.view.View.GONE);
        layoutSequence.removeAllViews();

        // Show each step with delay and execute on robot
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

        // Execute physically on robot
        viewModel.executeFullSequence();

        // After showing all, transition to input
        long totalShowTime = (long) sequence.size() * SHOW_STEP_DELAY + 1000;
        handler.postDelayed(() -> viewModel.onShowingFinished(), totalShowTime);
    }

    private void showOptions(List<String> options) {
        if (options == null) return;
        layoutOptions.setVisibility(android.view.View.VISIBLE);
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
