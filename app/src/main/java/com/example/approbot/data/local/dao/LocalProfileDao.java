package com.example.approbot.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.approbot.data.local.entity.LocalProfileEntity;

import java.util.List;

@Dao
public interface LocalProfileDao {

    @Query("SELECT * FROM local_profiles ORDER BY name ASC")
    LiveData<List<LocalProfileEntity>> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LocalProfileEntity profile);

    @Query("DELETE FROM local_profiles WHERE id = :id")
    void deleteById(String id);
}
