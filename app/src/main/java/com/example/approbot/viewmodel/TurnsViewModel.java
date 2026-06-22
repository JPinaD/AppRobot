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

public class TurnsViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "TurnsViewModel";

    public enum TurnState { WAITING, MY_TURN, COMPLETED }

    private final MutableLiveData<TurnState> state = new MutableLiveData<>(TurnState.WAITING);
    private final MutableLiveData<String> currentPictogram = new MutableLiveData<>();
    private final MutableLiveData<Integer> roundNumber = new MutableLiveData<>(0);

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;
    private List<String> pictogramPool;
    private final Random random = new Random();
    private int rounds = 0;
    private boolean soloMode = true; // Default solo mode

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<String> items) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.pictogramPool = items != null && !items.isEmpty() ? items : defaultPictograms();
    }

    public LiveData<TurnState> getState() { return state; }
    public LiveData<String> getCurrentPictogram() { return currentPictogram; }
    public LiveData<Integer> getRoundNumber() { return roundNumber; }
    public boolean isSoloMode() { return soloMode; }

    /** Called when TURN_SIGNAL is received (multiplayer mode). */
    public void onTurnSignalReceived(boolean active) {
        soloMode = false;
        if (active) {
            startMyTurn();
        } else {
            state.postValue(TurnState.WAITING);
            sendStop();
        }
    }

    /** For solo mode: start a new turn immediately. */
    public void startSoloTurn() {
        soloMode = true;
        startMyTurn();
    }

    /** Called when the child completes their turn. */
    public void onTurnCompleted() {
        rounds++;
        roundNumber.postValue(rounds);
        sendCelebrate();
        sendTurnDone();

        if (soloMode) {
            // In solo mode, auto-start next turn after celebration
            state.postValue(TurnState.WAITING);
        } else {
            state.postValue(TurnState.WAITING);
            sendMoveTimed("BACKWARD", 800);
        }
    }

    private void startMyTurn() {
        // Pick random pictogram for this turn's mini-activity
        String pic = pictogramPool.get(random.nextInt(pictogramPool.size()));
        currentPictogram.postValue(pic);
        state.postValue(TurnState.MY_TURN);

        // Robot approaches (multiplayer) or celebrates (solo)
        if (!soloMode) {
            sendMoveTimed("FORWARD", 800);
        }
    }

    private List<String> defaultPictograms() {
        List<String> list = new ArrayList<>();
        list.add("pic_agua");
        list.add("pic_jugar");
        list.add("pic_comer");
        list.add("pic_ayuda");
        return list;
    }

    private void sendTurnDone() {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("round", rounds);
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

    private void sendCelebrate() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_CELEBRATE, null));
    }

    private void sendStop() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_STOP, null));
    }

    // --- ActivityStatusProvider ---

    @Override public Integer getBatteryPct() { return null; }
    @Override public String getActivityId() { return AppConstants.ACTIVITY_TURNS; }
    @Override public Integer getProgressPct() { return null; } // turnos no tienen progreso lineal
}
