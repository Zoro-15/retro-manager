# Keep JNI native bridge methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.retropack.runtime.core.UniversalLibretroCore { *; }
-keep class com.retropack.runtime.core.NativeCoreBridge { *; }
