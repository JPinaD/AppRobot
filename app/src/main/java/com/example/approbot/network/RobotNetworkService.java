package com.example.approbot.network;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.repository.RobotIdentityRepository;

/**
 * Servicio en segundo plano que mantiene TcpServer y NsdAdvertiser activos
 * independientemente del ciclo de vida de WaitingSessionActivity.
 */
public class RobotNetworkService extends Service {

    private static final String TAG = "RobotNetworkService";

    public class LocalBinder extends Binder {
        public RobotNetworkService getService() { return RobotNetworkService.this; }
    }

    private final IBinder binder = new LocalBinder();

    private TcpServer tcpServer;
    private NsdAdvertiser nsdAdvertiser;
    private BluetoothRobotManager bluetoothRobotManager;
    private TcpServer.MessageListener messageListener;

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /** Inicializa y arranca los componentes de red. Llamar desde WaitingSessionActivity. */
    public void startNetwork(String robotName, int port,
                             TcpServer.MessageListener listener,
                             BluetoothRobotManager btManager) {
        this.bluetoothRobotManager = btManager;
        Log.d(TAG, "startNetwork() tcpServer=" + (tcpServer != null ? "exists" : "null"));

        if (tcpServer == null) {
            tcpServer = new TcpServer(port, listener);
            tcpServer.start();
            Log.d(TAG, "TcpServer iniciado en puerto " + port);
        } else {
            // Actualizar el listener con la instancia actual de la Activity
            tcpServer.setMessageListener(listener);
            Log.d(TAG, "TcpServer listener actualizado");
        }

        if (nsdAdvertiser == null) {
            nsdAdvertiser = new NsdAdvertiser(this);
            nsdAdvertiser.start(robotName, port);
            Log.d(TAG, "NsdAdvertiser iniciado como " + robotName);
        }

        SessionNetworkHolder.init(tcpServer, btManager);
    }

    /** Para los componentes de red. Llamar al salir definitivamente. */
    public void stopNetwork() {
        if (nsdAdvertiser != null) { nsdAdvertiser.stop(); nsdAdvertiser = null; }
        if (tcpServer != null)     { tcpServer.stop();     tcpServer = null; }
        SessionNetworkHolder.clear();
    }

    public TcpServer getTcpServer() { return tcpServer; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNetwork();
    }
}
