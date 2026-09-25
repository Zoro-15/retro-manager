# Preserve JNI boundary methods and NativeCore contracts
-keep class com.retropack.runtime.core.NativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.RetroKey { *; }
-keep class com.retropack.runtime.core.EmulationState { *; }
-keep class com.retropack.runtime.core.ScaleMode { *; }
-keep interface com.retropack.runtime.core.EmulationEngine { *; }
-keep class com.retropack.runtime.core.NativeEmulationEngine { *; }
