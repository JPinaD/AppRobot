package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.TcpServer;
import com.example.approbot.ui.pictogram.ActivityTheme;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

public class PictogramViewModel extends ViewModel {

    private static final String TAG = "PictogramViewModel";

    private final MutableLiveData<Boolean> selectionConfirmed = new MutableLiveData<>(false);
    private final MutableLiveData<String>  feedbackText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> confirmationColor  = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager bluetoothManager;
    private StudentProfile studentProfile;

    public void init(TcpServer tcpServer, BluetoothRobotManager bluetoothManager,
                     StudentProfile profile) {
        this.tcpServer        = tcpServer;
        this.bluetoothManager = bluetoothManager;
        this.studentProfile   = profile;
        confirmationColor.setValue(ActivityTheme.resolveConfirmationColor(profile));
    }

    public LiveData<Boolean>  getSelectionConfirmed() { return selectionConfirmed; }
    public LiveData<String>   getFeedbackText()        { return feedbackText; }
    public LiveData<Integer>  getConfirmationColor()   { return confirmationColor; }

    public void onPictogramSelected(String pictogramId) {
        selectionConfirmed.postValue(true);
        sendPictogramSelected(pictogramId);
        sendServoConfirm();
    }

    public void onFeedbackReceived(String text) {
        feedbackText.postValue(text);
    }

    private void sendPictogramSelected(String pictogramId) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("pictogramId", pictogramId);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_PICTOGRAM_SELECTED);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo PICTOGRAM_SELECTED", e);
        }
    }

    private void sendServoConfirm() {
        if (bluetoothManager == null) {
            Log.w(TAG, "HC-05 no disponible, omitiendo SERVO_COMMAND");
            return;
        }
        bluetoothManager.send(new RobotMessage(AppConstants.MSG_SERVO_CONFIRM, null));
    }
}
