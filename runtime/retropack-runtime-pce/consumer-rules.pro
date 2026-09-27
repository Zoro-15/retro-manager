# Preserve JNI boundary methods and PceNativeCore contracts
-keep class com.retropack.runtime.pce.PceNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
