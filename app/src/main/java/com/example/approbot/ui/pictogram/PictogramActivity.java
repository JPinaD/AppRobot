package com.example.approbot.ui.pictogram;

import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.approbot.R;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.ui.waiting.WaitingSessionActivity;
import com.example.approbot.viewmodel.PictogramViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Muestra los pictogramas al alumno. Recibe la lista de ids desde el Intent.
 * Al pulsar uno, muestra confirmación visual y notifica al ViewModel.
 */
public class PictogramActivity extends AppCompatActivity {

    public static final String EXTRA_PICTOGRAMS = "pictograms";

    private PictogramViewModel viewModel;
    private GridLayout gridPictograms;
    private LinearLayout layoutConfirmation;
    private TextView tvFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pictogram);

        gridPictograms     = findViewById(R.id.gridPictograms);
        layoutConfirmation = findViewById(R.id.layoutConfirmation);
        tvFeedback         = findViewById(R.id.tvFeedback);

        viewModel = new ViewModelProvider(this).get(PictogramViewModel.class);
        viewModel.init(SessionNetworkHolder.getTcpServer(), SessionNetworkHolder.getBluetoothManager());

        ArrayList<String> pictograms = getIntent().getStringArrayListExtra(EXTRA_PICTOGRAMS);
        if (pictograms == null || pictograms.isEmpty()) {
            finish();
            return;
        }

        buildGrid(pictograms);

        viewModel.getSelectionConfirmed().observe(this, confirmed -> {
            if (Boolean.TRUE.equals(confirmed)) {
                gridPictograms.setVisibility(View.GONE);
                layoutConfirmation.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getFeedbackText().observe(this, text -> {
            if (text != null && !text.isEmpty()) {
                tvFeedback.setText(text);
                tvFeedback.setVisibility(View.VISIBLE);
            }
        });
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

    public PictogramViewModel getViewModel() {
        return viewModel;
    }

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

            if (resId != 0) {
                btn.setImageResource(resId);
                btn.setBackgroundResource(resId);
            } else {
                btn.setBackgroundColor(0xFFE0E0E0);
            }

            btn.setContentDescription(id);
            btn.setOnClickListener(v -> onPictogramClicked(id));
            gridPictograms.addView(btn);
        }
    }

    private void onPictogramClicked(String pictogramId) {
        // Deshabilitar todos los botones para evitar doble pulsación (T17)
        for (int i = 0; i < gridPictograms.getChildCount(); i++) {
            gridPictograms.getChildAt(i).setEnabled(false);
        }
        viewModel.onPictogramSelected(pictogramId);
    }
}
