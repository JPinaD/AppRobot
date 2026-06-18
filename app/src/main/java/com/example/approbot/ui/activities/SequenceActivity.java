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

    private static final int SHOW_DURATION_MS = 3000;
    private static final int COLOR_CORRECT    = 0xFFC8E6C9;
    private static final int COLOR_NEUTRAL    = 0xFFFAFAFA;

    private SequenceViewModel viewModel;
    private TextView tvInstruction;
    private LinearLayout layoutSequence;
    private LinearLayout layoutOptions;
    private LinearLayout root;

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        int seqLength = getIntent().getIntExtra(EXTRA_SEQUENCE_LENGTH, 2);

        // Si no hay ítems, usar colores/formas por defecto
        if (items == null || items.isEmpty()) {
            items = new ArrayList<>();
            items.add("rojo"); items.add("azul"); items.add("verde");
        }

        buildLayout();

        viewModel = new ViewModelProvider(this).get(SequenceViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), sessionId, items, seqLength);

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case SHOWING:
                    root.setBackgroundColor(COLOR_NEUTRAL);
                    showSequence(viewModel.getSequence());
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
                    break;
                case WRONG:
                    // Volver a mostrar la secuencia tras breve pausa
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                            viewModel.restart(), 800);
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

    private void showSequence(List<String> sequence) {
        tvInstruction.setText(R.string.sequence_watch);
        layoutSequence.setVisibility(android.view.View.VISIBLE);
        layoutOptions.setVisibility(android.view.View.GONE);
        layoutSequence.removeAllViews();

        for (String item : sequence) {
            TextView tv = new TextView(this);
            tv.setText(item);
            tv.setTextSize(20f);
            tv.setTextColor(Color.parseColor("#212121"));
            tv.setBackgroundColor(0xFFE0E0E0);
            tv.setPadding(24, 16, 24, 16);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(8, 0, 8, 0);
            tv.setLayoutParams(p);
            layoutSequence.addView(tv);
        }

        // Ocultar tras SHOW_DURATION_MS
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> viewModel.onShowingFinished(), SHOW_DURATION_MS);
    }

    private void showOptions(List<String> options) {
        if (options == null) return;
        layoutOptions.setVisibility(android.view.View.VISIBLE);
        layoutOptions.removeAllViews();

        for (String item : options) {
            Button btn = new Button(this);
            btn.setText(item);
            btn.setTextSize(18f);
            btn.setMinHeight(80);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> viewModel.onItemSelected(item));
            layoutOptions.addView(btn);
        }
    }
}
