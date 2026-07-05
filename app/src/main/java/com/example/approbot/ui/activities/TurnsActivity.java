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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.data.model.StudentProfile;
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

    private static final int COLOR_MY_TURN = 0xFFFFF9C4;
    private static final int COLOR_WAITING = 0xFFFAFAFA;

    private TurnsViewModel viewModel;
    private TextView tvTurnStatus;
    private TextView tvRound;
    private ImageView ivPictogram;
    private Button btnDone;
    private LinearLayout root;
    private StudentProfile studentProfile;
    private CalmFabHelper calmFabHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (calmFabHelper != null) calmFabHelper.hide();
        }
    };

    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (calmFabHelper != null) calmFabHelper.show();
        }
    };

    private final BroadcastReceiver turnSignalReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            try {
                JSONObject obj = new JSONObject(payload);
                boolean active = obj.optBoolean("active", false);
                viewModel.onTurnSignalReceived(active);
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
        ArrayList<String> items = getIntent().getStringArrayListExtra(EXTRA_ITEMS);

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
                SessionNetworkHolder.getBluetoothManager(), sessionId, items);

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        viewModel.getState().observe(this, state -> {
            switch (state) {
                case MY_TURN:
                    root.setBackgroundColor(COLOR_MY_TURN);
                    tvTurnStatus.setText(R.string.turns_my_turn);
                    btnDone.setEnabled(true);
                    ivPictogram.setVisibility(android.view.View.VISIBLE);
                    break;
                case WAITING:
                    root.setBackgroundColor(COLOR_WAITING);
                    tvTurnStatus.setText(R.string.turns_wait);
                    btnDone.setEnabled(false);
                    ivPictogram.setVisibility(android.view.View.INVISIBLE);
                    // In solo mode, auto-start next turn after 2s
                    if (viewModel.isSoloMode() && viewModel.getRoundNumber().getValue() != null
                            && viewModel.getRoundNumber().getValue() > 0) {
                        handler.postDelayed(() -> viewModel.startSoloTurn(), 2000);
                    }
                    break;
                case COMPLETED:
                    root.setBackgroundColor(0xFFC8E6C9);
                    tvTurnStatus.setText("¡Completado!");
                    btnDone.setEnabled(false);
                    break;
            }
        });

        viewModel.getCurrentPictogram().observe(this, picId -> {
            if (picId == null) return;
            int resId = getResources().getIdentifier(picId, "drawable", getPackageName());
            if (resId != 0) ivPictogram.setImageResource(resId);
        });

        viewModel.getRoundNumber().observe(this, round ->
                tvRound.setText("Ronda: " + round));

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(turnSignalReceiver, new IntentFilter(AppConstants.ACTION_TURN_SIGNAL));
        lbm.registerReceiver(tiltAlertReceiver, new IntentFilter(AppConstants.ACTION_TILT_ALERT));

        // In solo mode, start first turn after a brief delay
        handler.postDelayed(() -> viewModel.startSoloTurn(), 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(turnSignalReceiver);
        lbm.unregisterReceiver(tiltAlertReceiver);
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(COLOR_WAITING);
        root.setPadding(32, 48, 32, 48);
        setContentView(root);

        tvRound = new TextView(this);
        tvRound.setTextSize(14f);
        tvRound.setTextColor(Color.parseColor("#616161"));
        tvRound.setGravity(Gravity.CENTER);
        tvRound.setText("Ronda: 0");
        root.addView(tvRound);

        tvTurnStatus = new TextView(this);
        tvTurnStatus.setText(R.string.turns_wait);
        tvTurnStatus.setTextSize(28f);
        tvTurnStatus.setTextColor(Color.parseColor("#212121"));
        tvTurnStatus.setGravity(Gravity.CENTER);
        tvTurnStatus.setPadding(0, 16, 0, 32);
        root.addView(tvTurnStatus);

        ivPictogram = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(200, 200);
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        imgParams.bottomMargin = 32;
        ivPictogram.setLayoutParams(imgParams);
        ivPictogram.setVisibility(android.view.View.INVISIBLE);
        root.addView(ivPictogram);

        btnDone = new Button(this);
        btnDone.setText(R.string.turns_done_button);
        btnDone.setTextSize(20f);
        btnDone.setMinHeight(80);
        btnDone.setEnabled(false);
        btnDone.setOnClickListener(v -> viewModel.onTurnCompleted());
        root.addView(btnDone);
    }
}
