package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.SessionConfig.SocialScenarioContent;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SocialViewModel extends ViewModel {

    private static final String TAG = "SocialViewModel";

    public static class UiState {
        public final SocialScenarioContent scenario;
        public final String outcomeText;
        public final int currentIndex;
        public final int total;

        UiState(SocialScenarioContent scenario, String outcomeText, int index, int total) {
            this.scenario = scenario;
            this.outcomeText = outcomeText;
            this.currentIndex = index;
            this.total = total;
        }
    }

    private final MutableLiveData<UiState> state = new MutableLiveData<>();

    private TcpServer tcpServer;
    private BluetoothRobotManager btManager;
    private String sessionId;
    private List<SocialScenarioContent> scenarios;
    private int currentIndex = 0;

    public void init(TcpServer tcpServer, BluetoothRobotManager btManager,
                     String sessionId, List<SocialScenarioContent> scenarios) {
        this.tcpServer = tcpServer;
        this.btManager = btManager;
        this.sessionId = sessionId;
        this.scenarios = scenarios;
        showScenario();
    }

    public LiveData<UiState> getState() { return state; }

    public void onOptionSelected(String option) {
        SocialScenarioContent s = scenarios.get(currentIndex);
        boolean isA = "A".equals(option);
        String outcome = isA ? s.outcomeA : s.outcomeB;

        // Robot takes path: left for A, right for B
        String dir = isA ? "LEFT" : "RIGHT";
        sendMoveTimed(dir, 500);

        sendResult(option, s.id);
        state.postValue(new UiState(s, outcome, currentIndex, scenarios.size()));
    }

    public void nextScenario() {
        // Robot returns to center
        sendMoveTimed("BACKWARD", 500);

        currentIndex++;
        if (currentIndex >= scenarios.size()) {
            currentIndex = 0; // Loop or could end
            sendCelebrate();
        }
        showScenario();
    }

    private void showScenario() {
        SocialScenarioContent s = scenarios.get(currentIndex);
        state.postValue(new UiState(s, null, currentIndex, scenarios.size()));
    }

    private void sendResult(String option, String scenarioId) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("scenarioId", scenarioId);
            payload.put("selectedOption", option);
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
        btManager.send(new RobotMessage(AppConstants.MSG_MOVE_TIMED,
                "{\"dir\":\"" + dir + "\",\"ms\":" + ms + "}"));
    }

    private void sendCelebrate() {
        if (btManager == null) return;
        btManager.send(new RobotMessage(AppConstants.MSG_CELEBRATE, null));
    }
}
