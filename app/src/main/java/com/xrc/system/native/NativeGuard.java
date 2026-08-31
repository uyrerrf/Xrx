package com.xrc.system.native;

public class NativeGuard {
    static {
        System.loadLibrary("xrc_guard");
    }

    public native void antiPtrace();
    public native void antiDebug();
    public native void hideProcess();
    public native void encryptStrings();
    public native boolean checkIntegrity();
    public native void obfuscateApi();
}
