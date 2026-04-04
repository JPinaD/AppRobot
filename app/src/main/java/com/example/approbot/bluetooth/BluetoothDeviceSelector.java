package com.example.approbot.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

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
    public List<BluetoothDeviceInfo> getPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<BluetoothDeviceInfo> result = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) return result;
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                result.add(new BluetoothDeviceInfo(device.getName(), device.getAddress()));
            }
        } catch (SecurityException e) {
            // Permiso BLUETOOTH_CONNECT no concedido en API 31+
        }
        return result;
    }
}
