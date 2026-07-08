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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel para Turnos Sociales cooperativos (modo MULTI exclusivamente).
 *
 * Siempre coordinado por AppTerapeuta vía TURN_SIGNAL. Requiere 2+ robots.
 * - AppTerapeuta envía TURN_SIGNAL {active:true, roundData:{...}} para cada ronda.
 * - Robot muestra opciones, alumno elige, robot envía TURN_DONE {selectedOption}.
 * - Robot espera TURN_SIGNAL {active:false, result:"correct"/"wrong"} para mostrar feedback.
 *
 * Feedback:
 * - Acierto conjunto: MOVE_TIMED FORWARD 800ms (avanza casilla).
 * - Fallo: sin movimiento. Mensaje de ánimo + resaltado de correcta.
 * - Última ronda con éxito: MOVE_TIMED FORWARD + DANCE.
 */
public class TurnsViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "TurnsViewModel";

    /** Duración feedback visual de fallo antes de mostrar correcta (ms). */
    private static final long WRONG_HIGHLIGHT_DELAY_MS = 2000;
    /** Duración total feedback fallo antes de avanzar (ms). */
    private static final long WRONG_ADVANCE_DELAY_MS = 4000;

    public enum State {
        WAITING_ROUND,       // Esperando que AppTerapeuta envíe la ronda
        SHOWING,             // Mostrando opciones al alumno
        WAITING_PARTNER,     // Alumno respondió, esperando al compañero
        CORRECT_ADVANCING,   // Acierto: avanzando casilla (MOVE_TIMED enviado)
        WRONG_SHOWING,       // Fallo: mostrando feedback suave
        COMPLETING,          // Última ronda: MOVE_TIMED enviado, esperando MOVE_DONE para DANCE
        DANCING,             // DANCE enviado, esperando DANCE_DONE
        COMPLETED            // Actividad completada
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.WAITING_ROUND);
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    /** Emits true when correct option should be highlighted (2s into WRONG_SHOWING). */
    private final MutableLiveData<Boolean> highlightCorrect = new MutableLiveData<>(false);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    // Emotion data
    private int totalRounds = 5;
    private int currentRound = 0;
    private int correctCount = 0;
    private String correctEmotionId;
    private List<String> currentOptions;

    // Multi-mode state
    private boolean roundActive = false; // true when showing options, prevents double-tap

    private final Handler handler = new Handler(Looper.getMainLooper());

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, int steps) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.totalRounds = Math.max(1, Math.min(10, steps > 0 ? steps : 5));
    }

    public LiveData<State> getState() { return state; }
    public LiveData<Integer> getProgress() { return progress; }
    public LiveData<Boolean> getHighlightCorrect() { return highlightCorrect; }
    public String getCorrectEmotionId() { return correctEmotionId; }
    public List<String> getCurrentOptions() { return currentOptions; }
    public int getTotalRounds() { return totalRounds; }
    public int getCurrentRound() { return currentRound; }

    // =========================================================================
    // TURN_SIGNAL handling (coordinated by AppTerapeuta)
    // =========================================================================

    /**
     * Called when TURN_SIGNAL is received from AppTerapeuta.
     * Two types:
     * 1. active=true, roundData={...}: new round, show options
     * 2. active=false, result="correct"/"wrong": round result
     */
    public void onTurnSignalReceived(JSONObject payload) {
        if (payload == null) return;

        boolean active = payload.optBoolean("active", false);

        if (active) {
            handleNewRound(payload);
        } else {
            handleRoundResult(payload);
        }
    }

    /**
     * Alumno selecciona una opción.
     * Sends TURN_DONE to terapeuta and shows "waiting for partner" state.
     */
    public void onOptionSelected(String selectedId) {
        if (!roundActive) return;
        roundActive = false; // prevent double-tap

        sendTurnDone(selectedId);
        state.postValue(State.WAITING_PARTNER);
    }

    // =========================================================================
    // Movement callbacks (BT DONE messages)
    // =========================================================================

    /** Called when MOVE_DONE is received from Arduino. */
    public void onMoveDone() {
        State current = state.getValue();
        if (current == State.CORRECT_ADVANCING) {
            // Wait for next TURN_SIGNAL from terapeuta
            state.postValue(State.WAITING_ROUND);
        } else if (current == State.COMPLETING) {
            state.postValue(State.DANCING);
            sendDance();
        }
    }

    /** Called when DANCE_DONE is received from Arduino. */
    public void onDanceDone() {
        if (state.getValue() != State.DANCING) return;
        state.postValue(State.COMPLETED);
    }

    /** Fallback for missing BT DONE messages. */
    public void advanceAfterCorrect() {
        State current = state.getValue();
        if (current == State.CORRECT_ADVANCING || current == State.COMPLETING) {
            onMoveDone();
        } else if (current == State.DANCING) {
            onDanceDone();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacksAndMessages(null);
    }

    // =========================================================================
    // PRIVATE — Round handlers
    // =========================================================================

    private void handleNewRound(JSONObject payload) {
        // Cancel any pending wrong-advance callbacks from a previous round
        handler.removeCallbacksAndMessages(null);
        highlightCorrect.postValue(false);

        JSONObject roundData = payload.optJSONObject("roundData");
        if (roundData == null) return;

        String emotionId = roundData.optString("emotionId", null);
        JSONArray optionsArr = roundData.optJSONArray("options");
        int round = roundData.optInt("round", 1);
        int total = roundData.optInt("totalRounds", totalRounds);

        if (emotionId == null || optionsArr == null) return;

        this.correctEmotionId = emotionId;
        this.totalRounds = total;
        this.currentRound = round - 1; // 0-indexed internally

        List<String> options = new ArrayList<>();
        for (int i = 0; i < optionsArr.length(); i++) {
            options.add(optionsArr.optString(i));
        }
        this.currentOptions = options;
        this.roundActive = true;

        state.postValue(State.SHOWING);
    }

    private void handleRoundResult(JSONObject payload) {
        String result = payload.optString("result", "wrong");
        boolean lastRound = payload.optBoolean("lastRound", false);
        String correctOption = payload.optString("correctOption", correctEmotionId);
        this.correctEmotionId = correctOption; // ensure we have the correct answer for highlighting

        if ("correct".equals(result)) {
            correctCount++;
            progress.postValue(correctCount);
            if (lastRound) {
                state.postValue(State.COMPLETING);
                sendMoveTimed("FORWARD", 800);
            } else {
                state.postValue(State.CORRECT_ADVANCING);
                sendMoveTimed("FORWARD", 800);
            }
        } else {
            // Wrong: show feedback, highlight correct
            highlightCorrect.postValue(false);
            state.postValue(State.WRONG_SHOWING);
            scheduleWrongAdvance();
        }
    }

    private void scheduleWrongAdvance() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> highlightCorrect.postValue(true), WRONG_HIGHLIGHT_DELAY_MS);
        handler.postDelayed(() -> {
            highlightCorrect.postValue(false);
            // Wait for next TURN_SIGNAL from terapeuta
            state.postValue(State.WAITING_ROUND);
        }, WRONG_ADVANCE_DELAY_MS);
    }

    // =========================================================================
    // PRIVATE — Network helpers
    // =========================================================================

    private void sendTurnDone(String selectedOption) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("selectedOption", selectedOption);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_TURN_DONE);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error enviando TURN_DONE", e);
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
    @Override public String getActivityId() { return AppConstants.ACTIVITY_TURNS; }
    @Override public Integer getProgressPct() {
        if (totalRounds == 0) return null;
        return Math.min(100, correctCount * 100 / totalRounds);
    }
}
