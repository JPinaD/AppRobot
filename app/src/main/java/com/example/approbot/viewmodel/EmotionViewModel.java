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

public class EmotionViewModel extends ViewModel {

    private static final String TAG = "EmotionViewModel";

    public enum State { SHOWING, CORRECT, WRONG, COMPLETED }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> emotionPool;
    private int currentIndex = 0;
    private int totalSteps = 5;
    private int correctCount = 0;
    private String correctEmotionId;

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<String> items, int steps) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.totalSteps = steps > 0 ? steps : 5;
        this.emotionPool = items != null && !items.isEmpty() ? items :
                defaultEmotions();
        pickNextEmotion();
    }

    public LiveData<State> getState() { return state; }
    public LiveData<Integer> getProgress() { return progress; }
    public String getCorrectEmotionId() { return correctEmotionId; }
    public int getTotalSteps() { return totalSteps; }
    public int getCorrectCount() { return correctCount; }

    public List<String> buildOptions() {
        List<String> options = new ArrayList<>();
        options.add(correctEmotionId);
        List<String> distractors = new ArrayList<>(emotionPool);
        distractors.remove(correctEmotionId);
        Collections.shuffle(distractors);
        for (String d : distractors) {
            if (options.size() >= 3) break;
            options.add(d);
        }
        Collections.shuffle(options);
        return options;
    }

    public void onOptionSelected(String selectedId) {
        boolean correct = selectedId.equals(correctEmotionId);
        sendResult(correct, selectedId);

        if (correct) {
            correctCount++;
            progress.postValue(correctCount);
            state.postValue(State.CORRECT);
            // Robot avanza una baldosa + celebra
            sendMoveTimed("FORWARD", 1000);
        } else {
            state.postValue(State.WRONG);
            // Robot hace negacion
            sendDeny();
        }
    }

    public void advanceAfterCorrect() {
        if (correctCount >= totalSteps) {
            state.postValue(State.COMPLETED);
            sendCelebrate();
        } else {
            pickNextEmotion();
            state.postValue(State.SHOWING);
        }
    }

    public void resetAfterWrong() {
        state.postValue(State.SHOWING);
    }

    private void pickNextEmotion() {
        currentIndex = (currentIndex + 1) % emotionPool.size();
        // Seleccionar aleatoriamente para variedad
        Collections.shuffle(emotionPool);
        correctEmotionId = emotionPool.get(0);
    }

    private List<String> defaultEmotions() {
        List<String> list = new ArrayList<>();
        list.add("emotion_happy");
        list.add("emotion_sad");
        list.add("emotion_angry");
        list.add("emotion_surprised");
        list.add("emotion_scared");
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
            payload.put("step", correctCount + 1);
            payload.put("totalSteps", totalSteps);
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
        String payload = "{\"dir\":\"" + dir + "\",\"ms\":" + ms + "}";
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED, payload));
    }

    private void sendDeny() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_DENY, null));
    }

    private void sendCelebrate() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_CELEBRATE, null));
    }
}
