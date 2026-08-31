package com.xrc.system.features;

import android.content.Context;
import android.os.Debug;
import android.util.Log;

import com.xrc.system.core.Constants;

public class AntiDebug {
    private static final String TAG = Constants.TAG + ":AntiDbg";
    private static AntiDebug instance;
    private final Context ctx;

    static {
        System.loadLibrary("xrc_guard");
    }

    private AntiDebug(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized AntiDebug get(Context ctx) {
        if (instance == null) {
            instance = new AntiDebug(ctx);
        }
        return instance;
    }

    public void check() {
        if (Debug.isDebuggerConnected()) {
            Log.w(TAG, "Debugger detected");
            nativeAntiDebug();
        }
        if (isBeingTraced()) {
            Log.w(TAG, "Ptrace detected");
            nativeAntiDebug();
        }
    }

    private boolean isBeingTraced() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/status"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    int tracerPid = Integer.parseInt(line.split(":")[1].trim());
                    reader.close();
                    return tracerPid != 0;
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Tracer check failed", e);
        }
        return false;
    }

    private native void nativeAntiDebug();
}
