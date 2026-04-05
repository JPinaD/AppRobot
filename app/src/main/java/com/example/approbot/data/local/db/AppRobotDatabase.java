package com.example.approbot.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.approbot.data.local.dao.LocalProfileDao;
import com.example.approbot.data.local.entity.LocalProfileEntity;

@Database(
    entities = {LocalProfileEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class AppRobotDatabase extends RoomDatabase {

    private static volatile AppRobotDatabase instance;

    public abstract LocalProfileDao localProfileDao();

    public static AppRobotDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppRobotDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppRobotDatabase.class,
                            "approbot.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
