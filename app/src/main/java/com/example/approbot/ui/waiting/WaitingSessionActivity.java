package com.example.approbot.ui.waiting;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.approbot.R;
import com.example.approbot.bluetooth.BluetoothDeviceSelector;
import com.example.approbot.bluetooth.BluetoothRobotListener;
import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.StudentProfile;
import com.example.approbot.data.repository.RobotIdentityRepository;
import com.example.approbot.network.NsdAdvertiser;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.network.TcpServer;
import com.example.approbot.ui.pictogram.PictogramActivity;
import com.example.approbot.util.AppConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WaitingSessionActivity extends AppCompatActivity implements BluetoothRobotListener {

    private static final String TAG = "WaitingSessionActivity";

    private NsdAdvertiser nsdAdvertiser;
    private TcpServer tcpServer;
    private TextView tvNetworkStatus;
    private RobotIdentityRepository identityRepository;
    private BluetoothRobotManager bluetoothRobotManager;

    private static PictogramActivity activePictogramActivity;

    public static void registerPictogramActivity(PictogramActivity activity) {
        activePictogramActivity = activity;
    }

    public static void unregisterPictogramActivity() {
        activePictogramActivity = null;
    }

    private final ActivityResultLauncher<String> requestBluetoothPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startBluetoothConnection();
                } else {
                    Toast.makeText(this,
                            "Permiso Bluetooth necesario para conectar con el robot.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_session);

        tvNetworkStatus    = findViewById(R.id.tvNetworkStatus);
        identityRepository = new RobotIdentityRepository(this);

        int port = identityRepository.getPort();

        nsdAdvertiser        = new NsdAdvertiser(this);
        tcpServer            = new TcpServer(port, this::handleTcpMessage);
        bluetoothRobotManager = new BluetoothRobotManager();
        bluetoothRobotManager.setListener(this);

        SessionNetworkHolder.init(tcpServer, bluetoothRobotManager);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        TextView tvSelectedProfileName        = findViewById(R.id.tvSelectedProfileName);
        TextView tvSelectedProfileDescription = findViewById(R.id.tvSelectedProfileDescription);
        tvSelectedProfileName.setText(getIntent().getStringExtra("profile_name"));
        tvSelectedProfileDescription.setText(getIntent().getStringExtra("profile_description"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        tvNetworkStatus.setText(getString(R.string.network_status_waiting));

        String robotName = identityRepository.getRobotName("Robot-1");
        int port         = identityRepository.getPort();

        tcpServer.start();
        nsdAdvertiser.start(robotName, port);

        if (hasBluetoothPermission()) {
            startBluetoothConnection();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        nsdAdvertiser.stop();
        tcpServer.stop();
        bluetoothRobotManager.disconnect();
        SessionNetworkHolder.clear();
    }

    // --- Enrutamiento de mensajes TCP ---

    private void handleTcpMessage(String message, java.io.PrintWriter out) {
        if (AppConstants.MSG_PING.equals(message)) {
            out.println(AppConstants.MSG_PONG);
            runOnUiThread(() -> tvNetworkStatus.setText(getString(R.string.network_status_connected)));
            return;
        }

        try {
            JSONObject obj  = new JSONObject(message);
            String type     = obj.getString("type");
            String payloadStr = obj.optString("payload", null);

            switch (type) {
                case AppConstants.MSG_ACTIVITY_START:
                    handleActivityStart(payloadStr);
                    break;
                case AppConstants.MSG_ROBOT_FEEDBACK:
                    handleRobotFeedback(payloadStr);
                    break;
                default:
                    Log.d(TAG, "Mensaje no manejado: " + type);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Mensaje TCP no parseable: " + message);
        }
    }

    private void handleActivityStart(String payloadStr) {
        if (payloadStr == null) {
            Log.w(TAG, "ACTIVITY_START sin payload, ignorado");
            return;
        }
        try {
            JSONObject payload = new JSONObject(payloadStr);
            JSONArray pics = payload.optJSONArray("pictograms");
            if (pics == null || pics.length() == 0) {
                Log.w(TAG, "ACTIVITY_START con lista de pictogramas vacía, ignorado");
                return;
            }

            ArrayList<String> pictogramList = new ArrayList<>();
            for (int i = 0; i < pics.length(); i++) pictogramList.add(pics.getString(i));

            // Extraer perfil del alumno (opcional, retrocompatible)
            StudentProfile profile = null;
            JSONObject profileObj = payload.optJSONObject("studentProfile");
            if (profileObj != null) {
                profile = StudentProfile.fromJson(profileObj);
            }

            final StudentProfile finalProfile = profile;
            runOnUiThread(() -> {
                Intent intent = new Intent(this, PictogramActivity.class);
                intent.putStringArrayListExtra(PictogramActivity.EXTRA_PICTOGRAMS, pictogramList);
                if (finalProfile != null) {
                    intent.putExtra(PictogramActivity.EXTRA_STUDENT_PROFILE,
                            profileObj != null ? profileObj.toString() : null);
                }
                startActivity(intent);
            });
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando payload de ACTIVITY_START: " + payloadStr);
        }
    }

    private void handleRobotFeedback(String payloadStr) {
        if (payloadStr == null) return;
        try {
            JSONObject payload = new JSONObject(payloadStr);
            String text = payload.optString("text", null);
            if (text == null) return;

            PictogramActivity target = activePictogramActivity;
            if (target != null) {
                target.runOnUiThread(() -> target.getViewModel().onFeedbackReceived(text));
            } else {
                Log.w(TAG, "ROBOT_FEEDBACK recibido pero PictogramActivity no está activa");
            }
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando payload de ROBOT_FEEDBACK: " + payloadStr);
        }
    }

    // --- BluetoothRobotListener ---

    @Override public void onConnected() { Log.i(TAG, "Conectado al robot físico vía Bluetooth"); }

    @Override
    public void onMessageReceived(RobotMessage message) {
        Log.d(TAG, "Mensaje recibido del robot: type=" + message.type + " payload=" + message.payload);
    }

    @Override
    public void onConnectionError(String reason) {
        runOnUiThread(() -> Toast.makeText(this, "Error Bluetooth: " + reason, Toast.LENGTH_LONG).show());
    }

    @Override public void onDisconnected() { Log.i(TAG, "Desconectado del robot físico"); }

    // --- privado ---

    private void startBluetoothConnection() {
        String mac = identityRepository.getHcMac();
        if (mac != null) {
            bluetoothRobotManager.connect(mac);
        } else {
            showBluetoothDeviceSelector();
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void showBluetoothDeviceSelector() {
        BluetoothDeviceSelector selector = new BluetoothDeviceSelector();
        List<BluetoothDeviceSelector.BluetoothDeviceInfo> devices = selector.getPairedDevices();

        if (devices.isEmpty()) {
            Toast.makeText(this,
                    "No hay dispositivos Bluetooth emparejados. Empareja el HC-05 desde Ajustes.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            names[i] = devices.get(i).name + " (" + devices.get(i).mac + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Selecciona el módulo HC-05")
                .setItems(names, (dialog, which) -> {
                    String mac = devices.get(which).mac;
                    identityRepository.saveHcMac(mac);
                    bluetoothRobotManager.connect(mac);
                })
                .setCancelable(false)
                .show();
    }
}
