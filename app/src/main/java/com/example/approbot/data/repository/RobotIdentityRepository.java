package com.example.approbot.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.approbot.util.AppConstants;

/**
 * Persiste la identidad del robot (nombre, puerto, MAC del HC-05) en SharedPreferences.
 */
public class RobotIdentityRepository {

    private static final String PREFS_NAME = "robot_identity";
    private static final String KEY_ROBOT_NAME = "robot_name";
    private static final String KEY_PORT = "robot_port";

    private final SharedPreferences prefs;

    public RobotIdentityRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Devuelve el nombre del robot, creándolo con el valor por defecto si no existe. */
    public String getRobotName(String defaultName) {
        return prefs.getString(KEY_ROBOT_NAME, defaultName);
    }

    /** Devuelve el puerto TCP del robot. */
    public int getPort() {
        return prefs.getInt(KEY_PORT, AppConstants.NSD_DEFAULT_PORT);
    }

    /** Devuelve el MAC del HC-05, o null si no está configurado. */
    public String getHcMac() {
        return prefs.getString(AppConstants.BT_PREFS_KEY_MAC, null);
    }

    /** Persiste el MAC del HC-05. */
    public void saveHcMac(String mac) {
        prefs.edit().putString(AppConstants.BT_PREFS_KEY_MAC, mac).apply();
    }
}
