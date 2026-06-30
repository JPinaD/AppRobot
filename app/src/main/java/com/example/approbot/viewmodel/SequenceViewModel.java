package com.example.approbot.viewmodel;

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
import java.util.Random;

/**
 * ViewModel para la actividad de Secuencias Visuales.
 *
 * Feedback estandarizado TEA:
 * - Acierto (secuencia completa correcta): CELEBRATE → esperar CELEBRATE_DONE
 *   → luego ejecutar la secuencia físicamente como recompensa bonus
 * - Fallo: DENY → esperar DENY_DONE → volver a mostrar secuencia (reintento)
 * - Completitud (N secuencias correctas): DANCE → esperar DANCE_DONE → fin
 */
public class SequenceViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "SequenceViewModel";
    private static final String[] MOVE_POOL = {"FORWARD", "LEFT", "RIGHT", "SERVO"};
    private static final int TOTAL_SEQUENCES_TO_COMPLETE = 3; // Actividad termina tras 3 secuencias correctas

    public enum State {
        SHOWING,           // Mostrando secuencia al alumno
        INPUT,             // Esperando que el alumno reproduzca la secuencia
        CORRECT,           // Secuencia correcta: CELEBRATE enviado
        EXECUTING_BONUS,   // CELEBRATE_DONE recibido, ejecutando secuencia física como bonus
        WRONG,             // Fallo: DENY enviado
        COMPLETING,        // Actividad completada: DANCE enviado
        COMPLETED          // DANCE_DONE recibido: fin
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<List<String>> shuffledOptions = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> currentSequence = new ArrayList<>();
    private List<String> previousSequence = new ArrayList<>();
    private int inputIndex = 0;
    private int sequenceLength;
    private int completedSequences = 0; // Número de secuencias acertadas
    private final Random random = new Random();

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<String> items, int seqLength) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.sequenceLength = Math.max(2, Math.min(seqLength, 5));
        generateNewSequence();
    }

    public LiveData<State> getState() { return state; }
    public LiveData<List<String>> getShuffledOptions() { return shuffledOptions; }
    public List<String> getSequence() { return currentSequence; }
    public int getCompletedSequences() { return completedSequences; }
    public int getTotalSequencesToComplete() { return TOTAL_SEQUENCES_TO_COMPLETE; }

    /** Called after the showing animation finishes — transition to input. */
    public void onShowingFinished() {
        inputIndex = 0;
        List<String> options = new ArrayList<>(currentSequence);
        Collections.shuffle(options);
        shuffledOptions.postValue(options);
        state.postValue(State.INPUT);
    }

    /**
     * Called when user taps an item in the input phase.
     * Cada ítem se ejecuta físicamente uno a uno.
     * Si completa toda la secuencia correctamente: CELEBRATE.
     * Si se equivoca: DENY.
     */
    public void onItemSelected(String item) {
        String expected = currentSequence.get(inputIndex);
        if (item.equals(expected)) {
            inputIndex++;
            // Ejecutar el paso individual en el robot (feedback incremental)
            executeStep(item);
            if (inputIndex >= currentSequence.size()) {
                // Secuencia completa correcta
                completedSequences++;
                sendResult(true);
                state.postValue(State.CORRECT);
                sendCelebrate();
            }
        } else {
            // Fallo: enviar DENY (feedback estandarizado)
            sendResult(false);
            state.postValue(State.WRONG);
            sendDeny();
        }
    }

    /**
     * Llamado cuando se recibe CELEBRATE_DONE del Arduino.
     * Ejecuta la secuencia completa como bonus de recompensa.
     */
    public void onCelebrateDone() {
        if (state.getValue() != State.CORRECT) return;

        if (completedSequences >= TOTAL_SEQUENCES_TO_COMPLETE) {
            // Actividad completada: enviar DANCE
            state.postValue(State.COMPLETING);
            sendDance();
        } else {
            // Bonus: ejecutar la secuencia físicamente como recompensa visual
            state.postValue(State.EXECUTING_BONUS);
            executeFullSequence();
        }
    }

    /**
     * Llamado tras la ejecución del bonus (llamado por la Activity con timeout
     * basado en la duración de la secuencia).
     * Genera nueva secuencia y vuelve a SHOWING.
     */
    public void onBonusFinished() {
        if (state.getValue() != State.EXECUTING_BONUS) return;
        generateNewSequence();
        state.postValue(State.SHOWING);
    }

    /**
     * Llamado cuando se recibe DENY_DONE del Arduino.
     * Vuelve a mostrar la secuencia (reintento sin penalización).
     */
    public void onDenyDone() {
        if (state.getValue() != State.WRONG) return;
        // Volver a mostrar la MISMA secuencia (reintento)
        state.postValue(State.SHOWING);
    }

    /**
     * Llamado cuando se recibe DANCE_DONE del Arduino.
     * Transiciona a COMPLETED.
     */
    public void onDanceDone() {
        if (state.getValue() != State.COMPLETING) return;
        state.postValue(State.COMPLETED);
    }

    /** Fallback: restart con nueva secuencia (legacy, usado como fallback de timeout). */
    public void restart() {
        generateNewSequence();
        state.postValue(State.SHOWING);
    }

    /** Generate a random sequence different from the previous one. */
    private void generateNewSequence() {
        List<String> seq;
        do {
            seq = new ArrayList<>();
            for (int i = 0; i < sequenceLength; i++) {
                seq.add(MOVE_POOL[random.nextInt(MOVE_POOL.length)]);
            }
        } while (seq.equals(previousSequence));
        previousSequence = new ArrayList<>(seq);
        currentSequence = seq;
    }

    /** Execute a single step on the physical robot. */
    private void executeStep(String step) {
        if (btManager == null) return;
        switch (step) {
            case "FORWARD":
                btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                        "{\"dir\":\"FORWARD\",\"ms\":600}"));
                break;
            case "LEFT":
                btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                        "{\"dir\":\"LEFT\",\"ms\":400}"));
                break;
            case "RIGHT":
                btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                        "{\"dir\":\"RIGHT\",\"ms\":400}"));
                break;
            case "SERVO":
                btManager.send(new RobotMessage(AppConstants.MSG_SERVO_COMMAND, "CONFIRM"));
                break;
        }
    }

    /** Execute the full sequence as a bonus reward. */
    public void executeFullSequence() {
        if (btManager == null) return;
        for (int i = 0; i < currentSequence.size(); i++) {
            final String step = currentSequence.get(i);
            final int delay = i * 1200; // 1.2s between steps
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> executeStep(step), delay);
        }
    }

    /** Calcula la duración total de la ejecución bonus en ms. */
    public long getBonusDurationMs() {
        return (long) currentSequence.size() * 1200 + 500; // duración + margen
    }

    private void sendResult(boolean correct) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            payload.put("sequenceLength", sequenceLength);
            payload.put("completedSequences", completedSequences);
            payload.put("totalSequences", TOTAL_SEQUENCES_TO_COMPLETE);
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
    @Override public String getActivityId() { return AppConstants.ACTIVITY_SEQUENCE; }
    @Override public Integer getProgressPct() {
        if (TOTAL_SEQUENCES_TO_COMPLETE == 0) return null;
        return Math.min(100, completedSequences * 100 / TOTAL_SEQUENCES_TO_COMPLETE);
    }
}
