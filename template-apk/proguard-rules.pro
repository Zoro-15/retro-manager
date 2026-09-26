# Retropack Standalone Template ProGuard / R8 Rules

# Preserve fully-qualified GameActivity entrypoint
-keep public class com.retropack.runtime.GameActivity {
    public <init>();
    *;
}

# Preserve Native JNI Cores & Native Methods
-keep interface com.retropack.runtime.core.NativeCoreBridge { *; }
-keep class com.retropack.runtime.core.NativeCore { *; }
-keep class com.retropack.runtime.factory.NativeCoreFactory { *; }
-keep class com.retropack.runtime.mgba.** { *; }
-keep class com.retropack.runtime.snes.** { *; }
-keep class com.retropack.runtime.genesis.** { *; }
-keep class com.retropack.runtime.fceumm.** { *; }
-keep class com.retropack.runtime.pce.** { *; }
-keep class com.retropack.runtime.fbneo.** { *; }
-keep class com.retropack.runtime.pcsx.** { *; }
-keep class com.retropack.runtime.mupen64.** { *; }
-keep class com.retropack.runtime.ppsspp.** { *; }
-keep class com.retropack.runtime.melonds.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Preserve Runtime Configuration model
-keep class com.retropack.runtime.host.RuntimeConfig { *; }
-keep class com.retropack.runtime.host.GameConfig { *; }
-keep class com.retropack.runtime.host.EngineConfig { *; }
-keep class com.retropack.runtime.host.ControlsConfig { *; }
-keep class com.retropack.runtime.host.StorageConfig { *; }
-keep class com.retropack.runtime.host.ProvenanceConfig { *; }
