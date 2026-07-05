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
import java.util.List;
import java.util.Random;

/**
 * ViewModel para la actividad de Secuencias Visuales.
 *
 * Flujo correcto:
 * - SHOWING: se genera secuencia aleatoria de 3 direcciones, se muestra en pantalla
 *   Y el robot la ejecuta físicamente (MOVE_TIMED por cada dirección, esperando
 *   MOVE_DONE entre cada una). Se transiciona a INPUT cuando TODOS los movimientos
 *   físicos han terminado.
 * - INPUT: se muestran 4 botones (FORWARD, BACKWARD, LEFT, RIGHT). El alumno
 *   pulsa en el orden correcto (3 pulsaciones).
 * - Acierto: se muestra mensaje positivo durante 4s. NO se ejecuta la secuencia
 *   físicamente tras acertar (la ejecución física es solo durante SHOWING/demo).
 *   Luego avanza a la siguiente ronda.
 * - Fallo: feedback visual suave (sin movimiento), espera 4s, muestra la MISMA secuencia.
 * - Última ronda completada: envía DANCE (celebración final).
 *
 * El servo NO se usa. CELEBRATE no se usa (no tiene sentido en este flujo).
 */
public class SequenceViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "SequenceViewModel";
    private static final String[] DIRECTIONS = {"FORWARD", "BACKWARD", "LEFT", "RIGHT"};
    private static final int SEQUENCE_LENGTH = 3;

    /** Duración del feedback visual de fallo antes de reintentar (ms). */
    private static final long WRONG_RETRY_DELAY_MS = 4000;
    /** Duración del mensaje de acierto antes de avanzar a la siguiente ronda (ms). */
    private static final long CORRECT_SHOWING_DELAY_MS = 4000;
    /** Duración de cada MOVE_TIMED en la ejecución física (ms). */
    private static final int MOVE_DURATION_MS = 800;
    /** Delay between sequential MOVE_TIMED commands to avoid BT buffer congestion (ms). */
    private static final long INTER_MOVE_DELAY_MS = 150;
    /** Overall timeout for the entire demo phase (ms). If demo doesn't complete, force-advance to INPUT. */
    private static final long DEMO_OVERALL_TIMEOUT_MS = 8000;

    public enum State {
        SHOWING,            // Mostrando la secuencia al alumno + robot ejecutándola
        INPUT,              // Esperando que el alumno pulse los 4 botones en orden
        CORRECT_SHOWING,    // Acierto: mostrando mensaje positivo, luego avanza a siguiente ronda
        WRONG_SHOWING,      // Fallo: mostrando mensaje suave, luego reintento
        DANCING,            // Última ronda: DANCE enviado, esperando DANCE_DONE
        COMPLETED           // DANCE_DONE recibido: fin
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<Integer> inputProgress = new MutableLiveData<>(0);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> currentSequence = new ArrayList<>();
    private List<String> userInput = new ArrayList<>();
    private int totalRounds = 3;
    private int completedRounds = 0;
    private int executingStepIndex = 0;
    private final Random random = new Random();

    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Safety timeout: if the entire demo (all MOVE_TIMED steps) doesn't complete
     * within DEMO_OVERALL_TIMEOUT_MS, force transition to INPUT to avoid getting stuck.
     */
    private final Runnable demoOverallTimeout = () -> {
        if (state.getValue() == State.SHOWING) {
            Log.w(TAG, "Demo overall timeout fired — forcing transition to INPUT");
            showingDemoDone = true;
            isShowingDemo = false;
            checkShowingComplete();
        }
    };

    // --- Tracking for SHOWING phase (demo + visual timer) ---
    private boolean showingDemoDone = false;
    private boolean showingTimerDone = false;
    /** True when in SHOWING phase executing demo moves */
    private boolean isShowingDemo = false;

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<String> items, int rounds) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.totalRounds = rounds > 0 ? rounds : 3;
        generateNewSequence();
        // Start the demo execution for the first showing
        startShowingDemo();
    }

    public LiveData<State> getState() { return state; }
    public LiveData<Integer> getInputProgress() { return inputProgress; }
    public List<String> getSequence() { return currentSequence; }
    public int getCompletedRounds() { return completedRounds; }
    public int getTotalRounds() { return totalRounds; }

    /**
     * Called by Activity after the visual showing timer finishes (3s).
     * Transition to INPUT only if the physical demo is also done.
     */
    public void onShowingFinished() {
        showingTimerDone = true;
        checkShowingComplete();
    }

    /**
     * Called when the user presses one of the 4 direction buttons.
     * Validates incrementally: each press is checked against the expected direction.
     * After 3 correct presses, triggers physical execution.
     * On first wrong press, triggers failure feedback.
     */
    public void onDirectionSelected(String direction) {
        if (state.getValue() != State.INPUT) return;

        int currentIndex = userInput.size();
        String expected = currentSequence.get(currentIndex);

        if (direction.equals(expected)) {
            // Correct press
            userInput.add(direction);
            inputProgress.setValue(userInput.size());

            if (userInput.size() >= SEQUENCE_LENGTH) {
                // Full sequence correct!
                completedRounds++;
                sendResult(true);
                // Show positive message, then advance (no physical execution)
                state.setValue(State.CORRECT_SHOWING);
                scheduleCorrectAdvance();
            }
        } else {
            // Wrong press — immediate failure
            sendResult(false);
            state.setValue(State.WRONG_SHOWING);
            scheduleRetry();
        }
    }

    /**
     * Called when MOVE_DONE is received from the Arduino.
     * Only handles the SHOWING demo phase (physical execution during sequence display).
     */
    public void onMoveDone() {
        State current = state.getValue();

        if (current == State.SHOWING && isShowingDemo) {
            // Demo move completed during SHOWING phase
            executingStepIndex++;
            if (executingStepIndex < currentSequence.size()) {
                // Small delay before sending next command to avoid BT buffer congestion
                handler.postDelayed(() -> {
                    if (state.getValue() == State.SHOWING && isShowingDemo) {
                        sendMoveTimedForStep(currentSequence.get(executingStepIndex));
                    }
                }, INTER_MOVE_DELAY_MS);
            } else {
                // All demo steps done
                showingDemoDone = true;
                isShowingDemo = false;
                checkShowingComplete();
            }
        }
    }

    /** Called when DANCE_DONE is received. */
    public void onDanceDone() {
        if (state.getValue() != State.DANCING) return;
        state.setValue(State.COMPLETED);
    }

    /** Fallback: force advance if MOVE_DONE doesn't arrive (timeout in Activity). */
    public void forceMoveDone() {
        onMoveDone();
    }

    /** Fallback: force dance done if DANCE_DONE doesn't arrive (timeout in Activity). */
    public void forceDanceDone() {
        onDanceDone();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacksAndMessages(null);
    }

    // --- Private ---

    /**
     * Starts the physical demo execution during SHOWING phase.
     * Sends the first MOVE_TIMED of the sequence. Subsequent steps are sent
     * when onMoveDone() is called.
     * Also starts an overall safety timeout to guarantee we transition to INPUT.
     */
    private void startShowingDemo() {
        showingDemoDone = false;
        showingTimerDone = false;
        isShowingDemo = true;
        executingStepIndex = 0;
        sendMoveTimedForStep(currentSequence.get(0));
        // Safety: if demo doesn't complete within overall timeout, force it done
        handler.postDelayed(demoOverallTimeout, DEMO_OVERALL_TIMEOUT_MS);
    }

    /**
     * Checks if both the visual timer and the physical demo are complete.
     * If so, transitions to INPUT.
     */
    private void checkShowingComplete() {
        if (showingDemoDone && showingTimerDone) {
            handler.removeCallbacks(demoOverallTimeout);
            userInput.clear();
            inputProgress.setValue(0);
            state.setValue(State.INPUT);
        }
    }

    /**
     * Schedules advance to next round after showing the "correct" message.
     * After CORRECT_SHOWING_DELAY_MS: if this was the last round, send DANCE;
     * otherwise generate new sequence and start SHOWING.
     */
    private void scheduleCorrectAdvance() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (completedRounds >= totalRounds) {
                // Last round: send DANCE
                sendDance();
                state.setValue(State.DANCING);
            } else {
                // More rounds: generate new sequence and show
                generateNewSequence();
                state.setValue(State.SHOWING);
                startShowingDemo();
            }
        }, CORRECT_SHOWING_DELAY_MS);
    }

    /** Schedules automatic retry after wrong answer (shows same sequence again). */
    private void scheduleRetry() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            // Show the SAME sequence again (don't generate new)
            userInput.clear();
            inputProgress.setValue(0);
            state.setValue(State.SHOWING);
            startShowingDemo();
        }, WRONG_RETRY_DELAY_MS);
    }

    /** Generate a new random sequence of 3 directions. */
    private void generateNewSequence() {
        currentSequence.clear();
        for (int i = 0; i < SEQUENCE_LENGTH; i++) {
            currentSequence.add(DIRECTIONS[random.nextInt(DIRECTIONS.length)]);
        }
        userInput.clear();
        inputProgress.postValue(0);
    }

    /** Send MOVE_TIMED for a single step of the sequence. */
    private void sendMoveTimedForStep(String direction) {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                "{\"dir\":\"" + direction + "\",\"ms\":" + MOVE_DURATION_MS + "}"));
    }

    private void sendDance() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_DANCE, null));
    }

    private void sendResult(boolean correct) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            payload.put("completedRounds", completedRounds);
            payload.put("totalRounds", totalRounds);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_ACTIVITY_RESULT);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error enviando resultado", e);
        }
    }

    // --- ActivityStatusProvider ---

    @Override public Integer getBatteryPct() { return null; }
    @Override public String getActivityId() { return AppConstants.ACTIVITY_SEQUENCE; }
    @Override public Integer getProgressPct() {
        if (totalRounds == 0) return null;
        return Math.min(100, completedRounds * 100 / totalRounds);
    }
}
