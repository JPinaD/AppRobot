package com.example.approbot.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

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
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3000;

    private BluetoothSocket socket;
    private PrintWriter writer;
    private BluetoothRobotListener listener;
    private volatile boolean running = false;
    private volatile boolean shouldReconnect = false;
    private Context connectContext;
    private String connectMac;

    public void setListener(BluetoothRobotListener listener) {
        this.listener = listener;
    }

    /** Abre el socket RFCOMM en un hilo de fondo con reintentos. */
    public void connect(Context context, String macAddress) {
        if (macAddress == null) {
            notifyError("MAC del HC-05 no configurada");
            return;
        }
        this.connectContext = context.getApplicationContext();
        this.connectMac = macAddress;
        this.shouldReconnect = true;
        new Thread(() -> connectWithRetries(), "bt-connect").start();
    }

    private void connectWithRetries() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int result = ContextCompat.checkSelfPermission(
                    connectContext, android.Manifest.permission.BLUETOOTH_CONNECT);
            if (result != PackageManager.PERMISSION_GRANTED) {
                notifyError("Permiso Bluetooth no concedido");
                return;
            }
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            notifyError("Bluetooth no disponible o desactivado");
            return;
        }
        BluetoothDevice device = adapter.getRemoteDevice(connectMac);

        for (int attempt = 1; attempt <= MAX_RETRIES && shouldReconnect; attempt++) {
            try {
                BluetoothSocket s;
                try {
                    s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                } catch (IOException | SecurityException e) {
                    Log.w(TAG, "Socket seguro fallido, intentando inseguro: " + e.getMessage());
                    s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                }
                s.connect();
                socket = s;
                writer = new PrintWriter(s.getOutputStream(), true);
                running = true;
                if (listener != null) listener.onConnected();
                readLoop(s);
                return; // readLoop terminó normalmente (desconexión limpia)
            } catch (IOException e) {
                Log.w(TAG, "Intento " + attempt + "/" + MAX_RETRIES + " fallido: " + e.getMessage());
                if (attempt < MAX_RETRIES && shouldReconnect) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) { return; }
                } else {
                    notifyError("No se pudo conectar tras " + MAX_RETRIES + " intentos: " + e.getMessage());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException al conectar", e);
                notifyError(e.getMessage());
                return;
            }
        }
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
        shouldReconnect = false;
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
