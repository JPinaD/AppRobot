package com.example.approbot.mqtt;

import com.example.approbot.mqtt.message.RobotCommand;
import com.example.approbot.mqtt.message.RobotEvent;
import com.example.approbot.mqtt.message.RobotStatus;
import com.example.approbot.mqtt.message.SessionControlMessage;
import com.google.gson.Gson;

/**
 * Punto de entrada MQTT para AppRobot.
 * - Suscribe a comandos del terapeuta y a control de sesión.
 * - Publica estado periódico y eventos puntuales.
 */
public class RobotMqttClient {

    private final MqttManager mqtt;
    private final Gson gson = new Gson();
    private final String robotId;

    public interface CommandListener {
        void onCommandReceived(RobotCommand command);
    }

    public interface SessionListener {
        void onSessionControlReceived(SessionControlMessage message);
    }

    private CommandListener commandListener;
    private SessionListener sessionListener;

    public RobotMqttClient(String robotId) {
        this.robotId = robotId;
        this.mqtt = MqttManager.getInstance();
    }

    public void setCommandListener(CommandListener l)  { this.commandListener = l; }
    public void setSessionListener(SessionListener l)  { this.sessionListener = l; }

    /** Conecta al broker y suscribe a los topics de este robot. */
    public void connect(Runnable onSuccess, java.util.function.Consumer<Throwable> onFailure) {
        mqtt.connect(() -> {
            subscribeToCommands();
            subscribeToSessionControl();
            if (onSuccess != null) onSuccess.run();
        }, onFailure);
    }

    private void subscribeToCommands() {
        mqtt.subscribe(MqttTopics.command(robotId), payload -> {
            RobotCommand cmd = gson.fromJson(payload, RobotCommand.class);
            if (commandListener != null) commandListener.onCommandReceived(cmd);
        });
    }

    private void subscribeToSessionControl() {
        mqtt.subscribe(MqttTopics.SESSION_CONTROL, payload -> {
            SessionControlMessage msg = gson.fromJson(payload, SessionControlMessage.class);
            if (sessionListener != null) sessionListener.onSessionControlReceived(msg);
        });
    }

    /** Publica el estado actual del robot (llamar periódicamente, ej. cada 5s). */
    public void publishStatus(RobotStatus status) {
        status.robotId = robotId;
        status.timestamp = System.currentTimeMillis();
        mqtt.publish(MqttTopics.status(robotId), gson.toJson(status));
    }

    /** Publica un evento puntual (sensor, respuesta del alumno, incidencia). */
    public void publishEvent(RobotEvent event) {
        event.robotId = robotId;
        event.timestamp = System.currentTimeMillis();
        mqtt.publish(MqttTopics.event(robotId), gson.toJson(event));
    }

    public void disconnect() {
        mqtt.disconnect();
    }
}
