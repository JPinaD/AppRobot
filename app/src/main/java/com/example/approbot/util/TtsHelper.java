package com.example.approbot.util;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Singleton helper for Text-To-Speech functionality.
 * Initialize once in WaitingSessionActivity.onCreate() and use across activities.
 * Fails silently if TTS is not available.
 */
public class TtsHelper {

    private static final String TAG = "TtsHelper";
    private static TtsHelper instance;

    private TextToSpeech tts;
    private boolean ready = false;

    private TtsHelper() {}

    public static synchronized TtsHelper getInstance() {
        if (instance == null) {
            instance = new TtsHelper();
        }
        return instance;
    }

    /**
     * Initialize TTS engine. Call once from the main activity's onCreate().
     * @param context Application or Activity context.
     */
    public void init(Context context) {
        if (tts != null) return; // Already initialized
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("es", "ES"));
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default locale
                    tts.setLanguage(Locale.getDefault());
                    Log.w(TAG, "Spanish TTS not available, using default locale");
                }
                ready = true;
                Log.i(TAG, "TTS initialized successfully");
            } else {
                Log.w(TAG, "TTS initialization failed with status: " + status);
            }
        });
    }

    /**
     * Speak text aloud. Fails silently if TTS is not ready.
     * @param text The text to speak.
     */
    public void speak(String text) {
        if (!ready || tts == null || text == null || text.isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
    }

    /**
     * Stop any current speech.
     */
    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    /**
     * Release TTS resources. Call from WaitingSessionActivity.onDestroy().
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }

    public boolean isReady() {
        return ready;
    }
}
