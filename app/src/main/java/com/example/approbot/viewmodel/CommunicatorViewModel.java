package com.example.approbot.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.approbot.network.ActivityStatusProvider;
import com.example.approbot.network.TcpServer;
import com.example.approbot.ui.communicator.PictogramCatalog;
import com.example.approbot.util.AppConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel para CommunicatorActivity.
 * Gestiona la composición de secuencias de pictogramas y la comunicación TCP.
 */
public class CommunicatorViewModel extends ViewModel implements ActivityStatusProvider {

    private static final String TAG = "CommunicatorVM";
    private static final int MAX_SEQUENCE_LENGTH = 6;

    public enum State {
        IDLE,               // Normal, alumno puede componer
        SENDING,            // Secuencia enviada, esperando respuesta del terapeuta
        RESPONSE_OK,        // Terapeuta entendió el mensaje
        RESPONSE_FAIL,      // Terapeuta no entendió
        RECEIVING           // Mostrando mensaje del terapeuta
    }

    private TcpServer tcpServer;
    private String sessionId;

    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final MutableLiveData<List<String>> compositionBar = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> receivedPictograms = new MutableLiveData<>();

    private int messagesSent = 0;

    public void init(TcpServer tcpServer, String sessionId) {
        this.tcpServer = tcpServer;
        this.sessionId = sessionId;
    }

    public LiveData<State> getState() { return state; }
    public LiveData<List<String>> getCompositionBar() { return compositionBar; }
    public LiveData<List<String>> getReceivedPictograms() { return receivedPictograms; }

    /**
     * Alumno toca un pictograma → se añade a la barra de composición.
     */
    public void addToComposition(String pictogramId) {
        if (state.getValue() != State.IDLE) return;
        List<String> current = new ArrayList<>(safeComposition());
        if (current.size() >= MAX_SEQUENCE_LENGTH) return;
        current.add(pictogramId);
        compositionBar.setValue(current);
    }

    /**
     * Alumno toca un pictograma en la barra → se elimina.
     */
    public void removeFromComposition(int index) {
        if (state.getValue() != State.IDLE) return;
        List<String> current = new ArrayList<>(safeComposition());
        if (index >= 0 && index < current.size()) {
            current.remove(index);
            compositionBar.setValue(current);
        }
    }

    /**
     * Alumno pulsa ENVIAR → envía COMMUNICATOR_SEQUENCE al terapeuta.
     */
    public void sendSequence() {
        List<String> current = safeComposition();
        if (current.isEmpty()) return;
        if (state.getValue() != State.IDLE) return;

        state.setValue(State.SENDING);

        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId != null ? sessionId : "");
            JSONArray ids = new JSONArray();
            for (String id : current) ids.put(id);
            payload.put("pictogramIds", ids);

            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_COMMUNICATOR_SEQUENCE);
            msg.put("payload", payload.toString());

            if (tcpServer != null) {
                tcpServer.sendToClient(msg.toString());
            }
            messagesSent++;
            Log.d(TAG, "COMMUNICATOR_SEQUENCE enviado: " + ids);
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo COMMUNICATOR_SEQUENCE", e);
            state.setValue(State.IDLE);
        }
    }

    /**
     * Recibido COMMUNICATOR_RESPONSE del terapeuta.
     */
    public void onCommunicatorResponse(boolean understood) {
        if (understood) {
            state.setValue(State.RESPONSE_OK);
        } else {
            state.setValue(State.RESPONSE_FAIL);
        }
    }

    /**
     * Tras mostrar feedback de respuesta, volver al estado normal.
     */
    public void resetAfterResponse() {
        compositionBar.setValue(new ArrayList<>());
        state.setValue(State.IDLE);
    }

    /**
     * Recibido TERAPEUTA_PICTOGRAM_MESSAGE: el terapeuta envía pictos al alumno.
     */
    public void onTerapeutaPictogramMessage(List<String> pictogramIds) {
        receivedPictograms.setValue(pictogramIds);
        state.setValue(State.RECEIVING);
    }

    /**
     * Alumno responde al mensaje del terapeuta (SÍ/NO).
     */
    public void respondToTerapeuta(boolean understood) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("understood", understood);

            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_STUDENT_PICTOGRAM_RESPONSE);
            msg.put("payload", payload.toString());

            if (tcpServer != null) {
                tcpServer.sendToClient(msg.toString());
            }
            Log.d(TAG, "STUDENT_PICTOGRAM_RESPONSE enviado: understood=" + understood);
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo STUDENT_PICTOGRAM_RESPONSE", e);
        }

        receivedPictograms.setValue(null);
        state.setValue(State.IDLE);
    }

    // --- ActivityStatusProvider ---

    @Override
    public Integer getBatteryPct() {
        return null; // Battery is reported by RobotStatusReporter independently
    }

    @Override
    public String getActivityId() {
        return AppConstants.ACTIVITY_COMMUNICATOR;
    }

    @Override
    public Integer getProgressPct() {
        return 0; // Communicator has no measurable progress
    }

    // --- Private helpers ---

    private List<String> safeComposition() {
        List<String> list = compositionBar.getValue();
        return list != null ? list : new ArrayList<>();
    }
}
