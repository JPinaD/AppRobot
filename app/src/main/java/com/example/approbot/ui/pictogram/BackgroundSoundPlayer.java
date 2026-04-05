package com.example.approbot.ui.pictogram;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Gestiona la reproducción del sonido de fondo durante una actividad.
 * Uso: play() en onCreate, stop() en onDestroy.
 */
public class BackgroundSoundPlayer {

    private static final String TAG = "BackgroundSoundPlayer";

    private MediaPlayer mediaPlayer;

    /**
     * Inicia la reproducción en bucle del recurso raw identificado por resName.
     * Si el recurso no existe, loguea y no hace nada (no crasha).
     */
    public void play(Context context, String resName) {
        if (resName == null || resName.isEmpty()) return;

        int resId = context.getResources().getIdentifier(resName, "raw", context.getPackageName());
        if (resId == 0) {
            Log.w(TAG, "Recurso de sonido no encontrado: " + resName);
            return;
        }

        stop(); // liberar cualquier instancia previa
        mediaPlayer = MediaPlayer.create(context, resId);
        if (mediaPlayer == null) {
            Log.w(TAG, "No se pudo crear MediaPlayer para: " + resName);
            return;
        }
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
    }

    /** Detiene y libera el MediaPlayer. Seguro de llamar aunque no haya sonido activo. */
    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
