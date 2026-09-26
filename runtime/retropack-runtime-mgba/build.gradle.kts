import com.android.build.gradle.LibraryExtension

val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
    providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
    file("${rootDir}/local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (hasAndroidSdk) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")

    configure<LibraryExtension> {
        namespace = "com.retropack.runtime.mgba"
        compileSdk = 35

        defaultConfig {
            minSdk = 26
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("consumer-rules.pro")

            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }

            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_STL=c++_static")
                }
            }
        }

        externalNativeBuild {
            cmake {
                path = file("CMakeLists.txt")
                version = "3.22.1"
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
            unitTests.all {
                it.useJUnitPlatform()
            }
        }
    }
} else {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        sourceSets.named("main") {
            java.srcDirs("src/main/kotlin", "src/stub/java")
        }
    }

    // Pin Kotlin JVM target to the Java target so host-JDK version (17/21+) never
    // causes "Inconsistent JVM-target compatibility" failures in JVM-only mode.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

dependencies {
    "api"(project(":runtime:retropack-runtime-common"))

    // Testing
    "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
