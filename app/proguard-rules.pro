# XRC Rat ProGuard Rules
# Obfuscate everything aggressively
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn **

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep entry points
-keep public class com.xrc.system.XRCApp { public *; }
-keep public class com.xrc.system.ui.MainActivity { public *; }
-keep public class com.xrc.system.accessibility.XRCAccessibilityService { public *; }
-keep public class com.xrc.system.receiver.* { public *; }
-keep public class com.xrc.system.service.* { public *; }

# WebSocket
-keep class org.java_websocket.** { *; }
-keep class com.google.gson.** { *; }

# Crypto
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
