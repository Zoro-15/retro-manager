# Preserve JNI boundary methods and Snes9xNativeCore contracts
-keep class com.retropack.runtime.snes.Snes9xNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
