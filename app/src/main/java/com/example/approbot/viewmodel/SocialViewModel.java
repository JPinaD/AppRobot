package com.example.approbot.viewmodel;

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
 * Feedback estandarizado TEA:
 * - Acierto: CELEBRATE → esperar CELEBRATE_DONE → MOVE_TIMED FORWARD (avance casilla)
 * - Fallo: DENY (oscilación servo, sin movimiento) + muestra respuesta correcta
 * - Completitud: DANCE → esperar DANCE_DONE → pantalla completada
 */
public class SocialViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "SocialViewModel";
    private static final int TOTAL_SQUARES = 5;

    public enum State { SHOWING, CORRECT, CORRECT_ADVANCING, WRONG, COMPLETING, COMPLETED }

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
     * - Acierto: envía CELEBRATE, transiciona a CORRECT
     * - Fallo: envía DENY, transiciona a WRONG
     */
    public void onOptionSelected(String option) {
        if (currentScenario == null) return;

        boolean correct = option.equals(currentScenario.correctOption);
        String selectedOutcome = "A".equals(option) ? currentScenario.outcomeA : currentScenario.outcomeB;

        sendResult(correct, option, currentScenario.id);

        if (correct) {
            currentSquare++;
            // Enviar CELEBRATE (feedback estandarizado: acierto = CELEBRATE)
            sendCelebrate();
            uiState.postValue(new UiState(State.CORRECT, currentScenario,
                    selectedOutcome, null, currentSquare));
        } else {
            // Enviar DENY (feedback estandarizado: fallo = DENY)
            sendDeny();
            String correctOptionText = "A".equals(currentScenario.correctOption)
                    ? currentScenario.optionA : currentScenario.optionB;
            uiState.postValue(new UiState(State.WRONG, currentScenario,
                    selectedOutcome, correctOptionText, currentSquare));
        }
    }

    /**
     * Llamado cuando se recibe CELEBRATE_DONE del Arduino.
     * Si no es la última casilla: envía MOVE_TIMED FORWARD.
     * Si es la última: envía DANCE.
     */
    public void onCelebrateDone() {
        UiState current = uiState.getValue();
        if (current == null || current.state != State.CORRECT) return;

        if (currentSquare >= TOTAL_SQUARES) {
            // Completitud: enviar DANCE
            sendDance();
            uiState.postValue(new UiState(State.COMPLETING, current.scenario,
                    current.feedbackText, null, currentSquare));
        } else {
            // Avanzar casilla
            sendMoveTimed("FORWARD", 600);
            uiState.postValue(new UiState(State.CORRECT_ADVANCING, current.scenario,
                    current.feedbackText, null, currentSquare));
        }
    }

    /**
     * Llamado cuando se recibe MOVE_DONE del Arduino (tras avance de casilla).
     * Carga el siguiente escenario.
     */
    public void onMoveDone() {
        UiState current = uiState.getValue();
        if (current == null || current.state != State.CORRECT_ADVANCING) return;
        pickNextScenario();
    }

    /**
     * Llamado cuando se recibe DANCE_DONE del Arduino.
     * Transiciona a COMPLETED.
     */
    public void onDanceDone() {
        UiState current = uiState.getValue();
        if (current == null || current.state != State.COMPLETING) return;
        uiState.postValue(new UiState(State.COMPLETED, current.scenario,
                null, null, currentSquare));
    }

    /**
     * Llamado cuando se recibe DENY_DONE del Arduino.
     * Carga un nuevo escenario sin avanzar casilla.
     */
    public void onDenyDone() {
        UiState current = uiState.getValue();
        if (current == null || current.state != State.WRONG) return;
        pickNextScenario();
    }

    /**
     * Fallback: si no llega CELEBRATE_DONE a tiempo (BT inestable),
     * la Activity llama este método para forzar avance.
     */
    public void advanceToNext() {
        UiState current = uiState.getValue();
        if (current == null) return;
        if (current.state == State.CORRECT) {
            onCelebrateDone();
        } else if (current.state == State.CORRECT_ADVANCING) {
            onMoveDone();
        }
    }

    /**
     * Fallback: si no llega DENY_DONE, forzar retry.
     */
    public void retryAfterWrong() {
        pickNextScenario();
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

    private void sendCelebrate() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_CELEBRATE, null));
    }

    private void sendMoveTimed(String dir, int ms) {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                "{\"dir\":\"" + dir + "\",\"ms\":" + ms + "}"));
    }

    private void sendDeny() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_DENY, null));
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
