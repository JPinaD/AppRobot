package com.example.approbot.mqtt.message;

/** Estado del robot publicado hacia AppTerapeuta. */
public class RobotStatus {
    public String robotId;
    public int batteryLevel;
    public int activityProgress;
    public String currentActivity;
    public boolean isConnectedToHw;
    public long timestamp;
}
