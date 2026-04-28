package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SequenceViewModel extends ViewModel {

    private static final String TAG = "SequenceViewModel";

    public enum SequenceState { SHOWING, INPUT, CORRECT, WRONG }

    private final MutableLiveData<SequenceState> state = new MutableLiveData<>(SequenceState.SHOWING);
    private final MutableLiveData<List<String>> shuffledOptions = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager bluetoothManager;
    private String sessionId;
    private List<String> sequence = new ArrayList<>();
    private List<String> userSelection = new ArrayList<>();

    public void init(TcpServer tcpServer, BluetoothRobotManager bluetoothManager,
                     String sessionId, List<String> allItems, int sequenceLength) {
        this.tcpServer        = tcpServer;
        this.bluetoothManager = bluetoothManager;
        this.sessionId        = sessionId;

        // Construir secuencia aleatoria de la longitud indicada
        List<String> pool = new ArrayList<>(allItems);
        Collections.shuffle(pool);
        sequence = pool.subList(0, Math.min(sequenceLength, pool.size()));

        // Opciones desordenadas para la fase INPUT
        List<String> opts = new ArrayList<>(sequence);
        Collections.shuffle(opts);
        shuffledOptions.setValue(opts);
    }

    public LiveData<SequenceState> getState()          { return state; }
    public LiveData<List<String>> getShuffledOptions() { return shuffledOptions; }
    public List<String> getSequence()                  { return sequence; }

    /** Llamado cuando termina el tiempo de muestra: pasa a fase INPUT. */
    public void onShowingFinished() {
        userSelection.clear();
        state.postValue(SequenceState.INPUT);
    }

    public void onItemSelected(String itemId) {
        if (SequenceState.INPUT != state.getValue()) return;
        userSelection.add(itemId);
        if (userSelection.size() == sequence.size()) {
            validateSelection();
        }
    }

    private void validateSelection() {
        boolean correct = userSelection.equals(sequence);
        if (correct) {
            state.postValue(SequenceState.CORRECT);
            sendActivityResult(true);
            sendServoCommand();
        } else {
            state.postValue(SequenceState.WRONG);
            sendActivityResult(false);
        }
    }

    /** Reinicia la secuencia (tras fallo). */
    public void restart() {
        userSelection.clear();
        state.postValue(SequenceState.SHOWING);
    }

    private void sendActivityResult(boolean correct) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_ACTIVITY_RESULT);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo ACTIVITY_RESULT", e);
        }
    }

    private void sendServoCommand() {
        if (bluetoothManager == null) {
            Log.w(TAG, "HC-05 no disponible, omitiendo SERVO_COMMAND");
            return;
        }
        bluetoothManager.send(new RobotMessage(AppConstants.MSG_SERVO_COMMAND, "CONFIRM"));
    }
}
