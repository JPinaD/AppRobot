package com.example.approbot.ui.pictogram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.ui.waiting.WaitingSessionActivity;
import com.example.approbot.util.AppConstants;
import com.example.approbot.viewmodel.PictogramViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PictogramActivity extends AppCompatActivity {

    public static final String EXTRA_PICTOGRAMS     = "pictograms";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private PictogramViewModel viewModel;
    private GridLayout gridPictograms;
    private LinearLayout layoutConfirmation;
    private TextView tvFeedback;

    private final BackgroundSoundPlayer soundPlayer = new BackgroundSoundPlayer();

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pictogram);

        gridPictograms     = findViewById(R.id.gridPictograms);
        layoutConfirmation = findViewById(R.id.layoutConfirmation);
        tvFeedback         = findViewById(R.id.tvFeedback);

        StudentProfile profile = null;
        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { profile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }

        if (profile != null && profile.backgroundSoundResName != null)
            soundPlayer.play(this, profile.backgroundSoundResName);

        viewModel = new ViewModelProvider(this).get(PictogramViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(),
                SessionNetworkHolder.getBluetoothManager(), profile);

        ArrayList<String> pictograms = getIntent().getStringArrayListExtra(EXTRA_PICTOGRAMS);
        if (pictograms == null || pictograms.isEmpty()) { finish(); return; }

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

        LocalBroadcastManager.getInstance(this).registerReceiver(
                sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndReceiver);
    }

    public PictogramViewModel getViewModel() { return viewModel; }

    private void buildGrid(List<String> pictogramIds) {
        for (String id : pictogramIds) {
            ImageButton btn = new ImageButton(this);
            int resId = getResources().getIdentifier(id, "drawable", getPackageName());

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(16, 16, 16, 16);
            btn.setLayoutParams(params);
            btn.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            btn.setPadding(16, 16, 16, 16);

            if (resId != 0) { btn.setImageResource(resId); btn.setBackgroundResource(resId); }
            else btn.setBackgroundColor(0xFFE0E0E0);

            btn.setContentDescription(id);
            btn.setOnClickListener(v -> onPictogramClicked(id));
            gridPictograms.addView(btn);
        }
    }

    private void onPictogramClicked(String pictogramId) {
        for (int i = 0; i < gridPictograms.getChildCount(); i++)
            gridPictograms.getChildAt(i).setEnabled(false);
        viewModel.onPictogramSelected(pictogramId);
    }
}
