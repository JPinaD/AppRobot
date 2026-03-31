package com.example.approbot.mqtt;

/**
 * Mismos topics que AppTerapeuta. Copiado aquí para que AppRobot sea autónoma.
 * Si en el futuro se extrae a un módulo compartido, se puede eliminar esta clase.
 */
public class MqttTopics {

    public static final String BASE = "robots/";
    public static final String SESSION_CONTROL = "session/control";

    public static String command(String robotId) {
        return BASE + robotId + "/command";
    }

    public static String status(String robotId) {
        return BASE + robotId + "/status";
    }

    public static String event(String robotId) {
        return BASE + robotId + "/event";
    }

    private MqttTopics() {}
}
