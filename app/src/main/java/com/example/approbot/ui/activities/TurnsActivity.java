package com.example.approbot.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
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
import com.example.approbot.viewmodel.TurnsViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class TurnsActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID      = "session_id";
    public static final String EXTRA_ITEMS           = "activity_items";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int COLOR_MY_TURN = 0xFFFFF9C4; // amarillo suave
    private static final int COLOR_WAITING = 0xFFFAFAFA;

    private TurnsViewModel viewModel;
    private TextView tvTurnStatus;
    private ImageView ivPictogram;
    private Button btnTurn;
    private LinearLayout root;

    private String currentPictogramId;

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { finish(); }
    };

    private final BroadcastReceiver turnSignalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            try {
                JSONObject obj = new JSONObject(payload);
                boolean active = obj.optBoolean("active", false);
                viewModel.onTurnSignalReceived(active);
            } catch (JSONException e) {
                android.util.Log.w("TurnsActivity", "Error parseando TURN_SIGNAL: " + payload);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);
        currentPictogramId = (items != null && !items.isEmpty()) ? items.get(0) : null;

        buildLayout();

        viewModel = new ViewModelProvider(this).get(TurnsViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(), sessionId);

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case MY_TURN:
                    root.setBackgroundColor(COLOR_MY_TURN);
                    tvTurnStatus.setText(R.string.turns_my_turn);
                    btnTurn.setEnabled(true);
                    if (currentPictogramId != null) {
                        int resId = getResources().getIdentifier(
                                currentPictogramId, "drawable", getPackageName());
                        if (resId != 0) ivPictogram.setImageResource(resId);
                        ivPictogram.setVisibility(android.view.View.VISIBLE);
                    }
                    break;
                case WAITING:
                    root.setBackgroundColor(COLOR_WAITING);
                    tvTurnStatus.setText(R.string.turns_wait);
                    btnTurn.setEnabled(false);
                    ivPictogram.setVisibility(android.view.View.INVISIBLE);
                    break;
            }
        });

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(turnSignalReceiver, new IntentFilter(AppConstants.ACTION_TURN_SIGNAL));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(turnSignalReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_WAITING);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

        tvTurnStatus = new TextView(this);
        tvTurnStatus.setText(R.string.turns_wait);
        tvTurnStatus.setTextSize(28f);
        tvTurnStatus.setTextColor(Color.parseColor("#212121"));
        tvTurnStatus.setGravity(Gravity.CENTER);
        tvTurnStatus.setPadding(0, 0, 0, 32);
        root.addView(tvTurnStatus);

        ivPictogram = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(200, 200);
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        imgParams.bottomMargin = 32;
        ivPictogram.setLayoutParams(imgParams);
        ivPictogram.setVisibility(android.view.View.INVISIBLE);
        root.addView(ivPictogram);

        btnTurn = new Button(this);
        btnTurn.setText(R.string.turns_done_button);
        btnTurn.setTextSize(20f);
        btnTurn.setMinHeight(80);
        btnTurn.setEnabled(false);
        btnTurn.setOnClickListener(v -> viewModel.onTurnButtonPressed());
        root.addView(btnTurn);
    }
}
