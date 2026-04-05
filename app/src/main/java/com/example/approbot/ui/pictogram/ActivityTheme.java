package com.example.approbot.ui.pictogram;

import android.graphics.Color;

import com.example.approbot.data.model.StudentProfile;

/**
 * Utilidades de adaptación visual según el perfil del alumno.
 */
public class ActivityTheme {

    // Color de confirmación por defecto (verde suave)
    public static final String DEFAULT_CONFIRMATION_COLOR = "#C8E6C9";
    // Color alternativo neutro cuando el verde está excluido
    private static final String FALLBACK_CONFIRMATION_COLOR = "#E0E0E0";

    private ActivityTheme() {}

    public static boolean isColorExcluded(StudentProfile profile, String hexColor) {
        if (profile == null || profile.excludedColors.isEmpty()) return false;
        return profile.excludedColors.contains(hexColor.toUpperCase());
    }

    public static int resolveConfirmationColor(StudentProfile profile) {
        if (isColorExcluded(profile, DEFAULT_CONFIRMATION_COLOR)) {
            return Color.parseColor(FALLBACK_CONFIRMATION_COLOR);
        }
        return Color.parseColor(DEFAULT_CONFIRMATION_COLOR);
    }
}
