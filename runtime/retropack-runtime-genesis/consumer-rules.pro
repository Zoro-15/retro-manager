# Preserve JNI boundary methods and GenesisNativeCore contracts
-keep class com.retropack.runtime.genesis.GenesisNativeCore {
    native <methods>;
    *;
}
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
