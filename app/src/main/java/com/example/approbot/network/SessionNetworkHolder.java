package com.example.approbot.network;

import com.example.approbot.bluetooth.BluetoothRobotManager;

/**
 * Holder de sesión de red. Mantiene referencias a TcpServer y BluetoothRobotManager
 * activos durante la sesión, para que PictogramActivity pueda acceder a ellos.
 * Se inicializa en WaitingSessionActivity y se limpia al salir.
 */
public class SessionNetworkHolder {

    private static TcpServer tcpServer;
    private static BluetoothRobotManager bluetoothManager;

    public static void init(TcpServer server, BluetoothRobotManager btManager) {
        tcpServer = server;
        bluetoothManager = btManager;
    }

    public static TcpServer getTcpServer() { return tcpServer; }
    public static BluetoothRobotManager getBluetoothManager() { return bluetoothManager; }

    public static void clear() {
        tcpServer = null;
        bluetoothManager = null;
    }
}
