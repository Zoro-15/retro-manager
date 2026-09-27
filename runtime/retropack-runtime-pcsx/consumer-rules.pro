# Preserve JNI boundary methods and PcsxNativeCore contracts
-keep class com.retropack.runtime.pcsx.PcsxNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
