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

public class EmotionActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";
    public static final String EXTRA_STEPS           = "emotion_steps";

    private static final int COLOR_CORRECT = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;

    private EmotionViewModel viewModel;
    private ImageView ivEmotion;
    private LinearLayout layoutOptions;
    private TextView tvQuestion;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private LinearLayout root;

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
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        int steps = getIntent().getIntExtra(EXTRA_STEPS, 5);

        buildLayout();

        viewModel = new ViewModelProvider(this).get(EmotionViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items, steps);

        progressBar.setMax(viewModel.getTotalSteps());
        updateProgressText();
        showCurrentEmotion();

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case CORRECT:
                    root.setBackgroundColor(COLOR_CORRECT);
                    setOptionsEnabled(false);
                    handler.postDelayed(() -> {
                        root.setBackgroundColor(COLOR_NEUTRAL);
                        viewModel.advanceAfterCorrect();
                    }, 1500);
                    break;
                case WRONG:
                    setOptionsEnabled(false);
                    handler.postDelayed(() -> {
                        viewModel.resetAfterWrong();
                    }, 1000);
                    break;
                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    showCurrentEmotion();
                    setOptionsEnabled(true);
                    break;
                case COMPLETED:
                    root.setBackgroundColor(COLOR_CORRECT);
                    tvQuestion.setText("¡Completado!");
                    tvQuestion.setTextSize(28f);
                    layoutOptions.removeAllViews();
                    ivEmotion.setVisibility(android.view.View.GONE);
                    break;
            }
        });

        viewModel.getProgress().observe(this, p -> {
            progressBar.setProgress(p);
            updateProgressText();
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
        root.setPadding(32, 32, 32, 32);
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

        tvQuestion = new TextView(this);
        tvQuestion.setText(R.string.emotion_question);
        tvQuestion.setTextSize(22f);
        tvQuestion.setTextColor(Color.parseColor("#212121"));
        tvQuestion.setGravity(Gravity.CENTER);
        tvQuestion.setPadding(0, 0, 0, 24);
        root.addView(tvQuestion);

        ivEmotion = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(240, 240);
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        imgParams.bottomMargin = 32;
        ivEmotion.setLayoutParams(imgParams);
        root.addView(ivEmotion);

        layoutOptions = new LinearLayout(this);
        layoutOptions.setOrientation(LinearLayout.HORIZONTAL);
        layoutOptions.setGravity(Gravity.CENTER);
        root.addView(layoutOptions);
    }

    private void showCurrentEmotion() {
        String correctId = viewModel.getCorrectEmotionId();
        int resId = getResources().getIdentifier(correctId, "drawable", getPackageName());
        if (resId != 0) ivEmotion.setImageResource(resId);
        ivEmotion.setVisibility(android.view.View.VISIBLE);

        List<String> options = viewModel.buildOptions();
        layoutOptions.removeAllViews();
        for (String optionId : options) {
            Button btn = new Button(this);
            btn.setText(emotionLabel(optionId));
            btn.setTextSize(18f);
            btn.setMinHeight(80);
            btn.setPadding(24, 16, 24, 16);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(12, 0, 12, 0);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> viewModel.onOptionSelected(optionId));
            layoutOptions.addView(btn);
        }
    }

    private void updateProgressText() {
        tvProgress.setText("Baldosa " + viewModel.getCorrectCount() + " / " + viewModel.getTotalSteps());
    }

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < layoutOptions.getChildCount(); i++)
            layoutOptions.getChildAt(i).setEnabled(enabled);
    }

    private String emotionLabel(String emotionId) {
        switch (emotionId) {
            case "emotion_happy":     return getString(R.string.emotion_happy);
            case "emotion_sad":       return getString(R.string.emotion_sad);
            case "emotion_angry":     return getString(R.string.emotion_angry);
            case "emotion_surprised": return getString(R.string.emotion_surprised);
            case "emotion_scared":    return getString(R.string.emotion_scared);
            default:                  return emotionId;
        }
    }
}
