package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.SessionConfig.SocialScenarioContent;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SocialViewModel extends ViewModel {

    private static final String TAG = "SocialViewModel";

    public static class SocialState {
        public final SocialScenarioContent scenario;
        public final String outcomeText; // null = mostrando opciones
        public SocialState(SocialScenarioContent scenario, String outcomeText) {
            this.scenario    = scenario;
            this.outcomeText = outcomeText;
        }
    }

    private final MutableLiveData<SocialState> state = new MutableLiveData<>();

    private TcpServer tcpServer;
    private String sessionId;
    private List<SocialScenarioContent> scenarios;
    private int currentIndex = 0;

    public void init(TcpServer tcpServer, String sessionId,
                     List<SocialScenarioContent> scenarios) {
        this.tcpServer  = tcpServer;
        this.sessionId  = sessionId;
        this.scenarios  = scenarios;
        if (scenarios != null && !scenarios.isEmpty()) {
            state.setValue(new SocialState(scenarios.get(0), null));
        }
    }

    public LiveData<SocialState> getState() { return state; }

    public void onOptionSelected(String option) {
        SocialState current = state.getValue();
        if (current == null || current.scenario == null) return;

        String outcome = "A".equals(option)
                ? current.scenario.outcomeA
                : current.scenario.outcomeB;

        state.postValue(new SocialState(current.scenario, outcome));
        sendActivityResult(option);
    }

    /** Avanza al siguiente escenario o reinicia al primero. */
    public void nextScenario() {
        if (scenarios == null || scenarios.isEmpty()) return;
        currentIndex = (currentIndex + 1) % scenarios.size();
        state.postValue(new SocialState(scenarios.get(currentIndex), null));
    }

    /** Actualiza el escenario activo (llamado al recibir SESSION_START con nuevo escenario). */
    public void updateScenario(SocialScenarioContent scenario) {
        state.postValue(new SocialState(scenario, null));
    }

    private void sendActivityResult(String option) {
        if (tcpServer == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("correct", JSONObject.NULL);
            payload.put("itemId", option);
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_ACTIVITY_RESULT);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo ACTIVITY_RESULT", e);
        }
    }
}
