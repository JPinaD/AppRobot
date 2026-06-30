package com.example.approbot.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.approbot.R;
import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.util.AppConstants;

/**
 * Overlay de calma que se superpone sobre la Activity en curso sin destruirla.
 * Muestra una de las 4 experiencias de calma según el tipo configurado en el perfil del alumno.
 *
 * Tipos soportados:
 * - calm_breathing: círculo pulsante de respiración (4s ciclo)
 * - calm_colors: fondo que transita entre colores pastel (3s transición)
 * - calm_music: pantalla minimalista con reproducción del sonido del perfil
 * - calm_counting: números del 1 al 10 secuenciales (2s cada uno) + BREATHE del robot
 */
public class CalmOverlayFragment extends DialogFragment {

    public static final String TAG = "calm_overlay";
    private static final String ARG_CALM_TYPE = "calm_type";
    private static final String ARG_SOUND_RES = "sound_res_name";

    private ObjectAnimator breathAnimator;
    private ValueAnimator colorAnimator;
    private MediaPlayer mediaPlayer;
    private Handler handler;
    private boolean isRunning = false;

    public static CalmOverlayFragment newInstance(String calmType, String soundResName) {
        CalmOverlayFragment fragment = new CalmOverlayFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CALM_TYPE, calmType != null ? calmType : StudentProfile.DEFAULT_CALM_TYPE);
        args.putString(ARG_SOUND_RES, soundResName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Estilo fullscreen sin marco
        setStyle(DialogFragment.STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        setCancelable(false); // No se cierra con back

        requireActivity().getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() { /* bloqueado */ }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        handler = new Handler(Looper.getMainLooper());
        isRunning = true;

        String calmType = getArguments() != null
                ? getArguments().getString(ARG_CALM_TYPE, StudentProfile.DEFAULT_CALM_TYPE)
                : StudentProfile.DEFAULT_CALM_TYPE;

        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Botón de cierre (X) en esquina superior izquierda
        ImageButton btnClose = new ImageButton(requireContext());
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setContentDescription(getString(R.string.calm_close_desc));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(48 * 3, 48 * 3);
        closeParams.gravity = Gravity.TOP | Gravity.START;
        closeParams.setMargins(32, 32, 0, 0);
        btnClose.setLayoutParams(closeParams);
        btnClose.setPadding(16, 16, 16, 16);
        btnClose.setOnClickListener(v -> closeOverlay());

        switch (calmType) {
            case "calm_colors":
                buildColorsView(root);
                break;
            case "calm_music":
                buildMusicView(root);
                break;
            case "calm_counting":
                buildCountingView(root);
                break;
            case "calm_breathing":
            default:
                buildBreathingView(root);
                break;
        }

        root.addView(btnClose);

        // Enviar BREATHE_START al Arduino
        startRobotBreathing();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (breathAnimator != null) breathAnimator.cancel();
        if (colorAnimator != null) colorAnimator.cancel();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopRobotBreathing();
    }

    private void closeOverlay() {
        if (getParentFragmentManager().findFragmentByTag(TAG) != null) {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    // --- Experiencia: Respiración guiada ---

    private void buildBreathingView(FrameLayout root) {
        root.setBackgroundColor(Color.parseColor("#E3F2FD"));

        // Círculo animado
        View circle = new View(requireContext());
        circle.setBackgroundResource(R.drawable.calm_circle_bg);
        int size = 400;
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(size, size);
        circleParams.gravity = Gravity.CENTER;
        circle.setLayoutParams(circleParams);
        root.addView(circle);

        // Texto
        TextView tv = new TextView(requireContext());
        tv.setText(R.string.calm_breathe);
        tv.setTextSize(24f);
        tv.setTextColor(Color.parseColor("#5C8A8A"));
        tv.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER;
        textParams.topMargin = size / 2 + 80;
        tv.setLayoutParams(textParams);
        root.addView(tv);

        // Animación: escala 0.6 → 1.0 → 0.6 en 4 segundos
        circle.post(() -> {
            circle.setPivotX(circle.getWidth() / 2f);
            circle.setPivotY(circle.getHeight() / 2f);
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.0f, 0.6f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.0f, 0.6f);
            breathAnimator = ObjectAnimator.ofPropertyValuesHolder(circle, scaleX, scaleY);
            breathAnimator.setDuration(4000);
            breathAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            breathAnimator.start();
        });
    }

    // --- Experiencia: Colores pastel ---

    private void buildColorsView(FrameLayout root) {
        int[] colors = {
                Color.parseColor("#E3F2FD"),
                Color.parseColor("#F3E5F5"),
                Color.parseColor("#E8F5E9"),
                Color.parseColor("#FFF8E1")
        };
        root.setBackgroundColor(colors[0]);

        // Transición cíclica entre colores: 3s por transición
        colorAnimator = ValueAnimator.ofInt(0, colors.length);
        colorAnimator.setDuration((long) colors.length * 3000);
        colorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            int totalSegments = colors.length;
            float segFraction = fraction * totalSegments;
            int idx = (int) segFraction % totalSegments;
            int nextIdx = (idx + 1) % totalSegments;
            float localFrac = segFraction - (int) segFraction;
            int color = (int) new ArgbEvaluator().evaluate(localFrac, colors[idx], colors[nextIdx]);
            root.setBackgroundColor(color);
        });
        colorAnimator.start();
    }

