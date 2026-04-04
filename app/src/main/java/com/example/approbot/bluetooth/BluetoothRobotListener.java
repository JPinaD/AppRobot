package com.example.approbot.bluetooth;

import com.example.approbot.data.model.RobotMessage;

public interface BluetoothRobotListener {
    void onConnected();
    void onMessageReceived(RobotMessage message);
    void onConnectionError(String reason);
    void onDisconnected();
}
