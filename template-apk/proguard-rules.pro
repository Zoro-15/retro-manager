# Retropack Standalone Template ProGuard / R8 Rules

# Preserve fully-qualified GameActivity entrypoint
-keep public class com.retropack.runtime.GameActivity {
    public <init>();
    *;
}

# Preserve Native JNI Cores & Native Methods
-keep interface com.retropack.runtime.core.NativeCoreBridge { *; }
-keep class com.retropack.runtime.core.NativeCore { *; }
-keep class com.retropack.runtime.core.UniversalLibretroCore { *; }
-keep class com.retropack.runtime.factory.NativeCoreFactory { *; }
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
