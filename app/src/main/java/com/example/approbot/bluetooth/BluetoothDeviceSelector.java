package com.example.approbot.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Proporciona la lista de dispositivos Bluetooth ya emparejados en el sistema. */
public class BluetoothDeviceSelector {

    public static class BluetoothDeviceInfo {
        public final String name;
        public final String mac;

        public BluetoothDeviceInfo(String name, String mac) {
            this.name = name;
            this.mac = mac;
        }
    }

    /** Devuelve lista vacía si Bluetooth no está disponible, desactivado o sin permiso. */
    public List<BluetoothDeviceInfo> getPairedDevices(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<BluetoothDeviceInfo> result = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) return result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            return result;
        }
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                result.add(new BluetoothDeviceInfo(device.getName(), device.getAddress()));
            }
        } catch (SecurityException e) {
            // no debería llegar aquí tras la comprobación anterior
        }
        return result;
    }
}
