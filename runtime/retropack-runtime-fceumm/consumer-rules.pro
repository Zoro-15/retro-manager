# Preserve JNI boundary methods and FceummNativeCore contracts
-keep class com.retropack.runtime.fceumm.FceummNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
