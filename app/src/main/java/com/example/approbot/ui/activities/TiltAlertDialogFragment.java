package com.example.approbot.ui.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.approbot.util.TtsHelper;

/**
 * Overlay amigable que se muestra al alumno cuando el robot detecta un vuelco (TILT_ALERT).
 * <p>
 * Diseño sensorial TEA:
 * - Fondo semi-transparente suave (no negro intenso)
 * - Card central con fondo crema claro y esquinas redondeadas
 * - Emoji grande (😴) como indicador visual amigable
 * - Mensaje en lenguaje positivo y comprensible para el alumno
 * - Se reproduce el mensaje por TTS
 * - Auto-dismiss tras 6 segundos
 * - El alumno no puede cerrarlo manualmente (setCancelable false)
 */
public class TiltAlertDialogFragment extends DialogFragment {

    public static final String TAG = "tilt_alert_overlay";
    private static final long AUTO_DISMISS_MS = 6000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismissRunnable = () -> {
        if (isAdded() && !isStateSaved()) {
            dismissAllowingStateLoss();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen sin marco, igual que CalmOverlayFragment
        setStyle(DialogFragment.STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        setCancelable(false);

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
        // Root: fondo semi-transparente oscuro suave
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(Color.argb(180, 30, 30, 50));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setClickable(true);
        root.setFocusable(true);

        // Card central con esquinas redondeadas
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(32), dpToPx(40), dpToPx(32), dpToPx(40));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#FFFBF0")); // Crema suave
        cardBg.setCornerRadius(dpToPx(24));
        card.setBackground(cardBg);
        card.setElevation(dpToPx(8));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                dpToPx(320), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        card.setLayoutParams(cardParams);

        // Emoji grande (robot dormido)
        TextView emojiView = new TextView(requireContext());
        emojiView.setText("\uD83D\uDE34"); // 😴
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        emojiView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emojiParams.bottomMargin = dpToPx(24);
        emojiView.setLayoutParams(emojiParams);
        card.addView(emojiView);

        // Mensaje amigable
        TextView messageView = new TextView(requireContext());
        messageView.setText("¡No me cojas, que me canso\ny me tengo que ir a dormir!");
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        messageView.setTextColor(Color.parseColor("#212121"));
        messageView.setTypeface(Typeface.DEFAULT_BOLD);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(dpToPx(4), 1f);
        card.addView(messageView);

        root.addView(card);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reproducir mensaje por TTS
        TtsHelper.getInstance().speak("¡No me cojas, que me canso y me tengo que ir a dormir!");
        // Auto-dismiss tras 6 segundos
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(autoDismissRunnable);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                requireContext().getResources().getDisplayMetrics());
    }
}
