package com.example.approbot.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.example.approbot.data.model.RobotIdentity;
import com.example.approbot.util.AppConstants;

public class NsdAdvertiser {

    private static final String TAG = "NsdAdvertiser";

    private final NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private boolean registered = false;

    public NsdAdvertiser(Context context) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void start(RobotIdentity identity) {
        if (registered) return;

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(identity.name);
        serviceInfo.setServiceType(AppConstants.NSD_SERVICE_TYPE);
        serviceInfo.setPort(identity.port);
        serviceInfo.setAttribute(AppConstants.NSD_ATTR_ROBOT_ID, identity.robotId);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Registro NSD fallido: " + errorCode);
            }
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Baja NSD fallida: " + errorCode);
            }
            @Override public void onServiceRegistered(NsdServiceInfo info) {
                Log.d(TAG, "Servicio NSD registrado: " + info.getServiceName());
                registered = true;
            }
            @Override public void onServiceUnregistered(NsdServiceInfo info) {
                Log.d(TAG, "Servicio NSD dado de baja");
                registered = false;
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    public void stop() {
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Intento de baja NSD sin registro activo");
            }
            registrationListener = null;
        }
    }
}
