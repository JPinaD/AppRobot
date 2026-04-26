package com.example.approbot.network;

public interface ActivityStatusProvider {
    /** Porcentaje de batería del dispositivo (0-100), o null si no disponible. */
    Integer getBatteryPct();
    /** ID de la actividad en curso, o null si no hay actividad. */
    String getActivityId();
    /** Progreso de la actividad (0-100), o null si no hay actividad. */
    Integer getProgressPct();
}
