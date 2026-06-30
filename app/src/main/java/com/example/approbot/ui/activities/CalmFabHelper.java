package com.example.approbot.ui.activities;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.approbot.R;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.ui.pictogram.PauseOverlayFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Helper para añadir el FAB de Momento de Calma a las Activities de actividad.
 * Uso:
 *   CalmFabHelper helper = CalmFabHelper.attach(activity, rootView, studentProfile);
 *   // En pausa:  helper.hide();
 *   // En resume: helper.show();
 */
public class CalmFabHelper {

    private final FloatingActionButton fab;
    private final AppCompatActivity activity;
    private final String calmType;
    private final String soundResName;

    private CalmFabHelper(AppCompatActivity activity, FloatingActionButton fab,
                          String calmType, String soundResName) {
        this.activity = activity;
        this.fab = fab;
        this.calmType = calmType;
        this.soundResName = soundResName;
    }

    /**
     * Crea y añade el FAB de calma a un FrameLayout raíz.
     * Si el layout raíz es LinearLayout, envolverlo en un FrameLayout primero.
     */
    public static CalmFabHelper attach(AppCompatActivity activity, FrameLayout root,
                                       StudentProfile profile) {
        FloatingActionButton fab = new FloatingActionButton(activity);
        fab.setImageResource(R.drawable.ic_calm);
        fab.setImageTintList(null); // Preserve vector drawable colors
        fab.setContentDescription(activity.getString(R.string.calm_fab_desc));
        fab.setBackgroundTintList(ContextCompat.getColorStateList(activity, R.color.calm_fab_bg));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, dpToPx(activity, 16), dpToPx(activity, 16), 0);
        fab.setLayoutParams(params);
        root.addView(fab);

        String calmType = (profile != null) ? profile.calmType : StudentProfile.DEFAULT_CALM_TYPE;
        String soundRes = (profile != null) ? profile.backgroundSoundResName : null;

        CalmFabHelper helper = new CalmFabHelper(activity, fab, calmType, soundRes);
        fab.setOnClickListener(v -> helper.openCalmOverlay());
        return helper;
    }

    /**
     * Variante para Activities con layout raíz no-FrameLayout:
     * añade el FAB al FrameLayout android.R.id.content.
     */
    public static CalmFabHelper attachToContent(AppCompatActivity activity, StudentProfile profile) {
        FrameLayout contentFrame = activity.findViewById(android.R.id.content);

        FloatingActionButton fab = new FloatingActionButton(activity);
        fab.setImageResource(R.drawable.ic_calm);
        fab.setImageTintList(null); // Preserve vector drawable colors
        fab.setContentDescription(activity.getString(R.string.calm_fab_desc));
        fab.setBackgroundTintList(ContextCompat.getColorStateList(activity, R.color.calm_fab_bg));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, dpToPx(activity, 16), dpToPx(activity, 16), 0);
        fab.setLayoutParams(params);
        contentFrame.addView(fab);

        String calmType = (profile != null) ? profile.calmType : StudentProfile.DEFAULT_CALM_TYPE;
        String soundRes = (profile != null) ? profile.backgroundSoundResName : null;

        CalmFabHelper helper = new CalmFabHelper(activity, fab, calmType, soundRes);
        fab.setOnClickListener(v -> helper.openCalmOverlay());
        return helper;
    }

    /** Oculta el FAB (llamar durante SESSION_PAUSE). */
    public void hide() {
        fab.setVisibility(View.GONE);
    }

    /** Muestra el FAB (llamar durante SESSION_RESUME). */
    public void show() {
        fab.setVisibility(View.VISIBLE);
    }

    private void openCalmOverlay() {
        // No abrir si ya hay un overlay de calma
        Fragment existing = activity.getSupportFragmentManager().findFragmentByTag(CalmOverlayFragment.TAG);
        if (existing != null) return;
        // No abrir si estamos pausados
        Fragment pause = activity.getSupportFragmentManager().findFragmentByTag(PauseOverlayFragment.TAG);
        if (pause != null) return;

        CalmOverlayFragment.newInstance(calmType, soundResName)
                .show(activity.getSupportFragmentManager(), CalmOverlayFragment.TAG);
    }

    private static int dpToPx(AppCompatActivity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }
}
