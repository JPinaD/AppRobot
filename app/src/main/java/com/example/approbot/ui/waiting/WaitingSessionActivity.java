package com.example.approbot.ui.waiting;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.approbot.R;
import com.example.approbot.bluetooth.BluetoothDeviceSelector;
import com.example.approbot.bluetooth.BluetoothRobotListener;
import com.example.approbot.bluetooth.BluetoothRobotManager;
import com.example.approbot.data.model.RobotMessage;
import com.example.approbot.data.model.SessionConfig;
import com.example.approbot.data.repository.RobotIdentityRepository;
import com.example.approbot.network.RobotNetworkService;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.ui.pictogram.PictogramActivity;
import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WaitingSessionActivity extends AppCompatActivity implements BluetoothRobotListener {

    private static final String TAG = "WaitingSessionActivity";

    private TextView tvNetworkStatus;
    private TextView tvBluetoothStatus;
    private TextView tvBatteryStatus;
    private RobotIdentityRepository identityRepository;
    private BluetoothRobotManager bluetoothRobotManager;

    private RobotNetworkService networkService;
    private boolean serviceBound = false;

    private static PictogramActivity activePictogramActivity;

    public static void registerPictogramActivity(PictogramActivity activity) {
        activePictogramActivity = activity;
    }

    public static void unregisterPictogramActivity() {
        activePictogramActivity = null;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            networkService = ((RobotNetworkService.LocalBinder) binder).getService();
            serviceBound = true;
            String robotName = identityRepository.getRobotName("Robot-1");
            int port = identityRepository.getPort();
            networkService.startNetwork(robotName, port,
                    WaitingSessionActivity.this::handleTcpMessage, bluetoothRobotManager);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            serviceBound = false;
        }
    };

    private final ActivityResultLauncher<String> requestBluetoothPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startBluetoothConnection();
                else Toast.makeText(this, "Permiso Bluetooth necesario.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_session);

        tvNetworkStatus   = findViewById(R.id.tvNetworkStatus);
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus);
        tvBatteryStatus   = findViewById(R.id.tvBatteryStatus);
        identityRepository = new RobotIdentityRepository(this);

        bluetoothRobotManager = new BluetoothRobotManager();
        bluetoothRobotManager.setListener(this);

        findViewById(R.id.back_button).setOnClickListener(v -> {
            bluetoothRobotManager.disconnect();
            stopService(new Intent(this, RobotNetworkService.class));
            finish();
        });
        ((TextView) findViewById(R.id.tvSelectedProfileName)).setText(
                getIntent().getStringExtra("profile_name"));
        ((TextView) findViewById(R.id.tvSelectedProfileDescription)).setText(
                getIntent().getStringExtra("profile_description"));

        // Arrancar y enlazar el servicio de red — solo una vez en onCreate
        Intent serviceIntent = new Intent(this, RobotNetworkService.class);
        startService(serviceIntent);
        boolean bound = bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        tvNetworkStatus.setText(getString(R.string.network_status_waiting));

        // Bluetooth
        if (hasBluetoothPermission()) startBluetoothConnection();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // No desconectamos BT aquí — debe seguir activo durante la sesión en PictogramActivity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        // Paramos el servicio solo si el usuario sale explícitamente (botón volver)
        // No lo paramos en recreaciones de configuración
    }

    // --- Enrutamiento de mensajes TCP ---

    private void handleTcpMessage(String message, java.io.PrintWriter out) {
        if (AppConstants.MSG_PING.equals(message)) {
            out.println(AppConstants.MSG_PONG);
            runOnUiThread(() -> tvNetworkStatus.setText(getString(R.string.network_status_connected)));
            return;
        }
        try {
            JSONObject obj    = new JSONObject(message);
            String type       = obj.getString("type");
            String payloadStr = obj.optString("payload", null);

            switch (type) {
                case AppConstants.MSG_SESSION_START:
                    handleSessionStart(payloadStr, out);
                    break;
                case AppConstants.MSG_SESSION_END:
                    handleSessionEnd(payloadStr, out);
                    break;
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

    private void handleSessionStart(String payloadStr, java.io.PrintWriter out) {
        SessionConfig config = SessionConfig.fromJson(payloadStr);
        if (config == null) {
            Log.w(TAG, "SESSION_START con payload inválido, ignorado");
            return;
        }
        if (!"pictogram_v1".equals(config.activityId)) {
            Log.w(TAG, "SESSION_START con activityId desconocido: " + config.activityId);
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", config.sessionId);
            payload.put("robotId", identityRepository.getRobotName("Robot-1"));
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_SESSION_READY);
            msg.put("payload", payload.toString());
            out.println(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo SESSION_READY", e);
        }

        ArrayList<String> pictogramList = new ArrayList<>(config.pictograms);
        String profileJson = config.studentProfile != null
                ? studentProfileToJson(config.studentProfile) : null;

        runOnUiThread(() -> {
            Intent intent = new Intent(this, PictogramActivity.class);
            intent.putStringArrayListExtra(PictogramActivity.EXTRA_PICTOGRAMS, pictogramList);
            if (profileJson != null) intent.putExtra(PictogramActivity.EXTRA_STUDENT_PROFILE, profileJson);
            startActivity(intent);
        });
    }

    private void handleSessionEnd(String payloadStr, java.io.PrintWriter out) {
        String sessionId = "";
        try {
            if (payloadStr != null) sessionId = new JSONObject(payloadStr).optString("sessionId", "");
        } catch (JSONException ignored) {}

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(AppConstants.ACTION_SESSION_END));

        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("robotId", identityRepository.getRobotName("Robot-1"));
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_SESSION_ENDED);
            msg.put("payload", payload.toString());
            out.println(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo SESSION_ENDED", e);
        }
    }

    private void handleActivityStart(String payloadStr) {
        if (payloadStr == null) return;
        try {
            JSONObject payload = new JSONObject(payloadStr);
            org.json.JSONArray pics = payload.optJSONArray("pictograms");
            if (pics == null || pics.length() == 0) return;
            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < pics.length(); i++) list.add(pics.getString(i));
            JSONObject profileObj = payload.optJSONObject("studentProfile");
            final String profileJson = profileObj != null ? profileObj.toString() : null;
            runOnUiThread(() -> {
                Intent intent = new Intent(this, PictogramActivity.class);
                intent.putStringArrayListExtra(PictogramActivity.EXTRA_PICTOGRAMS, list);
                if (profileJson != null) intent.putExtra(PictogramActivity.EXTRA_STUDENT_PROFILE, profileJson);
                startActivity(intent);
            });
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando ACTIVITY_START: " + payloadStr);
        }
    }

    private void handleRobotFeedback(String payloadStr) {
        if (payloadStr == null) return;
        try {
            String text = new JSONObject(payloadStr).optString("text", null);
            if (text == null) return;
            PictogramActivity target = activePictogramActivity;
            if (target != null) target.runOnUiThread(() -> target.getViewModel().onFeedbackReceived(text));
        } catch (JSONException e) {
            Log.w(TAG, "Error parseando ROBOT_FEEDBACK: " + payloadStr);
        }
    }

    // --- BluetoothRobotListener ---

    @Override
    public void onConnected() {
        Log.i(TAG, "Conectado al robot físico vía Bluetooth");
        runOnUiThread(() -> tvBluetoothStatus.setText(getString(R.string.bt_status_connecting)));
        bluetoothRobotManager.send(new RobotMessage(AppConstants.MSG_PING, null));
    }

    @Override
    public void onMessageReceived(RobotMessage m) {
        Log.d(TAG, "BT: " + m.type);
        switch (m.type) {
            case AppConstants.MSG_PONG:
                runOnUiThread(() -> tvBluetoothStatus.setText(getString(R.string.bt_status_verified)));
                break;
            case AppConstants.MSG_BATTERY_STATUS:
                handleBatteryStatus(m.payload);
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnectionError(String r) {
        runOnUiThread(() -> {
            tvBluetoothStatus.setText(getString(R.string.bt_status_error));
            Toast.makeText(this, "Error Bluetooth: " + r, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Desconectado del robot físico");
        runOnUiThread(() -> tvBluetoothStatus.setText(getString(R.string.bt_status_disconnected)));
    }

    // --- privado ---

    private void handleBatteryStatus(String payload) {
        if (payload == null) return;
        try {
            int level = Integer.parseInt(payload.trim());
            runOnUiThread(() -> {
                tvBatteryStatus.setText(getString(R.string.bt_battery_format, level));
                tvBatteryStatus.setVisibility(android.view.View.VISIBLE);
            });
        } catch (NumberFormatException e) {
            Log.w(TAG, "BATTERY_STATUS con valor no numérico ignorado: " + payload);
        }
    }

    private void startBluetoothConnection() {
        String mac = identityRepository.getHcMac();
        if (mac != null) bluetoothRobotManager.connect(this, mac);
        else showBluetoothDeviceSelector();
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void showBluetoothDeviceSelector() {
        BluetoothDeviceSelector selector = new BluetoothDeviceSelector();
        List<BluetoothDeviceSelector.BluetoothDeviceInfo> devices = selector.getPairedDevices(this);
        if (devices.isEmpty()) {
            Toast.makeText(this, "No hay dispositivos Bluetooth emparejados.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++)
            names[i] = devices.get(i).name + " (" + devices.get(i).mac + ")";
        new AlertDialog.Builder(this)
                .setTitle("Selecciona el módulo HC-05")
                .setItems(names, (d, w) -> {
                    identityRepository.saveHcMac(devices.get(w).mac);
                    bluetoothRobotManager.connect(WaitingSessionActivity.this, devices.get(w).mac);
                })
                .setCancelable(false).show();
    }

    private String studentProfileToJson(com.example.approbot.data.model.StudentProfile p) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", p.id);
            obj.put("name", p.name);
            org.json.JSONArray colors = new org.json.JSONArray();
            for (String c : p.excludedColors) colors.put(c);
            obj.put("excludedColors", colors);
            if (p.backgroundSoundResName != null) obj.put("backgroundSoundResName", p.backgroundSoundResName);
            return obj.toString();
        } catch (JSONException e) {
            return null;
        }
    }
}
