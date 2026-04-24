package com.example.approbot.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.approbot.data.local.entity.LocalProfileEntity;
import com.example.approbot.data.repository.LocalProfileRepository;

import java.util.List;

public class ProfileSelectionViewModel extends AndroidViewModel {

    private final LocalProfileRepository repository;
    public final LiveData<List<LocalProfileEntity>> profiles;

    public ProfileSelectionViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalProfileRepository(application);
        profiles = repository.getAll();
    }

    public void insert(LocalProfileEntity profile) {
        repository.insert(profile);
    }

    public void insertDefaultIfEmpty() {
        LocalProfileEntity defaultProfile = new LocalProfileEntity(
                "default", "Alumno", "Perfil por defecto", true, false);
        repository.insert(defaultProfile);
    }

    public void deleteById(String id) {
        repository.deleteById(id);
    }
}
