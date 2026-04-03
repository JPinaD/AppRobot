package com.example.approbot.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.approbot.data.model.RobotIdentity;
import com.example.approbot.util.AppConstants;

import java.util.UUID;

public class RobotIdentityRepository {

    private static final String PREFS_NAME = "robot_identity";
    private static final String KEY_ROBOT_ID = "robotId";
    private static final String KEY_NAME = "name";

    private final SharedPreferences prefs;

    public RobotIdentityRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public RobotIdentity getOrCreate(String defaultName) {
        String robotId = prefs.getString(KEY_ROBOT_ID, null);
        if (robotId == null) {
            robotId = UUID.randomUUID().toString();
            prefs.edit()
                    .putString(KEY_ROBOT_ID, robotId)
                    .putString(KEY_NAME, defaultName)
                    .apply();
        }
        String name = prefs.getString(KEY_NAME, defaultName);
        return new RobotIdentity(robotId, name, AppConstants.NSD_DEFAULT_PORT);
    }
}
