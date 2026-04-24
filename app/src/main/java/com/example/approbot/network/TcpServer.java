package com.example.approbot.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor TCP que escucha conexiones entrantes de AppTerapeuta.
 * Expone sendToClient() para enviar mensajes proactivos al terapeuta conectado.
 */
public class TcpServer {

    public interface MessageListener {
        void onMessage(String message, PrintWriter out);
    }

    private static final String TAG = "TcpServer";

    private final int port;
    private volatile MessageListener listener;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private volatile PrintWriter clientWriter;
    private volatile Socket currentClient;
    private volatile boolean running = false;

    public TcpServer(int port, MessageListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    public void start() {
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                Log.d(TAG, "Servidor TCP escuchando en puerto " + port);
                while (running) {
                    Socket client = serverSocket.accept();
                    // Cerrar conexión anterior si existe
                    if (currentClient != null) {
                        try { currentClient.close(); } catch (IOException ignored) {}
                    }
                    currentClient = client;
                    executor.execute(() -> handleClient(client));
                }
            } catch (IOException e) {
                if (running) Log.e(TAG, "Error en servidor TCP", e);
            }
        });
    }

    public void stop() {
        running = false;
        clientWriter = null;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Log.w(TAG, "Error cerrando servidor TCP", e);
        }
        executor.shutdownNow();
    }

    /** Envía un mensaje al terapeuta conectado. No hace nada si no hay cliente. */
    public void sendToClient(String message) {
        PrintWriter writer = clientWriter;
        if (writer != null) {
            executor.execute(() -> writer.println(message));
        } else {
            Log.w(TAG, "sendToClient(): no hay cliente conectado");
        }
    }

    private void handleClient(Socket client) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            clientWriter = out;
            String line;
            while ((line = in.readLine()) != null) {
                Log.d(TAG, "Recibido: " + line);
                listener.onMessage(line, out);
            }
        } catch (IOException e) {
            Log.w(TAG, "Cliente desconectado: " + e.getMessage());
        } finally {
            clientWriter = null;
            try { client.close(); } catch (IOException ignored) {}
        }
    }
}
