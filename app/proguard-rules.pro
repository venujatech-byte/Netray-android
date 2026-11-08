# Native method names are exported by JNI using the fully-qualified class/method names.
# JNI exports are looked up by class and native method name from Rust.
-keep class com.venujatech.netray.profiles.NativeBackend { native <methods>; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# libXray's AAR contributes its own consumer rules for the reflective Go bridge.
