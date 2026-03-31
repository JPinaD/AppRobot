package com.example.approbot.mqtt.message;

/** Evento puntual publicado por AppRobot hacia AppTerapeuta. */
public class RobotEvent {
    public String robotId;
    public String type;
    public boolean correct;
    public String description;
    public long timestamp;
}
