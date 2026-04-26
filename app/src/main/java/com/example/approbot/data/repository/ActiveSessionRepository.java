package com.example.approbot.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.approbot.data.model.SessionConfig;
import com.example.approbot.util.AppConstants;

/**
 * Persiste el estado de sesión activa en SharedPreferences.
 * Permite detectar sesiones interrumpidas tras un reinicio.
 */
public class ActiveSessionRepository {

    private static final String TAG = "ActiveSessionRepository";
    private static final String PREFS_NAME = "active_session_prefs";
    private static final String KEY_SESSION_JSON = "session_json";

    private final SharedPreferences prefs;

    public ActiveSessionRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Persiste la configuración de sesión activa. */
    public void save(SessionConfig config) {
        if (config == null) return;
        try {
            String json = config.toJson();
            prefs.edit().putString(KEY_SESSION_JSON, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error guardando sesión activa", e);
        }
    }

    /**
     * Recupera la sesión activa persistida.
     * Devuelve null si no hay estado o está corrupto.
     */
    public SessionConfig load() {
        String json = prefs.getString(KEY_SESSION_JSON, null);
        if (json == null) return null;
        try {
            return SessionConfig.fromJson(json);
        } catch (Exception e) {
            Log.w(TAG, "Estado de sesión corrupto, descartando");
            clear();
            return null;
        }
    }

    /** Elimina el estado de sesión activa. */
    public void clear() {
        prefs.edit().remove(KEY_SESSION_JSON).apply();
    }
}
