package com.example.approbot.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Mensaje intercambiado entre AppRobot y el robot físico vía Bluetooth.
 * Formato: {"type":"...", "payload":"..."} — payload es opcional.
 */
public class RobotMessage {

    public final String type;
    public final String payload; // nullable

    public RobotMessage(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public static String toJson(RobotMessage msg) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", msg.type);
            if (msg.payload != null) obj.put("payload", msg.payload);
            return obj.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** Devuelve null si el JSON está mal formado o falta el campo type. */
    public static RobotMessage fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.getString("type");
            String payload = obj.optString("payload", null);
            return new RobotMessage(type, payload);
        } catch (JSONException e) {
            return null;
        }
    }
}
