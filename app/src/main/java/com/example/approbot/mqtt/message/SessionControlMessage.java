package com.example.approbot.mqtt.message;

/** Mensaje de control de sesión global recibido desde AppTerapeuta. */
public class SessionControlMessage {
    public String type;
    public String sessionId;
    public long timestamp;
}
