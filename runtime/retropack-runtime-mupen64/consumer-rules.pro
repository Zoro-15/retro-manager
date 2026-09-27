# Preserve JNI boundary methods and Mupen64NativeCore contracts
-keep class com.retropack.runtime.mupen64.Mupen64NativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
