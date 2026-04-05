package com.example.approbot.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.approbot.data.local.db.AppRobotDatabase;
import com.example.approbot.data.local.entity.LocalProfileEntity;

import java.util.List;
import java.util.concurrent.Executors;

public class LocalProfileRepository {

    private final AppRobotDatabase db;

    public LocalProfileRepository(Context context) {
        db = AppRobotDatabase.getInstance(context);
    }

    public LiveData<List<LocalProfileEntity>> getAll() {
        return db.localProfileDao().getAll();
    }

    public void insert(LocalProfileEntity profile) {
        Executors.newSingleThreadExecutor().execute(() -> db.localProfileDao().insert(profile));
    }

    public void deleteById(String id) {
        Executors.newSingleThreadExecutor().execute(() -> db.localProfileDao().deleteById(id));
    }
}
