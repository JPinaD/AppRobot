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

public class SocialViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "SocialViewModel";
    private static final int TOTAL_SQUARES = 5;

    public enum State { SHOWING, CORRECT, WRONG, COMPLETED }

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

    public void onOptionSelected(String option) {
        if (currentScenario == null) return;

        boolean correct = option.equals(currentScenario.correctOption);
        String selectedOutcome = "A".equals(option) ? currentScenario.outcomeA : currentScenario.outcomeB;

        sendResult(correct, option, currentScenario.id);

        if (correct) {
            currentSquare++;
            // Robot avanza una casilla (el avance ES el feedback positivo)
            sendMoveTimed("FORWARD", 600);

            if (currentSquare >= TOTAL_SQUARES) {
                uiState.postValue(new UiState(State.CORRECT, currentScenario,
                        selectedOutcome, null, currentSquare));
            } else {
                uiState.postValue(new UiState(State.CORRECT, currentScenario,
                        selectedOutcome, null, currentSquare));
            }
        } else {
            // Robot niega con servo (no se mueve)
            sendDeny();
            String correctOptionText = "A".equals(currentScenario.correctOption)
                    ? currentScenario.optionA : currentScenario.optionB;
            uiState.postValue(new UiState(State.WRONG, currentScenario,
                    selectedOutcome, correctOptionText, currentSquare));
        }
    }

    /** Llamado por la Activity tras el delay post-acierto para cargar el siguiente escenario. */
    public void advanceToNext() {
        if (currentSquare >= TOTAL_SQUARES) {
            sendDance();
            uiState.postValue(new UiState(State.COMPLETED, currentScenario, null, null, currentSquare));
            return;
        }
        pickNextScenario();
    }

    /** Llamado por la Activity tras el delay post-fallo para cargar nuevo escenario sin avanzar. */
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

    private void sendMoveTimed(String dir, int ms) {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                "{\"dir\":\"" + dir + "\",\"ms\":" + ms + "}"));
    }

    private void sendServoConfirm() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_SERVO_COMMAND, "CONFIRM"));
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
