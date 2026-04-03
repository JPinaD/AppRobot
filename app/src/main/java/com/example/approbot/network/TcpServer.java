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
 * Responde PONG a cualquier mensaje PING recibido.
 */
public class TcpServer {

    public interface MessageListener {
        void onMessage(String message, PrintWriter out);
    }

    private static final String TAG = "TcpServer";

    private final int port;
    private final MessageListener listener;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public TcpServer(int port, MessageListener listener) {
        this.port = port;
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
                    executor.execute(() -> handleClient(client));
                }
            } catch (IOException e) {
                if (running) Log.e(TAG, "Error en servidor TCP", e);
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Log.w(TAG, "Error cerrando servidor TCP", e);
        }
        executor.shutdownNow();
    }

    private void handleClient(Socket client) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                Log.d(TAG, "Recibido: " + line);
                listener.onMessage(line, out);
            }
        } catch (IOException e) {
            Log.w(TAG, "Cliente desconectado: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }
}
