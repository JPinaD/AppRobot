package com.example.approbot.network;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import com.example.approbot.util.AppConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emite ROBOT_STATUS periódicamente (cada 5s) y bajo demanda.
 */
public class RobotStatusReporter {

    private static final String TAG = "RobotStatusReporter";
    private static final int INTERVAL_SECONDS = 5;

    private final Context appContext;
    private final TcpServer tcpServer;
    private ActivityStatusProvider statusProvider;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> task;

    public RobotStatusReporter(Context context, TcpServer tcpServer) {
        this.appContext = context.getApplicationContext();
        this.tcpServer  = tcpServer;
    }

    public void setStatusProvider(ActivityStatusProvider provider) {
        this.statusProvider = provider;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = scheduler.scheduleAtFixedRate(this::sendStatus, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) { task.cancel(false); task = null; }
    }

    /** Envía un ROBOT_STATUS inmediato (p.ej. tras cambio de estado). */
    public void sendImmediate() {
        scheduler.execute(this::sendStatus);
    }

    private void sendStatus() {
        try {
            int battery = readBatteryPct();
            String activityId = statusProvider != null ? statusProvider.getActivityId() : null;
            Integer progressPct = statusProvider != null ? statusProvider.getProgressPct() : null;

            JSONObject payload = new JSONObject();
            payload.put("batteryPct", battery);
            if (activityId != null) payload.put("activityId", activityId);
            else payload.put("activityId", JSONObject.NULL);
            if (progressPct != null) payload.put("progressPct", progressPct);
            else payload.put("progressPct", JSONObject.NULL);

            JSONObject msg = new JSONObject();
            msg.put("type", AppConstants.MSG_ROBOT_STATUS);
            msg.put("payload", payload.toString());
            tcpServer.sendToClient(msg.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Error construyendo ROBOT_STATUS", e);
        }
    }

    private int readBatteryPct() {
        Intent intent = appContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return 0;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale > 0 ? (level * 100 / scale) : 0;
    }
}
