package com.example.approbot.util;

public class AppConstants {
    public static final String NSD_SERVICE_TYPE = "_approbot._tcp.";
    public static final int NSD_DEFAULT_PORT = 9000;
    public static final String NSD_ATTR_ROBOT_ID = "robotId";
    public static final String MSG_PING = "PING";
    public static final String MSG_PONG = "PONG";

    // Actividad pictograma v1
    public static final String MSG_ACTIVITY_START     = "ACTIVITY_START";
    public static final String MSG_PICTOGRAM_SELECTED = "PICTOGRAM_SELECTED";
    public static final String MSG_ROBOT_FEEDBACK     = "ROBOT_FEEDBACK";
    public static final String MSG_SERVO_CONFIRM      = "CONFIRM";

    // Bluetooth HC-05
    public static final String BT_SPP_UUID    = "00001101-0000-1000-8000-00805F9B34FB";
    public static final String BT_PREFS_KEY_MAC = "hc05_mac";
}
