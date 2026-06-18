package com.example.approbot.kiosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

/**
 * Gestiona el modo kiosko de AppRobot usando Screen Pinning (startLockTask).
 * No requiere permisos especiales ni dispositivo administrado.
 */
public class KioskModeManager {

    private static final String TAG = "KioskModeManager";

    private KioskModeManager() {}

    /** Activa el modo kiosko. Llamar desde el hilo principal. */
    public static void enter(Activity activity) {
        try {
            activity.startLockTask();
            Log.i(TAG, "Modo kiosko activado");
        } catch (Exception e) {
            Log.w(TAG, "No se pudo activar el modo kiosko: " + e.getMessage());
        }
    }

    /** Desactiva el modo kiosko. Llamar desde el hilo principal. */
    public static void exit(Activity activity) {
        try {
            activity.stopLockTask();
            Log.i(TAG, "Modo kiosko desactivado");
        } catch (Exception e) {
            Log.w(TAG, "No se pudo desactivar el modo kiosko: " + e.getMessage());
        }
    }

    /** Devuelve true si la app está actualmente en modo kiosko. */
    public static boolean isActive(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        int mode = am.getLockTaskModeState();
        return mode != ActivityManager.LOCK_TASK_MODE_NONE;
    }
}
