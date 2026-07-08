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
import android.widget.EditText;
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
import com.example.approbot.data.repository.ActiveSessionRepository;
import com.example.approbot.data.repository.RobotIdentityRepository;
import com.example.approbot.network.RobotNetworkService;
import com.example.approbot.network.RobotStatusReporter;
import com.example.approbot.network.SessionNetworkHolder;
import com.example.approbot.network.TcpServer;
import com.example.approbot.ui.activities.CalmActivity;
import com.example.approbot.ui.activities.EmotionActivity;
import com.example.approbot.ui.activities.SequenceActivity;
import com.example.approbot.ui.activities.SocialActivity;
import com.example.approbot.ui.activities.TurnsActivity;
import com.example.approbot.kiosk.KioskModeManager;
import com.example.approbot.ui.communicator.CommunicatorActivity;
import com.example.approbot.ui.pictogram.PictogramActivity;
import com.example.approbot.util.AppConstants;
import com.example.approbot.util.TtsHelper;

import org.json.JSONArray;
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
    private ActiveSessionRepository activeSessionRepository;
    private BluetoothRobotManager bluetoothRobotManager;

    private RobotNetworkService networkService;
    private boolean serviceBound = false;
    private RobotStatusReporter statusReporter;

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
            statusReporter = new RobotStatusReporter(
                    WaitingSessionActivity.this, networkService.getTcpServer());
            statusReporter.start();
            SessionNetworkHolder.setStatusReporter(statusReporter);
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
        identityRepository      = new RobotIdentityRepository(this);
        activeSessionRepository = new ActiveSessionRepository(this);
        checkInterruptedSession();

        bluetoothRobotManager = new BluetoothRobotManager();
        bluetoothRobotManager.setListener(this);

        findViewById(R.id.back_button).setOnClickListener(v -> attemptExit());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showRobotNameDialog());
        ((TextView) findViewById(R.id.tvSelectedProfileName)).setText(
                getIntent().getStringExtra("profile_name"));
        ((TextView) findViewById(R.id.tvSelectedProfileDescription)).setText(
                getIntent().getStringExtra("profile_description"));

        KioskModeManager.enter(this);
        TtsHelper.getInstance().init(this);
        Intent serviceIntent = new Intent(this, RobotNetworkService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        tvNetworkStatus.setText(getString(R.string.network_status_waiting));
        if (hasBluetoothPermission()) startBluetoothConnection();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Do NOT stop statusReporter here — activities run on top of this one
        // and the reporter must keep sending ROBOT_STATUS to prevent TCP timeout.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    // --- Configuracion de identidad ---

    private void showRobotNameDialog() {
        String currentName = identityRepository.getRobotName("Robot-1");
        EditText input = new EditText(this);
        input.setText(currentName);
        input.setHint(R.string.settings_robot_name_hint);
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_robot_name_title)
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    identityRepository.saveRobotName(newName);
                    if (serviceBound) {
                        networkService.restartNsd(newName, identityRepository.getPort());
                    }
                    Toast.makeText(this,
                            getString(R.string.settings_robot_name_saved, newName),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
                case AppConstants.MSG_SESSION_PAUSE:
                    handleSessionPause(payloadStr, out);
                    break;
                case AppConstants.MSG_SESSION_RESUME:
                    handleSessionResume(payloadStr, out);
                    break;
                case AppConstants.MSG_ACTIVITY_START:
                    handleActivityStart(payloadStr);
                    break;
                case AppConstants.MSG_ROBOT_FEEDBACK:
                    handleRobotFeedback(payloadStr);
                    break;
                case AppConstants.MSG_TURN_SIGNAL:
                    handleTurnSignal(payloadStr);
                    break;
                case AppConstants.MSG_TERAPEUTA_PICTOGRAM_MESSAGE:
                    handleTerapeutaPictogramMessage(payloadStr);
                    break;
                case AppConstants.MSG_COMMUNICATOR_RESPONSE:
                    handleCommunicatorResponse(payloadStr);
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
            Log.w(TAG, "SESSION_START con payload invalido, ignorado");
            return;
        }

        String activityId = config.activityId;

        if (!isKnownActivity(activityId)) {
            Log.w(TAG, "SESSION_START con activityId desconocido: " + activityId);
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

        activeSessionRepository.save(config);

        runOnUiThread(() -> {
            Intent intent = buildActivityIntent(activityId, config);
            if (intent != null) startActivity(intent);
        });
    }

    private boolean isKnownActivity(String activityId) {
        switch (activityId) {
            case AppConstants.ACTIVITY_COMMUNICATOR:
            case AppConstants.ACTIVITY_PICTOGRAM:
            case AppConstants.ACTIVITY_PICTOGRAM_LEGACY:
            case AppConstants.ACTIVITY_EMOTION:
            case AppConstants.ACTIVITY_SOCIAL:
            case AppConstants.ACTIVITY_SEQUENCE:
            case AppConstants.ACTIVITY_CALM:
            case AppConstants.ACTIVITY_TURNS:
                return true;
            default:
                return false;
        }
    }

    private Intent buildActivityIntent(String activityId, SessionConfig config) {
        String profileJson = config.studentProfile != null
                ? studentProfileToJson(config.studentProfile) : null;

        switch (activityId) {
            case AppConstants.ACTIVITY_COMMUNICATOR:
            case AppConstants.ACTIVITY_PICTOGRAM:
            case AppConstants.ACTIVITY_PICTOGRAM_LEGACY: {
                Intent intent = new Intent(this, CommunicatorActivity.class);
                intent.putExtra(CommunicatorActivity.EXTRA_SESSION_ID, config.sessionId);
                if (profileJson != null) intent.putExtra(CommunicatorActivity.EXTRA_STUDENT_PROFILE, profileJson);
                return intent;
            }
            case AppConstants.ACTIVITY_EMOTION: {
                Intent intent = new Intent(this, EmotionActivity.class);
                intent.putExtra(EmotionActivity.EXTRA_SESSION_ID, config.sessionId);
                intent.putStringArrayListExtra(EmotionActivity.EXTRA_ITEMS, new ArrayList<>(config.activityItems));
                intent.putExtra(EmotionActivity.EXTRA_STEPS, config.sequenceLength > 0 ? config.sequenceLength : 5);
                if (profileJson != null) intent.putExtra(EmotionActivity.EXTRA_STUDENT_PROFILE, profileJson);
                return intent;
            }
            case AppConstants.ACTIVITY_SOCIAL: {
                Intent intent = new Intent(this, SocialActivity.class);
                intent.putExtra(SocialActivity.EXTRA_SESSION_ID, config.sessionId);
                intent.putExtra(SocialActivity.EXTRA_SESSION_CONFIG_JSON, config.toJson());
                if (profileJson != null) intent.putExtra(SocialActivity.EXTRA_STUDENT_PROFILE, profileJson);
                return intent;
            }
            case AppConstants.ACTIVITY_SEQUENCE: {
                Intent intent = new Intent(this, SequenceActivity.class);
                intent.putExtra(SequenceActivity.EXTRA_SESSION_ID, config.sessionId);
                intent.putExtra(SequenceActivity.EXTRA_SEQUENCE_LENGTH,
                        config.sequenceLength > 0 ? config.sequenceLength : 2);
                intent.putStringArrayListExtra(SequenceActivity.EXTRA_ITEMS, new ArrayList<>(config.activityItems));
                if (profileJson != null) intent.putExtra(SequenceActivity.EXTRA_STUDENT_PROFILE, profileJson);
                return intent;
            }
            case AppConstants.ACTIVITY_CALM: {
                Intent intent = new Intent(this, CalmActivity.class);
                intent.putExtra(CalmActivity.EXTRA_SESSION_ID, config.sessionId);
                return intent;
            }
            case AppConstants.ACTIVITY_TURNS: {
                Intent intent = new Intent(this, TurnsActivity.class);
                intent.putExtra(TurnsActivity.EXTRA_SESSION_ID, config.sessionId);
                intent.putExtra(TurnsActivity.EXTRA_STEPS, config.sequenceLength > 0 ? config.sequenceLength : 5);
                if (profileJson != null) intent.putExtra(TurnsActivity.EXTRA_STUDENT_PROFILE, profileJson);
                return intent;
            }
            default:
                return null;
        }
    }

    private void handleTurnSignal(String payloadStr) {
        if (payloadStr == null) return;
        runOnUiThread(() -> {
            Intent broadcast = new Intent(AppConstants.ACTION_TURN_SIGNAL);
            broadcast.putExtra("payload", payloadStr);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        });
    }

    private void handleTerapeutaPictogramMessage(String payloadStr) {
        if (payloadStr == null) return;
        runOnUiThread(() -> {
            Intent broadcast = new Intent(AppConstants.ACTION_TERAPEUTA_PICTOGRAM);
            broadcast.putExtra("payload", payloadStr);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        });
    }

    private void handleCommunicatorResponse(String payloadStr) {
        if (payloadStr == null) return;
        runOnUiThread(() -> {
            Intent broadcast = new Intent(AppConstants.ACTION_COMMUNICATOR_RESPONSE);
            broadcast.putExtra("payload", payloadStr);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        });
    }

    private void handleSessionEnd(String payloadStr, java.io.PrintWriter out) {
        String sessionId = "";
        try {
            if (payloadStr != null) sessionId = new JSONObject(payloadStr).optString("sessionId", "");
        } catch (JSONException ignored) {}

        runOnUiThread(() -> startActivity(
                new Intent(this, com.example.approbot.ui.sessionend.SessionEndActivity.class)));

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(AppConstants.ACTION_SESSION_END));
        activeSessionRepository.clear();

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

    private void handleSessionPause(String payloadStr, java.io.PrintWriter out) {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(AppConstants.ACTION_SESSION_PAUSE));

        String sessionId = "";
        try {
            if (payloadStr != null) sessionId = new JSONObject(payloadStr).optString("sessionId", "");
        } catch (JSONException ignored) {}
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("robotId", identityRepository.getRobotName("Robot-1"));
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_SESSION_PAUSED);
            msg.put("payload", payload.toString());
            out.println(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo SESSION_PAUSED", e);
        }
    }

    private void handleSessionResume(String payloadStr, java.io.PrintWriter out) {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(AppConstants.ACTION_SESSION_RESUME));

        String sessionId = "";
        try {
            if (payloadStr != null) sessionId = new JSONObject(payloadStr).optString("sessionId", "");
        } catch (JSONException ignored) {}
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("robotId", identityRepository.getRobotName("Robot-1"));
            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_SESSION_RESUMED);
            msg.put("payload", payload.toString());
            out.println(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error construyendo SESSION_RESUMED", e);
        }
    }

    private void handleActivityStart(String payloadStr) {
        if (payloadStr == null) return;
        try {
            JSONObject payload = new JSONObject(payloadStr);
            JSONArray pics = payload.optJSONArray("pictograms");
            if (pics == null || pics.length() == 0) return;
            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < pics.length(); i++) list.add(pics.getString(i));
            JSONObject profileObj = payload.optJSONObject("studentProfile");
            final String profileJson = profileObj != null ? profileObj.toString() : null;
            runOnUiThread(() -> {
                // Legacy ACTIVITY_START: redirect to CommunicatorActivity
                Intent intent = new Intent(this, CommunicatorActivity.class);
                intent.putExtra(CommunicatorActivity.EXTRA_SESSION_ID, "");
                if (profileJson != null) intent.putExtra(CommunicatorActivity.EXTRA_STUDENT_PROFILE, profileJson);
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

    private void checkInterruptedSession() {
        SessionConfig interrupted = activeSessionRepository.load();
        if (interrupted == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Sesion interrumpida")
                .setMessage("Habia una sesion activa cuando la app se cerro.\nDescartar y continuar?")
                .setPositiveButton("Descartar", (d, w) -> activeSessionRepository.clear())
                .setNegativeButton("Esperar reconexion", null)
                .setCancelable(false)
                .show();
    }

    // --- BluetoothRobotListener ---

    @Override
    public void onConnected() {
        Log.i(TAG, "Conectado al robot fisico via Bluetooth");
        runOnUiThread(() -> tvBluetoothStatus.setText(getString(R.string.bt_status_connecting)));
        bluetoothRobotManager.send(new RobotMessage(AppConstants.MSG_PING, null));
    }

    @Override
    public void onMessageReceived(RobotMessage m) {
        Log.d(TAG, "BT: " + m.type);
        // All BT messages arrive on the BT background thread.
        // Dispatch broadcasts and UI work safely on the main thread to avoid
        // ConcurrentModificationException in LocalBroadcastManager and
        // CalledFromWrongThreadException on view updates.
        try {
            switch (m.type) {
                case AppConstants.MSG_PONG:
                    runOnUiThread(() -> tvBluetoothStatus.setText(getString(R.string.bt_status_verified)));
                    break;
                case AppConstants.MSG_BATTERY_STATUS:
                    handleBatteryStatus(m.payload);
                    break;
                case AppConstants.MSG_TILT_ALERT:
                    handleTiltAlert();
                    break;
                case AppConstants.MSG_CELEBRATE_DONE:
                    runOnUiThread(() -> LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(AppConstants.ACTION_CELEBRATE_DONE)));
                    break;
                case AppConstants.MSG_DENY_DONE:
                    // DEPRECATED: Servo no longer used. DENY_DONE ignored.
                    Log.d(TAG, "DENY_DONE received but ignored (servo disabled)");
                    break;
                case AppConstants.MSG_MOVE_DONE:
                    runOnUiThread(() -> LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(AppConstants.ACTION_MOVE_DONE)));
                    break;
                case AppConstants.MSG_DANCE_DONE:
                    runOnUiThread(() -> LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(AppConstants.ACTION_DANCE_DONE)));
                    break;
                case AppConstants.MSG_BLOCKED:
                    runOnUiThread(() -> LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(AppConstants.ACTION_BLOCKED)));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error procesando mensaje BT: " + m.type, e);
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
        Log.i(TAG, "Desconectado del robot fisico");
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
            Log.w(TAG, "BATTERY_STATUS con valor no numerico ignorado: " + payload);
        }
    }

    /**
     * El robot ha detectado un vuelco (inclinación > 45°). Acciones:
     * 1. Enviar STOP al Arduino como refuerzo de seguridad.
     * 2. Reenviar TILT_ALERT al terapeuta vía TCP con el robotId.
     * 3. Notificar a la Activity de actividad activa vía LocalBroadcast (main thread).
     */
    private void handleTiltAlert() {
        Log.w(TAG, "TILT_ALERT recibido: robot inclinado, deteniendo motores");

        // 1. Enviar STOP al Arduino como refuerzo (safe from any thread)
        if (bluetoothRobotManager != null) {
            bluetoothRobotManager.send(new RobotMessage(AppConstants.MSG_STOP, null));
        }

        // 2. Reenviar al terapeuta vía TCP (safe from any thread via executor)
        if (serviceBound && networkService != null) {
            try {
                TcpServer tcp = networkService.getTcpServer();
                if (tcp != null) {
                    String robotId = identityRepository.getRobotName("Robot-1");
                    JSONObject payload = new JSONObject();
                    payload.put("robotId", robotId);
                    JSONObject msg = new JSONObject();
                    msg.put("type", AppConstants.MSG_TILT_ALERT);
                    msg.put("payload", payload.toString());
                    tcp.sendToClient(msg.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error construyendo TILT_ALERT para TCP", e);
            }
        }

        // 3. Notificar a la Activity activa vía LocalBroadcast (MUST be main thread)
        runOnUiThread(() -> LocalBroadcastManager.getInstance(WaitingSessionActivity.this)
                .sendBroadcast(new Intent(AppConstants.ACTION_TILT_ALERT)));
    }

    private void startBluetoothConnection() {
        if (bluetoothRobotManager.isConnected()) return;
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
                .setTitle("Selecciona el modulo HC-05")
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
            JSONArray colors = new JSONArray();
            for (String c : p.excludedColors) colors.put(c);
            obj.put("excludedColors", colors);
            if (p.backgroundSoundResName != null) obj.put("backgroundSoundResName", p.backgroundSoundResName);
            obj.put("calmType", p.calmType);
            return obj.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        attemptExit();
    }

    private void attemptExit() {
        if (activeSessionRepository.load() != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Salir?")
                    .setMessage("Hay una sesion activa. Seguro que quieres salir?")
                    .setPositiveButton("Salir", (d, w) -> doExit())
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            doExit();
        }
    }

    private void doExit() {
        KioskModeManager.exit(this);
        TtsHelper.getInstance().shutdown();
        bluetoothRobotManager.disconnect();
        stopService(new Intent(this, RobotNetworkService.class));
        finish();
    }
}
