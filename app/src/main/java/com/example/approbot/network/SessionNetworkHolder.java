package com.example.approbot.network;

import com.example.approbot.bluetooth.BluetoothRobotManager;

/**
 * Holder de sesión de red. Mantiene referencias a TcpServer, BluetoothRobotManager
 * y RobotStatusReporter activos durante la sesión.
 */
public class SessionNetworkHolder {

    private static TcpServer tcpServer;
    private static BluetoothRobotManager bluetoothManager;
    private static RobotStatusReporter statusReporter;

    public static void init(TcpServer server, BluetoothRobotManager btManager) {
        tcpServer = server;
        bluetoothManager = btManager;
    }

    public static void setStatusReporter(RobotStatusReporter reporter) {
        statusReporter = reporter;
    }

    public static TcpServer getTcpServer()                   { return tcpServer; }
    public static BluetoothRobotManager getBluetoothManager() { return bluetoothManager; }
    public static RobotStatusReporter getStatusReporter()    { return statusReporter; }

    public static void clear() {
        tcpServer = null;
        bluetoothManager = null;
        statusReporter = null;
    }
}
