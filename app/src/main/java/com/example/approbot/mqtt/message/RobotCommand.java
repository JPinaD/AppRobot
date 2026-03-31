package com.example.approbot.mqtt.message;

/** Comando recibido desde AppTerapeuta. Misma estructura que en AppTerapeuta. */
public class RobotCommand {
    public String type;
    public String activityId;
    public String profileId;
    public String messageText;
    public String actionId;
    public long timestamp;
}
