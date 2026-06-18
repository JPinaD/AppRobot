package com.example.approbot.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.EmotionViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmotionActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int COLOR_CORRECT = 0xFFC8E6C9; // verde suave
    private static final int COLOR_NEUTRAL = 0xFFFAFAFA;

    private EmotionViewModel viewModel;
    private ImageView ivEmotion;
    private LinearLayout layoutOptions;
    private TextView tvQuestion;

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        if (items == null || items.isEmpty()) { finish(); return; }

        buildLayout();

        viewModel = new ViewModelProvider(this).get(EmotionViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items);

        showEmotion(viewModel.getCorrectEmotionId(), items);

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case CORRECT:
                    getWindow().getDecorView().setBackgroundColor(COLOR_CORRECT);
                    layoutOptions.setEnabled(false);
                    setOptionsEnabled(false);
                    break;
                case WRONG:
                    // Volver al estado inicial sin feedback negativo
                    getWindow().getDecorView().setBackgroundColor(COLOR_NEUTRAL);
                    viewModel.resetToShowing();
                    break;
                case SHOWING:
                    getWindow().getDecorView().setBackgroundColor(COLOR_NEUTRAL);
                    setOptionsEnabled(true);
                    break;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_NEUTRAL);
        root.setPadding(32, 32, 32, 32);
        setContentView(root);

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

    private void showEmotion(String correctId, List<String> allItems) {
        int resId = getResources().getIdentifier(correctId, "drawable", getPackageName());
        if (resId != 0) ivEmotion.setImageResource(resId);

        // Construir opciones: correcta + hasta 2 distractores, orden aleatorio
        List<String> options = new ArrayList<>();
        options.add(correctId);
        for (String item : allItems) {
            if (!item.equals(correctId) && options.size() < 3) options.add(item);
        }
        Collections.shuffle(options);

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

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < layoutOptions.getChildCount(); i++) {
            layoutOptions.getChildAt(i).setEnabled(enabled);
        }
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
