val hasAndroidSdk = providers.environmentVariable("ANDROID_HOME").isPresent ||
    providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
    file("${rootDir}/local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (hasAndroidSdk) {
    apply(plugin = "com.android.application")
    apply(plugin = "org.jetbrains.kotlin.android")

    extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
        namespace = "com.retropack.runtime"
        compileSdk = 35

        defaultConfig {
            applicationId = "com.retropack.runtime"
            minSdk = 26
            targetSdk = 35
            versionCode = 1
            versionName = "0.1.0"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            jniLibs {
                // Enforce uncompressed native libraries for Android 15/16 16 KB page-size compliance
                useLegacyPackaging = false
            }
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
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
            java.srcDirs(
                "src/main/kotlin",
                "${project(":runtime:retropack-runtime-mgba").projectDir}/src/stub/java"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

dependencies {
    "implementation"(project(":runtime:retropack-runtime-mgba"))

    // Testing
    "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
