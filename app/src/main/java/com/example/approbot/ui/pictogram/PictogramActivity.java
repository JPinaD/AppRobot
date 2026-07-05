package com.example.approbot.ui.pictogram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.RobotStatusReporter;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.ui.activities.CalmOverlayFragment;
import com.example.approbot.ui.activities.TiltAlertDialogFragment;
import com.example.approbot.ui.waiting.WaitingSessionActivity;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.PictogramViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PictogramActivity extends AppCompatActivity {

    public static final String EXTRA_PICTOGRAMS      = "pictograms";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private PictogramViewModel viewModel;
    private GridLayout gridPictograms;
    private LinearLayout layoutConfirmation;
    private TextView tvFeedback;
    private FloatingActionButton fabCalm;

    private StudentProfile studentProfile;
    private final BackgroundSoundPlayer soundPlayer = new BackgroundSoundPlayer();

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { finish(); }
    };

    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { showPauseOverlay(); }
    };

    private final BroadcastReceiver resumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { hidePauseOverlay(); }
    };

    private final BroadcastReceiver tiltAlertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getSupportFragmentManager().findFragmentByTag(TiltAlertDialogFragment.TAG) == null) {
                new TiltAlertDialogFragment().show(getSupportFragmentManager(), TiltAlertDialogFragment.TAG);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pictogram);

        gridPictograms     = findViewById(R.id.gridPictograms);
        layoutConfirmation = findViewById(R.id.layoutConfirmation);
        tvFeedback         = findViewById(R.id.tvFeedback);
        fabCalm            = findViewById(R.id.fabCalm);

        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { studentProfile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }

        if (studentProfile != null && studentProfile.backgroundSoundResName != null)
            soundPlayer.play(this, studentProfile.backgroundSoundResName);

        viewModel = new ViewModelProvider(this).get(PictogramViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), studentProfile);

        ArrayList<String> pictograms = getIntent().getStringArrayListExtra(EXTRA_PICTOGRAMS);
        if (pictograms == null || pictograms.isEmpty()) { finish(); return; }

        viewModel.setTotalPictograms(pictograms.size());

        RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        buildGrid(pictograms);

        viewModel.getSelectionConfirmed().observe(this, confirmed -> {
            if (Boolean.TRUE.equals(confirmed)) {
                gridPictograms.setVisibility(View.GONE);
                layoutConfirmation.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getConfirmationColor().observe(this, color -> {
            if (color != null) layoutConfirmation.setBackgroundColor(color);
        });

        viewModel.getFeedbackText().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                tvFeedback.setText(text);
                tvFeedback.setVisibility(View.VISIBLE);
            }
        });

        // FAB de calma
        fabCalm.setOnClickListener(v -> openCalmOverlay());

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver,      new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver,     new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(tiltAlertReceiver,  new IntentFilter(AppConstants.ACTION_TILT_ALERT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        WaitingSessionActivity.registerPictogramActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        WaitingSessionActivity.unregisterPictogramActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        soundPlayer.stop();
        viewModel.onActivityFinished();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(tiltAlertReceiver);
    }

    public PictogramViewModel getViewModel() { return viewModel; }

    private void openCalmOverlay() {
        if (getSupportFragmentManager().findFragmentByTag(CalmOverlayFragment.TAG) != null) return;
        String calmType = studentProfile != null ? studentProfile.calmType : StudentProfile.DEFAULT_CALM_TYPE;
        String soundRes = studentProfile != null ? studentProfile.backgroundSoundResName : null;
        CalmOverlayFragment.newInstance(calmType, soundRes)
                .show(getSupportFragmentManager(), CalmOverlayFragment.TAG);
    }

    private void showPauseOverlay() {
        fabCalm.setVisibility(View.GONE);
        if (getSupportFragmentManager().findFragmentByTag(PauseOverlayFragment.TAG) != null) return;
        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, new PauseOverlayFragment(), PauseOverlayFragment.TAG)
                .commit();
    }

    private void hidePauseOverlay() {
        fabCalm.setVisibility(View.VISIBLE);
        androidx.fragment.app.Fragment overlay =
                getSupportFragmentManager().findFragmentByTag(PauseOverlayFragment.TAG);
        if (overlay != null) {
            getSupportFragmentManager().beginTransaction().remove(overlay).commit();
        }
    }

    private void buildGrid(List<String> pictogramIds) {
        for (String id : pictogramIds) {
            // Contenedor vertical: imagen + etiqueta
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setBackgroundColor(0xFFFFFFFF);
            cell.setClickable(true);
            cell.setFocusable(true);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(16, 16, 16, 16);
            cell.setLayoutParams(params);

            // Imagen
            android.widget.ImageView img = new android.widget.ImageView(this);
            int resId = getResources().getIdentifier(id, "drawable", getPackageName());
            if (resId != 0) img.setImageResource(resId);
            else img.setBackgroundColor(0xFFE0E0E0);
            img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            imgParams.setMargins(16, 16, 16, 8);
            img.setLayoutParams(imgParams);
            cell.addView(img);

            // Etiqueta
            TextView label = new TextView(this);
            String labelText = id.startsWith("pic_") ? capitalize(id.substring(4)) : id;
            label.setText(labelText);
            label.setTextSize(16f);
            label.setTextColor(0xFF212121);
            label.setGravity(android.view.Gravity.CENTER);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.setMargins(8, 0, 8, 12);
            label.setLayoutParams(labelParams);
            cell.addView(label);

            cell.setContentDescription(labelText);
            cell.setOnClickListener(v -> onPictogramClicked(id));
            gridPictograms.addView(cell);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void onPictogramClicked(String pictogramId) {
        for (int i = 0; i < gridPictograms.getChildCount(); i++)
            gridPictograms.getChildAt(i).setEnabled(false);
        viewModel.onPictogramSelected(pictogramId);
        RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.sendImmediate();
    }
}
