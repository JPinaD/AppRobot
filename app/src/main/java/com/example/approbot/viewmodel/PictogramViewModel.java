package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.network.ActivityStatusProvider;
import com.example.approbot.network.TcpServer;
import com.example.approbot.ui.pictogram.ActivityTheme;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

public class PictogramViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "PictogramViewModel";
    private static final String ACTIVITY_ID = "pictogram_v1";

    private final MutableLiveData<Boolean> selectionConfirmed = new MutableLiveData<>(false);
    private final MutableLiveData<String>  feedbackText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> confirmationColor  = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager bluetoothManager;
    private StudentProfile studentProfile;

    private int totalPictograms = 0;
    private int selectedCount   = 0;
    private boolean active      = false;

    public void init(TcpServer tcpServer, BluetoothRobotManager bluetoothManager,
                     StudentProfile profile) {
        this.tcpServer        = tcpServer;
        this.bluetoothManager = bluetoothManager;
        this.studentProfile   = profile;
        confirmationColor.setValue(ActivityTheme.resolveConfirmationColor(profile));
    }

    public void setTotalPictograms(int total) {
        this.totalPictograms = total;
        this.active = total > 0;
    }

    public LiveData<Boolean>  getSelectionConfirmed() { return selectionConfirmed; }
    public LiveData<String>   getFeedbackText()        { return feedbackText; }
    public LiveData<Integer>  getConfirmationColor()   { return confirmationColor; }

    public void onPictogramSelected(String pictogramId) {
        selectedCount++;
        selectionConfirmed.postValue(true);
        sendPictogramSelected(pictogramId);
        sendServoConfirm();
    }

    public void onFeedbackReceived(String text) {
        feedbackText.postValue(text);
    }

    public void onActivityFinished() {
        active = false;
    }

    // --- ActivityStatusProvider ---

    @Override
    public Integer getBatteryPct() { return null; } // lo lee RobotStatusReporter del sistema

    @Override
    public String getActivityId() { return active ? ACTIVITY_ID : null; }

    @Override
    public Integer getProgressPct() {
        if (!active || totalPictograms == 0) return null;
        return Math.min(100, selectedCount * 100 / totalPictograms);
    }

    // --- privado ---

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
        bluetoothManager.send(new RobotMessage("SERVO_COMMAND", "CONFIRM"));
    }
}
