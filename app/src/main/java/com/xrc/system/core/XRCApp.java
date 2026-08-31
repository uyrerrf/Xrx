package com.xrc.system.core;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class XRCApp extends Application {
    private static final String TAG = Constants.TAG + ":App";
    private static XRCApp instance;
    private Thread.UncaughtExceptionHandler defaultHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        installCrashHandler();
        ConfigManager.get(this);
        Log.i(TAG, "XRC Application initialized");
    }

    public static XRCApp get() {
        return instance;
    }

    private void installCrashHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "FATAL: " + throwable.getMessage(), throwable);
            SelfHealer.get(this).triggerRestart();
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
