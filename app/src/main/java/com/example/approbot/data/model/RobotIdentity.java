package com.example.approbot.data.model;

public class RobotIdentity {
    public final String robotId;
    public final String name;
    public final int port;

    public RobotIdentity(String robotId, String name, int port) {
        this.robotId = robotId;
        this.name = name;
        this.port = port;
    }
}
