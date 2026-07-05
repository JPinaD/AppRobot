package com.example.approbot.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.SessionConfig.SocialScenarioContent;
import com.example.approbot.network.ActivityStatusProvider;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel para la actividad de Escenarios Sociales.
 *
 * Feedback para actividades con casillas:
 * - Acierto: MOVE_TIMED FORWARD (avanza casilla).
 * - Último acierto: MOVE_TIMED FORWARD + esperar MOVE_DONE + DANCE (celebración de final).
 * - Fallo: feedback visual suave en pantalla (sin movimiento del robot).
 *   Muestra mensaje de ánimo + respuesta correcta, avanza tras 4s.
 */
public class SocialViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "SocialViewModel";
    private static final int TOTAL_SQUARES = 5;

    /** Duración total del feedback de fallo antes de avanzar al siguiente escenario (ms). */
    private static final long WRONG_ADVANCE_DELAY_MS = 4000;

    public enum State { SHOWING, CORRECT_ADVANCING, WRONG_SHOWING, COMPLETING, DANCING, COMPLETED }

    public static class UiState {
        public final State state;
        public final SocialScenarioContent scenario;
        public final String feedbackText;  // outcome de la opción seleccionada
        public final String correctText;   // solo se muestra en fallo: cuál era la correcta
        public final int currentSquare;    // 0..4 (casilla actual, avanza solo en acierto)

        UiState(State state, SocialScenarioContent scenario, String feedbackText,
                String correctText, int currentSquare) {
            this.state = state;
            this.scenario = scenario;
            this.feedbackText = feedbackText;
            this.correctText = correctText;
            this.currentSquare = currentSquare;
        }
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<SocialScenarioContent> scenarioPool;
    private List<SocialScenarioContent> usedScenarios = new ArrayList<>();
    private SocialScenarioContent currentScenario;
    private int currentSquare = 0; // número de aciertos (casillas avanzadas)

    private final Handler wrongHandler = new Handler(Looper.getMainLooper());

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<SocialScenarioContent> scenarios) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.scenarioPool = new ArrayList<>(scenarios);
        Collections.shuffle(this.scenarioPool);
        pickNextScenario();
    }

    public LiveData<UiState> getUiState() { return uiState; }
    public int getTotalSquares() { return TOTAL_SQUARES; }
    public int getCurrentSquare() { return currentSquare; }

    /**
     * Alumno selecciona una opción.
     * - Acierto: envía MOVE_TIMED FORWARD directamente.
     *   Si es la última casilla, pasa a COMPLETING (espera MOVE_DONE para DANCE).
     * - Fallo: NO envía DENY. Muestra feedback visual suave durante 4s y avanza.
     */
    public void onOptionSelected(String option) {
        if (currentScenario == null) return;

        boolean correct = option.equals(currentScenario.correctOption);
        String selectedOutcome = "A".equals(option) ? currentScenario.outcomeA : currentScenario.outcomeB;

        sendResult(correct, option, currentScenario.id);

        if (correct) {
            currentSquare++;

            if (currentSquare >= TOTAL_SQUARES) {
                // Última casilla: avanzar y luego DANCE
                sendMoveTimed("FORWARD", 800);
                uiState.postValue(new UiState(State.COMPLETING, currentScenario,
                        selectedOutcome, null, currentSquare));
            } else {
                // Casilla normal: solo avanzar
                sendMoveTimed("FORWARD", 800);
                uiState.postValue(new UiState(State.CORRECT_ADVANCING, currentScenario,
                        selectedOutcome, null, currentSquare));
            }
        } else {
            // Fallo: feedback visual suave sin DENY
            String correctOptionText = "A".equals(currentScenario.correctOption)
                    ? currentScenario.optionA : currentScenario.optionB;
            uiState.postValue(new UiState(State.WRONG_SHOWING, currentScenario,
                    selectedOutcome, correctOptionText, currentSquare));
            scheduleWrongAdvance();
        }
    }

    /**
     * Llamado cuando se recibe MOVE_DONE del Arduino (tras avance de casilla).
     * Si estábamos en COMPLETING (última casilla): envía DANCE.
     * Si estábamos en CORRECT_ADVANCING (casilla normal): carga siguiente escenario.
     */
    public void onMoveDone() {
        UiState current = uiState.getValue();
        if (current == null) return;

        if (current.state == State.CORRECT_ADVANCING) {
            pickNextScenario();
        } else if (current.state == State.COMPLETING) {
            // Casilla final avanzada, ahora celebrar con DANCE
            sendDance();
            uiState.postValue(new UiState(State.DANCING, current.scenario,
                    current.feedbackText, null, currentSquare));
        }
    }

    /**
     * Llamado cuando se recibe DANCE_DONE del Arduino.
     * Transiciona a COMPLETED.
     */
    public void onDanceDone() {
        UiState current = uiState.getValue();
        if (current == null || current.state != State.DANCING) return;
        uiState.postValue(new UiState(State.COMPLETED, current.scenario,
                null, null, currentSquare));
    }

    /**
     * Fallback: si no llega MOVE_DONE/DANCE_DONE a tiempo (BT inestable),
     * la Activity llama este método para forzar avance.
     */
    public void advanceToNext() {
        UiState current = uiState.getValue();
        if (current == null) return;
        if (current.state == State.CORRECT_ADVANCING || current.state == State.COMPLETING) {
            onMoveDone();
        } else if (current.state == State.DANCING) {
            onDanceDone();
        }
    }

    /**
     * Fallback: if the wrong feedback timer somehow doesn't fire,
     * the Activity can force a retry.
     */
    public void retryAfterWrong() {
        wrongHandler.removeCallbacksAndMessages(null);
        pickNextScenario();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        wrongHandler.removeCallbacksAndMessages(null);
    }

    // --- Private ---

    /**
     * Schedules auto-advance after a wrong answer.
     * After 4s of visual feedback, loads the next scenario.
     */
    private void scheduleWrongAdvance() {
        wrongHandler.removeCallbacksAndMessages(null);
        wrongHandler.postDelayed(this::pickNextScenario, WRONG_ADVANCE_DELAY_MS);
    }

    private void pickNextScenario() {
        // Si se han agotado los escenarios, reciclar con shuffle
        if (scenarioPool.isEmpty()) {
            scenarioPool.addAll(usedScenarios);
            usedScenarios.clear();
            Collections.shuffle(scenarioPool);
        }
        currentScenario = scenarioPool.remove(0);
        usedScenarios.add(currentScenario);
        uiState.postValue(new UiState(State.SHOWING, currentScenario, null, null, currentSquare));
    }

    private void sendResult(boolean correct, String option, String scenarioId) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("scenarioId", scenarioId);
            payload.put("selectedOption", option);
            payload.put("correct", correct);
            payload.put("square", currentSquare);
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
    @Override public String getActivityId() { return AppConstants.ACTIVITY_SOCIAL; }
    @Override public Integer getProgressPct() {
        return Math.min(100, currentSquare * 100 / TOTAL_SQUARES);
    }
}
