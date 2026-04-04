package com.example.approbot.ui.waiting;

import android.Manifest;
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
import com.example.approbot.data.repository.RobotIdentityRepository;
import com.example.approbot.network.NsdAdvertiser;
import com.example.approbot.network.TcpServer;
import com.example.approbot.util.AppConstants;

import java.util.List;

public class WaitingSessionActivity extends AppCompatActivity implements BluetoothRobotListener {

    private static final String TAG = "WaitingSessionActivity";

    private NsdAdvertiser nsdAdvertiser;
    private TcpServer tcpServer;
    private TextView tvNetworkStatus;
    private RobotIdentityRepository identityRepository;
    private BluetoothRobotManager bluetoothRobotManager;

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

        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);

        identityRepository = new RobotIdentityRepository(this);

        String robotName = identityRepository.getRobotName("Robot-1");
        int port = identityRepository.getPort();

        nsdAdvertiser = new NsdAdvertiser(this);
        tcpServer = new TcpServer(port, (message, out) -> {
            if (AppConstants.MSG_PING.equals(message)) {
                out.println(AppConstants.MSG_PONG);
            }
            runOnUiThread(() -> tvNetworkStatus.setText(
                    getString(R.string.network_status_connected)));
        });

        bluetoothRobotManager = new BluetoothRobotManager();
        bluetoothRobotManager.setListener(this);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        TextView tvSelectedProfileName = findViewById(R.id.tvSelectedProfileName);
        TextView tvSelectedProfileDescription = findViewById(R.id.tvSelectedProfileDescription);
        tvSelectedProfileName.setText(getIntent().getStringExtra("profile_name"));
        tvSelectedProfileDescription.setText(getIntent().getStringExtra("profile_description"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        tvNetworkStatus.setText(getString(R.string.network_status_waiting));

        String robotName = identityRepository.getRobotName("Robot-1");
        int port = identityRepository.getPort();

        tcpServer.start();
        nsdAdvertiser.start(robotName, port);

        if (hasBluetoothPermission()) {
            startBluetoothConnection();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

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
        return true; // API < 31: permiso normal, no requiere solicitud en runtime
    }

    @Override
    protected void onStop() {
        super.onStop();
        nsdAdvertiser.stop();
        tcpServer.stop();
        bluetoothRobotManager.disconnect();
    }

    // --- BluetoothRobotListener ---

    @Override
    public void onConnected() {
        Log.i(TAG, "Conectado al robot físico vía Bluetooth");
    }

    @Override
    public void onMessageReceived(RobotMessage message) {
        Log.d(TAG, "Mensaje recibido del robot: type=" + message.type + " payload=" + message.payload);
    }

    @Override
    public void onConnectionError(String reason) {
        runOnUiThread(() ->
                Toast.makeText(this, "Error Bluetooth: " + reason, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Desconectado del robot físico");
    }

    // --- privado ---

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
