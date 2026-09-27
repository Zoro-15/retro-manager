# Preserve JNI boundary methods and PpssppNativeCore contracts
-keep class com.retropack.runtime.ppsspp.PpssppNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
