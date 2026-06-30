package com.example.approbot.util;

public class AppConstants {
    public static final String NSD_SERVICE_TYPE  = "_approbot._tcp.";
    public static final int    NSD_DEFAULT_PORT  = 9000;
    public static final String NSD_ATTR_ROBOT_ID = "robotId";
    public static final String MSG_PING = "PING";
    public static final String MSG_PONG = "PONG";

    // Actividades
    public static final String MSG_ACTIVITY_START     = "ACTIVITY_START";
    public static final String MSG_ACTIVITY_RESULT    = "ACTIVITY_RESULT";
    public static final String MSG_PICTOGRAM_SELECTED = "PICTOGRAM_SELECTED";
    public static final String MSG_ROBOT_FEEDBACK     = "ROBOT_FEEDBACK";
    public static final String MSG_SERVO_COMMAND      = "SERVO_COMMAND";
    public static final String MSG_TURN_SIGNAL        = "TURN_SIGNAL";
    public static final String MSG_TURN_DONE          = "TURN_DONE";

    // IDs de actividades
    public static final String ACTIVITY_PICTOGRAM        = "activity_pictogram";
    public static final String ACTIVITY_PICTOGRAM_LEGACY = "pictogram_v1";
    public static final String ACTIVITY_EMOTION          = "activity_emotion";
    public static final String ACTIVITY_SOCIAL           = "activity_social";
    public static final String ACTIVITY_SEQUENCE         = "activity_sequence";
    public static final String ACTIVITY_CALM             = "activity_calm";
    public static final String ACTIVITY_TURNS            = "activity_turns";

    // Sesiones
    public static final String MSG_SESSION_START  = "SESSION_START";
    public static final String MSG_SESSION_END    = "SESSION_END";
    public static final String MSG_SESSION_READY  = "SESSION_READY";
    public static final String MSG_SESSION_ENDED  = "SESSION_ENDED";

    // Parada de emergencia
    public static final String MSG_SESSION_PAUSE   = "SESSION_PAUSE";
    public static final String MSG_SESSION_PAUSED  = "SESSION_PAUSED";
    public static final String MSG_SESSION_RESUME  = "SESSION_RESUME";
    public static final String MSG_SESSION_RESUMED = "SESSION_RESUMED";

    // Broadcasts locales
    public static final String ACTION_SESSION_END    = "com.example.approbot.ACTION_SESSION_END";
    public static final String ACTION_SESSION_PAUSE  = "com.example.approbot.ACTION_SESSION_PAUSE";
    public static final String ACTION_SESSION_RESUME = "com.example.approbot.ACTION_SESSION_RESUME";
    public static final String ACTION_TURN_SIGNAL    = "com.example.approbot.ACTION_TURN_SIGNAL";

    // Seguridad: broadcast para notificar tilt a Activities activas
    public static final String ACTION_TILT_ALERT = "com.example.approbot.ACTION_TILT_ALERT";

    // Broadcasts locales para comandos BT DONE (feedback estandarizado)
    public static final String ACTION_CELEBRATE_DONE = "com.example.approbot.ACTION_CELEBRATE_DONE";
    public static final String ACTION_DENY_DONE      = "com.example.approbot.ACTION_DENY_DONE";
    public static final String ACTION_MOVE_DONE      = "com.example.approbot.ACTION_MOVE_DONE";
    public static final String ACTION_DANCE_DONE     = "com.example.approbot.ACTION_DANCE_DONE";
    public static final String ACTION_BLOCKED        = "com.example.approbot.ACTION_BLOCKED";

    // Persistencia de sesión activa
    public static final String PREF_ACTIVE_SESSION = "active_session";

    // Bluetooth HC-05
    public static final String BT_SPP_UUID      = "00001101-0000-1000-8000-00805F9B34FB";
    public static final String BT_PREFS_KEY_MAC = "hc05_mac";

    // Mensajes Arduino <-> AppRobot (via BT)
    public static final String MSG_BATTERY_STATUS  = "BATTERY_STATUS";
    public static final String MSG_MOVE            = "MOVE";
    public static final String MSG_STOP            = "STOP";
    public static final String MSG_SENSOR_REQUEST  = "SENSOR_REQUEST";
    public static final String MSG_SENSOR_DATA     = "SENSOR_DATA";
    public static final String MSG_ROBOT_STATUS    = "ROBOT_STATUS";
    public static final String MSG_CELEBRATE       = "CELEBRATE";
    public static final String MSG_CELEBRATE_DONE  = "CELEBRATE_DONE";
    public static final String MSG_DENY            = "DENY";
    public static final String MSG_DENY_DONE       = "DENY_DONE";
    public static final String MSG_MOVE_TIMED      = "MOVE_TIMED";
    public static final String MSG_MOVE_DONE       = "MOVE_DONE";
    public static final String MSG_BREATHE_START   = "BREATHE_START";
    public static final String MSG_BREATHE_STOP    = "BREATHE_STOP";
    public static final String MSG_BLOCKED         = "BLOCKED";
    public static final String MSG_DANCE           = "DANCE";
    public static final String MSG_DANCE_DONE      = "DANCE_DONE";

    // Seguridad: detección de vuelco (MPU-6050)
    public static final String MSG_TILT_ALERT      = "TILT_ALERT";
}