    // --- Experiencia: Música ambiental ---

    private void buildMusicView(FrameLayout root) {
        root.setBackgroundColor(Color.parseColor("#F3E5F5"));

        // Icono de nota musical (texto grande como placeholder)
        TextView tvIcon = new TextView(requireContext());
        tvIcon.setText("♪");
        tvIcon.setTextSize(120f);
        tvIcon.setTextColor(Color.parseColor("#5C8A8A"));
        tvIcon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        iconParams.gravity = Gravity.CENTER;
        tvIcon.setLayoutParams(iconParams);
        root.addView(tvIcon);

        // Reproducir sonido del perfil
        String soundRes = getArguments() != null ? getArguments().getString(ARG_SOUND_RES) : null;
        if (soundRes != null && !soundRes.isEmpty()) {
            int resId = requireContext().getResources().getIdentifier(
                    soundRes, "raw", requireContext().getPackageName());
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(requireContext(), resId);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.start();
                }
            }
        }
    }

    // --- Experiencia: Conteo guiado ---

    private void buildCountingView(FrameLayout root) {
        root.setBackgroundColor(Color.parseColor("#E8F5E9"));

        TextView tvNumber = new TextView(requireContext());
        tvNumber.setTextSize(120f);
        tvNumber.setTextColor(Color.parseColor("#5C8A8A"));
        tvNumber.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams numParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        numParams.gravity = Gravity.CENTER;
        tvNumber.setLayoutParams(numParams);
        root.addView(tvNumber);

        // Mostrar números del 1 al 10, cada 2 segundos, luego ciclo
        startCounting(tvNumber, 1);
    }

    private void startCounting(TextView tvNumber, int current) {
        if (!isRunning || current > 10) {
            // Reiniciar el ciclo
            if (isRunning) handler.postDelayed(() -> startCounting(tvNumber, 1), 1000);
            return;
        }
        tvNumber.setText(String.valueOf(current));
        handler.postDelayed(() -> startCounting(tvNumber, current + 1), 2000);
    }

    // --- Comandos BT ---

    private void startRobotBreathing() {
        BluetoothRobotManager bt = SessionNetworkHolder.getBluetoothManager();
        if (bt != null) {
            bt.send(new RobotMessage(AppConstants.MSG_BREATHE_START, null));
        }
    }

    private void stopRobotBreathing() {
        BluetoothRobotManager bt = SessionNetworkHolder.getBluetoothManager();
        if (bt != null) {
            bt.send(new RobotMessage(AppConstants.MSG_BREATHE_STOP, null));
        }
    }
}
