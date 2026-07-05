package com.example.approbot.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.network.ActivityStatusProvider;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel para la actividad de Reconocimiento Emocional.
 *
 * Feedback para actividades con casillas:
 * - Acierto: MOVE_TIMED FORWARD (avanza una casilla).
 * - Último acierto: MOVE_TIMED FORWARD + esperar MOVE_DONE + DANCE (celebración de final).
 * - Fallo: feedback visual suave en pantalla (sin movimiento del robot).
 *   Muestra mensaje de ánimo, resalta opción correcta tras 2s, avanza tras 4s total.
 */
public class EmotionViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "EmotionViewModel";

    /** Duración del feedback visual de fallo antes de resaltar la correcta (ms). */
    private static final long WRONG_HIGHLIGHT_DELAY_MS = 2000;
    /** Duración total del feedback de fallo antes de avanzar (ms). */
    private static final long WRONG_ADVANCE_DELAY_MS = 4000;

    public enum State {
        SHOWING,           // Mostrando opciones, esperando input del alumno
        CORRECT_ADVANCING, // Acierto: avanzando casilla (MOVE_TIMED enviado)
        WRONG_SHOWING,     // Fallo: mostrando mensaje suave, luego resalta correcta
        COMPLETING,        // Último acierto: MOVE_TIMED enviado, esperando MOVE_DONE para enviar DANCE
        DANCING,           // DANCE enviado, esperando DANCE_DONE
        COMPLETED          // Fin: DANCE_DONE recibido, mostrar felicitación
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    /** Emits true when it's time to highlight the correct option (2s into WRONG_SHOWING). */
    private final MutableLiveData<Boolean> highlightCorrect = new MutableLiveData<>(false);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> emotionPool;
    private int totalRounds = 3;
    private int currentRound = 0;
    private String correctEmotionId;
    private String previousEmotionId;

    private final Handler wrongHandler = new Handler(Looper.getMainLooper());

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<String> items, int rounds) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.totalRounds = Math.max(1, Math.min(5, rounds > 0 ? rounds : 3));
        this.emotionPool = items != null && items.size() >= 4 ? items : defaultEmotions();
        pickNextEmotion();
    }

    public LiveData<State> getState() { return state; }
    public LiveData<Integer> getProgress() { return progress; }
    public LiveData<Boolean> getHighlightCorrect() { return highlightCorrect; }
    public String getCorrectEmotionId() { return correctEmotionId; }
    public int getTotalRounds() { return totalRounds; }
    public int getCurrentRound() { return currentRound; }

    public List<String> buildOptions() {
        List<String> distractors = new ArrayList<>(emotionPool);
        distractors.remove(correctEmotionId);
        Collections.shuffle(distractors);

        List<String> options = new ArrayList<>();
        options.add(correctEmotionId);
        for (int i = 0; i < 3 && i < distractors.size(); i++) {
            options.add(distractors.get(i));
        }
        Collections.shuffle(options);
        return options;
    }

    /**
     * Alumno selecciona una opción.
     * - Acierto: envía MOVE_TIMED FORWARD directamente.
     *   Si es la última ronda, pasa a estado COMPLETING (espera MOVE_DONE para enviar DANCE).
     * - Fallo: NO envía DENY. Muestra feedback visual suave durante 4s y avanza.
     */
    public void onOptionSelected(String selectedId) {
        boolean correct = selectedId.equals(correctEmotionId);
        sendResult(correct, selectedId);

        if (correct) {
            currentRound++;
            // Only post progress if not the final round (avoids display showing round N+1/N)
            if (currentRound < totalRounds) {
                progress.postValue(currentRound);
            }

            if (currentRound >= totalRounds) {
                // Última ronda: avanzar casilla y luego DANCE
                state.postValue(State.COMPLETING);
                sendMoveTimed("FORWARD", 800);
            } else {
                // Ronda normal: solo avanzar casilla
                state.postValue(State.CORRECT_ADVANCING);
                sendMoveTimed("FORWARD", 800);
            }
        } else {
            // Fallo: feedback visual suave sin DENY
            highlightCorrect.postValue(false);
            state.postValue(State.WRONG_SHOWING);
            scheduleWrongAdvance();
        }
    }

    /**
     * Llamado cuando se recibe MOVE_DONE del Arduino (tras avance de casilla).
     * Si estábamos en COMPLETING (última ronda): envía DANCE.
     * Si estábamos en CORRECT_ADVANCING (ronda normal): carga siguiente emoción.
     */
    public void onMoveDone() {
        State current = state.getValue();
        if (current == State.CORRECT_ADVANCING) {
            pickNextEmotion();
            state.postValue(State.SHOWING);
        } else if (current == State.COMPLETING) {
            // Casilla final avanzada, ahora celebrar con DANCE
            state.postValue(State.DANCING);
            sendDance();
        }
    }

    /**
     * Llamado cuando se recibe DANCE_DONE del Arduino.
     * Transiciona a COMPLETED para mostrar pantalla de felicitación.
     */
    public void onDanceDone() {
        if (state.getValue() != State.DANCING) return;
        state.postValue(State.COMPLETED);
    }

    /**
     * Fallback: si no llega el DONE (por BT inestable), la Activity puede
     * llamar a este método tras un timeout para forzar la transición.
     */
    public void advanceAfterCorrect() {
        State current = state.getValue();
        if (current == State.CORRECT_ADVANCING || current == State.COMPLETING) {
            onMoveDone();
        } else if (current == State.DANCING) {
            onDanceDone();
        }
    }

    /**
     * Fallback: if the wrong feedback timer somehow doesn't fire,
     * the Activity can force the reset.
     */
    public void resetAfterWrong() {
        wrongHandler.removeCallbacksAndMessages(null);
        highlightCorrect.postValue(false);
        pickNextEmotion();
        state.postValue(State.SHOWING);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        wrongHandler.removeCallbacksAndMessages(null);
    }

    // --- Private ---

    /**
     * Schedules the soft feedback sequence for a wrong answer:
     * 1. After 2s: highlight the correct option.
     * 2. After 4s total: advance to next emotion.
     */
    private void scheduleWrongAdvance() {
        wrongHandler.removeCallbacksAndMessages(null);

        // After 2s: signal to highlight the correct option
        wrongHandler.postDelayed(() -> highlightCorrect.postValue(true), WRONG_HIGHLIGHT_DELAY_MS);

        // After 4s: advance to next emotion
        wrongHandler.postDelayed(() -> {
            highlightCorrect.postValue(false);
            pickNextEmotion();
            state.postValue(State.SHOWING);
        }, WRONG_ADVANCE_DELAY_MS);
    }

    private void pickNextEmotion() {
        List<String> candidates = new ArrayList<>(emotionPool);
        if (previousEmotionId != null) candidates.remove(previousEmotionId);
        Collections.shuffle(candidates);
        correctEmotionId = candidates.get(0);
        previousEmotionId = correctEmotionId;
    }

    private List<String> defaultEmotions() {
        List<String> list = new ArrayList<>();
        list.add("emotion_happy");
        list.add("emotion_sad");
        list.add("emotion_angry");
        list.add("emotion_surprised");
        list.add("emotion_scared");
        list.add("emotion_disgusted");
        list.add("emotion_calm");
        list.add("emotion_shy");
        list.add("emotion_bored");
        list.add("emotion_tired");
        list.add("emotion_excited");
        list.add("emotion_terror");
        return list;
    }

    private void sendResult(boolean correct, String selectedId) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            payload.put("expected", correctEmotionId);
            payload.put("selected", selectedId);
            payload.put("step", currentRound + (correct ? 0 : 1));
            payload.put("totalSteps", totalRounds);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_ACTIVITY_RESULT);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error enviando resultado", e);
        }
    }

    private void sendMoveTimed(String dir, int ms) {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                "{\"dir\":\"" + dir + "\",\"ms\":" + ms + "}"));
    }

    private void sendDance() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_DANCE, null));
    }

    // --- ActivityStatusProvider ---
    @Override public Integer getBatteryPct() { return null; }
    @Override public String getActivityId() { return currentRound < totalRounds ? AppConstants.ACTIVITY_EMOTION : null; }
    @Override public Integer getProgressPct() {
        if (totalRounds == 0) return null;
        return Math.min(100, currentRound * 100 / totalRounds);
    }
}
