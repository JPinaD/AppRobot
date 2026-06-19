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
import java.util.Random;

public class SequenceViewModel extends ViewModel {

    private static final String TAG = "SequenceViewModel";
    private static final String[] MOVE_POOL = {"FORWARD", "LEFT", "RIGHT", "SERVO"};

    public enum State { SHOWING, INPUT, CORRECT, WRONG }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<List<String>> shuffledOptions = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> currentSequence = new ArrayList<>();
    private List<String> previousSequence = new ArrayList<>();
    private int inputIndex = 0;
    private int sequenceLength;
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

    /** Called after the showing animation finishes — transition to input. */
    public void onShowingFinished() {
        inputIndex = 0;
        List<String> options = new ArrayList<>(currentSequence);
        Collections.shuffle(options);
        shuffledOptions.postValue(options);
        state.postValue(State.INPUT);
    }

    /** Called when user taps an item in the input phase. */
    public void onItemSelected(String item) {
        String expected = currentSequence.get(inputIndex);
        if (item.equals(expected)) {
            inputIndex++;
            executeStep(item);
            if (inputIndex >= currentSequence.size()) {
                state.postValue(State.CORRECT);
                sendResult(true);
                sendCelebrate();
            }
        } else {
            state.postValue(State.WRONG);
            sendResult(false);
            sendDeny();
        }
    }

    /** Restart: generate new sequence and show again. */
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
                btManager.send(new RobotMessage("SERVO_COMMAND", "CONFIRM"));
                break;
        }
    }

    /** Execute the full sequence for demonstration. */
    public void executeFullSequence() {
        if (btManager == null) return;
        // Send each step with delays handled on Arduino side via MOVE_TIMED
        for (int i = 0; i < currentSequence.size(); i++) {
            final String step = currentSequence.get(i);
            final int delay = i * 1200; // 1.2s between steps
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> executeStep(step), delay);
        }
    }

    private void sendResult(boolean correct) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            payload.put("sequenceLength", sequenceLength);
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
}
