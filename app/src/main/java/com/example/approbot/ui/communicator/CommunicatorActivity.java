package com.example.approbot.ui.communicator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
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
import com.example.approbot.ui.pictogram.PauseOverlayFragment;
import com.example.approbot.util.AppConstants;
import com.example.approbot.util.TtsHelper;
import com.example.approbot.viewmodel.CommunicatorViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Actividad Comunicador Bidireccional con pictogramas.
 * Reemplaza a PictogramActivity. Permite al alumno componer secuencias de pictogramas
 * y enviarlas al terapeuta, y recibir secuencias del terapeuta.
 */
public class CommunicatorActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_STUDENT_PROFILE = "student_profile_json";

    private static final int FEEDBACK_DISPLAY_MS = 3000;

    private CommunicatorViewModel viewModel;
    private StudentProfile studentProfile;

    private LinearLayout layoutComposedPictos;
    private GridLayout gridPictograms;
    private LinearLayout layoutCategories;
    private Button btnSend;
    private FrameLayout overlayTerapeuta;
    private LinearLayout layoutReceivedPictos;
    private FrameLayout overlayFeedback;
    private TextView tvFeedbackMessage;
    private FloatingActionButton fabCalm;

    private PictogramCatalog.Category currentCategory = PictogramCatalog.Category.NEEDS;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Broadcast Receivers ---

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

    private final BroadcastReceiver terapeutaPictogramReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            try {
                JSONObject obj = new JSONObject(payload);
                org.json.JSONArray arr = obj.optJSONArray("pictogramIds");
                if (arr != null) {
                    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) ids.add(arr.getString(i));
                    viewModel.onTerapeutaPictogramMessage(ids);
                }
            } catch (JSONException e) {
                android.util.Log.w("CommunicatorActivity", "Error parsing TERAPEUTA_PICTOGRAM_MESSAGE", e);
            }
        }
    };

    private final BroadcastReceiver communicatorResponseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            try {
                JSONObject obj = new JSONObject(payload);
                boolean understood = obj.optBoolean("understood", false);
                viewModel.onCommunicatorResponse(understood);
            } catch (JSONException e) {
                android.util.Log.w("CommunicatorActivity", "Error parsing COMMUNICATOR_RESPONSE", e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communicator);

        // Parse student profile
        String profileJson = getIntent().getStringExtra(EXTRA_STUDENT_PROFILE);
        if (profileJson != null) {
            try { studentProfile = StudentProfile.fromJson(new JSONObject(profileJson)); }
            catch (JSONException ignored) {}
        }

        // Find views
        layoutComposedPictos = findViewById(R.id.layoutComposedPictos);
        gridPictograms = findViewById(R.id.gridPictograms);
        layoutCategories = findViewById(R.id.layoutCategories);
        btnSend = findViewById(R.id.btnSend);
        overlayTerapeuta = findViewById(R.id.overlayTerapeuta);
        layoutReceivedPictos = findViewById(R.id.layoutReceivedPictos);
        overlayFeedback = findViewById(R.id.overlayFeedback);
        tvFeedbackMessage = findViewById(R.id.tvFeedbackMessage);
        fabCalm = findViewById(R.id.fabCalm);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(CommunicatorViewModel.class);
        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        viewModel.init(SessionNetworkHolder.getTcpServer(), sessionId);

        // Status reporter
        RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(viewModel);

        // Build category tabs
        buildCategoryTabs();

        // Load initial category
        loadCategoryGrid(currentCategory);

        // Send button
        btnSend.setOnClickListener(v -> viewModel.sendSequence());

        // FAB de calma
        fabCalm.setOnClickListener(v -> openCalmOverlay());

        // YES / NO buttons for terapeuta overlay
        findViewById(R.id.btnYes).setOnClickListener(v -> viewModel.respondToTerapeuta(true));
        findViewById(R.id.btnNo).setOnClickListener(v -> viewModel.respondToTerapeuta(false));

        // Observe state
        viewModel.getState().observe(this, this::onStateChanged);

        // Observe composition bar
        viewModel.getCompositionBar().observe(this, this::updateCompositionBar);

        // Observe received pictograms
        viewModel.getReceivedPictograms().observe(this, this::showReceivedPictograms);

        // Register receivers
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
        lbm.registerReceiver(pauseReceiver, new IntentFilter(AppConstants.ACTION_SESSION_PAUSE));
        lbm.registerReceiver(resumeReceiver, new IntentFilter(AppConstants.ACTION_SESSION_RESUME));
        lbm.registerReceiver(tiltAlertReceiver, new IntentFilter(AppConstants.ACTION_TILT_ALERT));
        lbm.registerReceiver(terapeutaPictogramReceiver, new IntentFilter(AppConstants.ACTION_TERAPEUTA_PICTOGRAM));
        lbm.registerReceiver(communicatorResponseReceiver, new IntentFilter(AppConstants.ACTION_COMMUNICATOR_RESPONSE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(sessionEndReceiver);
        lbm.unregisterReceiver(pauseReceiver);
        lbm.unregisterReceiver(resumeReceiver);
        lbm.unregisterReceiver(tiltAlertReceiver);
        lbm.unregisterReceiver(terapeutaPictogramReceiver);
        lbm.unregisterReceiver(communicatorResponseReceiver);
    }

    // --- State management ---

    private void onStateChanged(CommunicatorViewModel.State state) {
        switch (state) {
            case IDLE:
                overlayFeedback.setVisibility(View.GONE);
                overlayTerapeuta.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                setGridEnabled(true);
                break;
            case SENDING:
                btnSend.setEnabled(false);
                setGridEnabled(false);
                tvFeedbackMessage.setText("Enviando...");
                overlayFeedback.setVisibility(View.VISIBLE);
                break;
            case RESPONSE_OK:
                tvFeedbackMessage.setText("✓ ¡Mensaje recibido!");
                overlayFeedback.setVisibility(View.VISIBLE);
                handler.postDelayed(() -> viewModel.resetAfterResponse(), FEEDBACK_DISPLAY_MS);
                break;
            case RESPONSE_FAIL:
                tvFeedbackMessage.setText("No se ha entendido, inténtalo de nuevo");
                overlayFeedback.setVisibility(View.VISIBLE);
                handler.postDelayed(() -> viewModel.resetAfterResponse(), FEEDBACK_DISPLAY_MS);
                break;
            case RECEIVING:
                overlayTerapeuta.setVisibility(View.VISIBLE);
                break;
        }
    }

    // --- Composition bar ---

    private void updateCompositionBar(List<String> pictogramIds) {
        layoutComposedPictos.removeAllViews();
        if (pictogramIds == null || pictogramIds.isEmpty()) {
            // Show placeholder text
            TextView placeholder = new TextView(this);
            placeholder.setText("Pulsa los pictogramas para componer tu mensaje");
            placeholder.setTextSize(16f);
            placeholder.setTextColor(0xFF9E9E9E);
            placeholder.setGravity(Gravity.CENTER);
            layoutComposedPictos.addView(placeholder);
            btnSend.setEnabled(false);
            return;
        }

        btnSend.setEnabled(viewModel.getState().getValue() == CommunicatorViewModel.State.IDLE);

        for (int i = 0; i < pictogramIds.size(); i++) {
            final int index = i;
            String pictoId = pictogramIds.get(i);
            PictogramCatalog.PictogramItem item = PictogramCatalog.findById(pictoId);

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(8, 4, 8, 4);

            // Image
            ImageView img = new ImageView(this);
            int resId = getResources().getIdentifier(pictoId, "drawable", getPackageName());
            if (resId != 0) img.setImageResource(resId);
            else img.setBackgroundColor(0xFFE0E0E0);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int sizePx = dpToPx(60);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(sizePx, sizePx);
            img.setLayoutParams(imgParams);
            cell.addView(img);

            // Small X button to remove
            TextView removeBtn = new TextView(this);
            removeBtn.setText("✕");
            removeBtn.setTextSize(14f);
            removeBtn.setTextColor(0xFFE53935);
            removeBtn.setGravity(Gravity.CENTER);
            removeBtn.setOnClickListener(v -> viewModel.removeFromComposition(index));
            cell.addView(removeBtn);

            layoutComposedPictos.addView(cell);
        }
    }

    // --- Category tabs ---

    private void buildCategoryTabs() {
        layoutCategories.removeAllViews();
        for (PictogramCatalog.Category cat : PictogramCatalog.Category.values()) {
            Button tab = new Button(this);
            tab.setText(cat.label);
            tab.setTextSize(14f);
            tab.setAllCaps(false);
            tab.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            tab.setLayoutParams(params);

            updateTabStyle(tab, cat == currentCategory);

            tab.setOnClickListener(v -> {
                currentCategory = cat;
                loadCategoryGrid(cat);
                updateAllTabStyles();
            });

            layoutCategories.addView(tab);
        }
    }

    private void updateAllTabStyles() {
        for (int i = 0; i < layoutCategories.getChildCount(); i++) {
            Button tab = (Button) layoutCategories.getChildAt(i);
            PictogramCatalog.Category cat = PictogramCatalog.Category.values()[i];
            updateTabStyle(tab, cat == currentCategory);
        }
    }

    private void updateTabStyle(Button tab, boolean selected) {
        if (selected) {
            tab.setBackgroundColor(0xFF1976D2);
            tab.setTextColor(Color.WHITE);
            tab.setTypeface(null, Typeface.BOLD);
        } else {
            tab.setBackgroundColor(0xFFE0E0E0);
            tab.setTextColor(0xFF424242);
            tab.setTypeface(null, Typeface.NORMAL);
        }
    }

    // --- Pictogram grid ---

    private void loadCategoryGrid(PictogramCatalog.Category category) {
        gridPictograms.removeAllViews();
        List<PictogramCatalog.PictogramItem> items = PictogramCatalog.getByCategory(category);

        // Adjust column count based on item count
        int cols = items.size() <= 4 ? 2 : 3;
        gridPictograms.setColumnCount(cols);

        for (PictogramCatalog.PictogramItem item : items) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundColor(0xFFFFFFFF);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            cell.setLayoutParams(params);
            cell.setElevation(dpToPx(2));

            // Image
            ImageView img = new ImageView(this);
            int resId = getResources().getIdentifier(item.id, "drawable", getPackageName());
            if (resId != 0) img.setImageResource(resId);
            else img.setBackgroundColor(0xFFE0E0E0);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);
            int imgSize = dpToPx(80);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(imgSize, imgSize);
            imgParams.setMargins(0, dpToPx(4), 0, dpToPx(4));
            img.setLayoutParams(imgParams);
            cell.addView(img);

            // Label
            TextView label = new TextView(this);
            label.setText(item.label);
            label.setTextSize(16f);
            label.setTextColor(0xFF212121);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(null, Typeface.BOLD);
            cell.addView(label);

            cell.setContentDescription(item.label);
            cell.setOnClickListener(v -> viewModel.addToComposition(item.id));

            gridPictograms.addView(cell);
        }
    }

    private void setGridEnabled(boolean enabled) {
        for (int i = 0; i < gridPictograms.getChildCount(); i++) {
            gridPictograms.getChildAt(i).setEnabled(enabled);
            gridPictograms.getChildAt(i).setAlpha(enabled ? 1f : 0.5f);
        }
    }

    // --- Received pictograms overlay ---

    private void showReceivedPictograms(List<String> pictogramIds) {
        if (pictogramIds == null || pictogramIds.isEmpty()) return;
        layoutReceivedPictos.removeAllViews();

        for (String pictoId : pictogramIds) {
            PictogramCatalog.PictogramItem item = PictogramCatalog.findById(pictoId);

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

            ImageView img = new ImageView(this);
            int resId = getResources().getIdentifier(pictoId, "drawable", getPackageName());
            if (resId != 0) img.setImageResource(resId);
            else img.setBackgroundColor(0xFFE0E0E0);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int sizePx = dpToPx(80);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(sizePx, sizePx);
            img.setLayoutParams(imgParams);
            cell.addView(img);

            if (item != null) {
                TextView label = new TextView(this);
                label.setText(item.label);
                label.setTextSize(14f);
                label.setTextColor(0xFF424242);
                label.setGravity(Gravity.CENTER);
                cell.addView(label);
            }

            layoutReceivedPictos.addView(cell);
        }

        // Speak via TTS
        if (TtsHelper.getInstance().isReady()) {
            StringBuilder sb = new StringBuilder();
            for (String pictoId : pictogramIds) {
                PictogramCatalog.PictogramItem item = PictogramCatalog.findById(pictoId);
                if (item != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(item.label);
                }
            }
            if (sb.length() > 0) TtsHelper.getInstance().speak(sb.toString());
        }
    }

    // --- Calm overlay ---

    private void openCalmOverlay() {
        if (getSupportFragmentManager().findFragmentByTag(CalmOverlayFragment.TAG) != null) return;
        String calmType = studentProfile != null ? studentProfile.calmType : StudentProfile.DEFAULT_CALM_TYPE;
        String soundRes = studentProfile != null ? studentProfile.backgroundSoundResName : null;
        CalmOverlayFragment.newInstance(calmType, soundRes)
                .show(getSupportFragmentManager(), CalmOverlayFragment.TAG);
    }

    // --- Pause overlay ---

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

    // --- Util ---

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
