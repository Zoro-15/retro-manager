plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Pin Kotlin JVM target to match the Java target above. Without this, the Kotlin
// compiler defaults to the host JDK's bytecode version (e.g. 21), which breaks
// `:core:compileKotlin` on any machine running Gradle with a JDK newer than 17:
//   "Inconsistent JVM-target compatibility detected for tasks 'compileJava' (17)
//    and 'compileKotlin' (21)."
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Packaging & Transformation Stack
    api("io.github.reandroid:ARSCLib:1.3.1")
    api("com.android:zipflinger:8.2.0")
    api("com.android.tools.build:apksig:8.2.0")

    // Cryptography & X.509 Certificate Generation
    api("org.bouncycastle:bcprov-jdk18on:1.78.1")
    api("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Archive Extraction (RAR / ZIP)
    api("com.github.junrar:junrar:7.5.5")

    // Coroutines & Utilities

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
