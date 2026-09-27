# Preserve JNI boundary methods and FbneoNativeCore contracts
-keep class com.retropack.runtime.fbneo.FbneoNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
