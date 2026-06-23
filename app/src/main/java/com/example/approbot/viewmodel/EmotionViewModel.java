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

public class EmotionViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "EmotionViewModel";

    public enum State { SHOWING, CORRECT, WRONG, COMPLETED }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SHOWING);
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;

    private List<String> emotionPool;
    private int totalRounds = 3;
    private int currentRound = 0;
    private String correctEmotionId;
    private String previousEmotionId;

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

    public void onOptionSelected(String selectedId) {
        boolean correct = selectedId.equals(correctEmotionId);
        sendResult(correct, selectedId);

        if (correct) {
            currentRound++;
            progress.postValue(currentRound);
            state.postValue(State.CORRECT);
            sendCelebrate();
        } else {
            state.postValue(State.WRONG);
            sendDeny();
        }
    }

    public void advanceAfterCorrect() {
        if (currentRound >= totalRounds) {
            state.postValue(State.COMPLETED);
        } else {
            pickNextEmotion();
            state.postValue(State.SHOWING);
        }
    }

    public void resetAfterWrong() {
        state.postValue(State.SHOWING);
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
        list.add("emotion_love");
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

    private void sendCelebrate() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_CELEBRATE, null));
    }

    private void sendDeny() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_DENY, null));
    }

    // --- ActivityStatusProvider ---
    @Override public Integer getBatteryPct() { return null; }
    @Override public String getActivityId() { return currentRound < totalRounds ? AppConstants.ACTIVITY_EMOTION : null; }
    @Override public Integer getProgressPct() {
        if (totalRounds == 0) return null;
        return Math.min(100, currentRound * 100 / totalRounds);
    }
}
