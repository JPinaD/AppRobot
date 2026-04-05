package com.example.approbot.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "local_profiles")
public class LocalProfileEntity {

    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public String description;
    public boolean usePictograms;
    public boolean useAudio;

    public LocalProfileEntity(@NonNull String id, String name, String description,
                               boolean usePictograms, boolean useAudio) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.usePictograms = usePictograms;
        this.useAudio = useAudio;
    }
}
