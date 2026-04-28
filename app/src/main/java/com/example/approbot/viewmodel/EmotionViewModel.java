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

import java.util.List;

public class EmotionViewModel extends ViewModel {

    private static final String TAG = "EmotionViewModel";

    public enum EmotionState { SHOWING, CORRECT, WRONG }

    private final MutableLiveData<EmotionState> state = new MutableLiveData<>(EmotionState.SHOWING);

    private TcpServer tcpServer;
    private BluetoothRobotManager bluetoothManager;
    private String sessionId;
    private String correctEmotionId;

    public void init(TcpServer tcpServer, BluetoothRobotManager bluetoothManager,
                     String sessionId, List<String> items) {
        this.tcpServer        = tcpServer;
        this.bluetoothManager = bluetoothManager;
        this.sessionId        = sessionId;
        // El primer ítem de la lista es la emoción a mostrar; las demás son distractores
        this.correctEmotionId = (items != null && !items.isEmpty()) ? items.get(0) : "";
    }

    public LiveData<EmotionState> getState() { return state; }
    public String getCorrectEmotionId()      { return correctEmotionId; }

    public void onOptionSelected(String optionId) {
        boolean correct = correctEmotionId.equals(optionId);
        state.postValue(correct ? EmotionState.CORRECT : EmotionState.WRONG);
        sendActivityResult(correct, optionId);
        if (correct) sendServoCommand();
    }

    /** Reinicia al estado inicial tras un fallo. */
    public void resetToShowing() {
        state.postValue(EmotionState.SHOWING);
    }

    private void sendActivityResult(boolean correct, String itemId) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", correct);
            payload.put("itemId", itemId);
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
