package com.example.approbot.ui.activities;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.network.ActivityStatusProvider;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;

/**
 * Actividad de calma con respiracion sincronizada.
 * El robot se mueve adelante/atras lentamente mientras la pantalla muestra
 * un circulo que pulsa al ritmo de la respiracion.
 */
public class CalmActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";

    private ObjectAnimator breathAnimator;
    private BluetoothRobotManager btManager;

    private final BroadcastReceiver sessionEndReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            stopRobotBreathing();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        btManager = SessionNetworkHolder.getBluetoothManager();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#E0F7FA"));
        setContentView(root);

        // Circulo animado
        View circle = new View(this);
        circle.setBackgroundColor(Color.parseColor("#80DEEA"));
        int size = 300;
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(size, size);
        circleParams.gravity = Gravity.CENTER;
        circle.setLayoutParams(circleParams);
        circle.post(() -> {
            circle.setPivotX(circle.getWidth() / 2f);
            circle.setPivotY(circle.getHeight() / 2f);
        });
        root.addView(circle);

        // Texto
        TextView tvCalm = new TextView(this);
        tvCalm.setText(R.string.calm_breathe);
        tvCalm.setTextSize(24f);
        tvCalm.setTextColor(Color.parseColor("#00838F"));
        tvCalm.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER;
        tvCalm.setLayoutParams(textParams);
        root.addView(tvCalm);

        // Animacion: escala 0.6 -> 1.0 -> 0.6, 4 segundos (sincronizado con BREATHE_HALF_MS * 2)
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.0f, 0.6f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.0f, 0.6f);
        breathAnimator = ObjectAnimator.ofPropertyValuesHolder(circle, scaleX, scaleY);
        breathAnimator.setDuration(4000);
        breathAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathAnimator.start();

        // Iniciar movimiento de respiracion en el robot
        startRobotBreathing();

        com.example.approbot.network.RobotStatusReporter reporter = SessionNetworkHolder.getStatusReporter();
        if (reporter != null) reporter.setStatusProvider(new ActivityStatusProvider() {
            @Override public Integer getBatteryPct() { return null; }
            @Override public String getActivityId() { return AppConstants.ACTIVITY_CALM; }
            @Override public Integer getProgressPct() { return null; }
        });

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(sessionEndReceiver, new IntentFilter(AppConstants.ACTION_SESSION_END));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (breathAnimator != null) breathAnimator.cancel();
        stopRobotBreathing();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndReceiver);
    }

    private void startRobotBreathing() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_BREATHE_START, null));
    }

    private void stopRobotBreathing() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_BREATHE_STOP, null));
    }
}
