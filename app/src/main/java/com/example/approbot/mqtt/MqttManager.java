package com.example.approbot.mqtt;

import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gestiona la conexión MQTT de AppRobot con el broker local.
 * Singleton: una sola conexión por instancia de la app.
 */
public class MqttManager {

    private static final String TAG = "MqttManager[Robot]";
    private static MqttManager instance;

    private Mqtt3AsyncClient client;

    private MqttManager() {
        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(MqttConfig.CLIENT_ID_PREFIX + UUID.randomUUID())
                .serverHost(MqttConfig.BROKER_IP)
                .serverPort(MqttConfig.BROKER_PORT)
                .buildAsync();
    }

    public static synchronized MqttManager getInstance() {
        if (instance == null) instance = new MqttManager();
        return instance;
    }

    public void connect(Runnable onSuccess, Consumer<Throwable> onFailure) {
        client.connect()
                .whenComplete((ack, ex) -> {
                    if (ex != null) {
                        Log.e(TAG, "Error al conectar con broker MQTT", ex);
                        if (onFailure != null) onFailure.accept(ex);
                    } else {
                        Log.d(TAG, "Conectado al broker MQTT");
                        if (onSuccess != null) onSuccess.run();
                    }
                });
    }

    public void disconnect() {
        client.disconnect();
    }

    public void publish(String topic, String payload) {
        client.publishWith()
                .topic(topic)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .send()
                .whenComplete((pub, ex) -> {
                    if (ex != null) Log.e(TAG, "Error publicando en " + topic, ex);
                });
    }

    public void subscribe(String topic, Consumer<String> onMessage) {
        client.subscribeWith()
                .topicFilter(topic)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    onMessage.accept(payload);
                })
                .send()
                .whenComplete((sub, ex) -> {
                    if (ex != null) Log.e(TAG, "Error suscribiendo a " + topic, ex);
                    else Log.d(TAG, "Suscrito a " + topic);
                });
    }

    public boolean isConnected() {
        return client.getState().isConnected();
    }
}
