package com.example.approbot.mqtt;

/**
 * Configuración MQTT para AppRobot.
 * El robotId se asigna en tiempo de ejecución (puede venir de SharedPreferences o de la config del dispositivo).
 */
public class MqttConfig {

    public static final String BROKER_IP   = "10.38.229.66";
    public static final int    BROKER_PORT = 1883;
    public static final String CLIENT_ID_PREFIX = "robot-";

    public static final int QOS = 1;

    private MqttConfig() {}
}
