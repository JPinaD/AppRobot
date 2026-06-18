package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

public class TurnsViewModel extends ViewModel {

    private static final String TAG = "TurnsViewModel";

    public enum TurnState { MY_TURN, WAITING }

    private final MutableLiveData<TurnState> state = new MutableLiveData<>(TurnState.WAITING);

    private TcpServer tcpServer;
    private String sessionId;

    public void init(TcpServer tcpServer, String sessionId) {
        this.tcpServer = tcpServer;
        this.sessionId = sessionId;
    }

    public LiveData<TurnState> getState() { return state; }

    public void onTurnSignalReceived(boolean active) {
        state.postValue(active ? TurnState.MY_TURN : TurnState.WAITING);
    }

    public void onTurnButtonPressed() {
        if (TurnState.MY_TURN != state.getValue()) return;
        state.postValue(TurnState.WAITING);
        sendTurnDone();
    }

    private void sendTurnDone() {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_TURN_DONE);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo TURN_DONE", e);
        }
    }
}
