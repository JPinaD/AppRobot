package com.example.approbot.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.util.AppConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida del socket RFCOMM con el HC-05 del robot físico.
 * Toda operación de red se ejecuta en hilos de fondo; los callbacks se invocan
 * desde esos hilos — la UI debe usar runOnUiThread si necesita actualizar vistas.
 */
public class BluetoothRobotManager {

    private static final String TAG = "BluetoothRobotManager";
    private static final UUID SPP_UUID = UUID.fromString(AppConstants.BT_SPP_UUID);

    private BluetoothSocket socket;
    private PrintWriter writer;
    private BluetoothRobotListener listener;
    private volatile boolean running = false;

    public void setListener(BluetoothRobotListener listener) {
        this.listener = listener;
    }

    /** Abre el socket RFCOMM en un hilo de fondo. */
    public void connect(String macAddress) {
        new Thread(() -> {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                notifyError("Bluetooth no disponible o desactivado");
                return;
            }
            BluetoothDevice device = adapter.getRemoteDevice(macAddress);
            try {
                BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                adapter.cancelDiscovery();
                s.connect();
                socket = s;
                writer = new PrintWriter(s.getOutputStream(), true);
                running = true;
                if (listener != null) listener.onConnected();
                readLoop(s);
            } catch (IOException e) {
                Log.e(TAG, "Error al conectar con HC-05", e);
                notifyError(e.getMessage());
            } catch (SecurityException e) {
                Log.e(TAG, "Permiso Bluetooth denegado", e);
                notifyError("Permiso Bluetooth no concedido");
            }
        }, "bt-connect").start();
    }

    /** Serializa y envía un mensaje al robot. Seguro llamar desde cualquier hilo. */
    public void send(RobotMessage message) {
        if (writer == null) {
            Log.w(TAG, "send() llamado sin conexión activa");
            return;
        }
        String json = RobotMessage.toJson(message);
        if (json != null) writer.println(json);
    }

    /** Cierra el socket limpiamente. */
    public void disconnect() {
        running = false;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
        writer = null;
    }

    // --- privado ---

    private void readLoop(BluetoothSocket s) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                RobotMessage msg = RobotMessage.fromJson(line);
                if (msg == null) {
                    Log.w(TAG, "Mensaje mal formado ignorado: " + line);
                    continue;
                }
                if (listener != null) listener.onMessageReceived(msg);
            }
        } catch (IOException e) {
            if (running) {
                Log.e(TAG, "Conexión perdida durante lectura", e);
                notifyError(e.getMessage());
            }
        } finally {
            running = false;
            if (listener != null) listener.onDisconnected();
        }
    }

    private void notifyError(String reason) {
        if (listener != null) listener.onConnectionError(reason);
    }
}
