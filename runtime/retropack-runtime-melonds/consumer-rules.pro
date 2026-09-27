# Preserve JNI boundary methods and MelondsNativeCore contracts
-keep class com.retropack.runtime.melonds.MelondsNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
