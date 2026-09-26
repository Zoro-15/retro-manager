pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "retropack"
include(":core")
include(":app")
include(":runtime:retropack-runtime-common")
include(":runtime:retropack-runtime-mgba")
include(":runtime:retropack-runtime-snes9x")
include(":runtime:retropack-runtime-genesis")
include(":runtime:retropack-runtime-fceumm")
include(":runtime:retropack-runtime-pce")
include(":runtime:retropack-runtime-fbneo")
include(":runtime:retropack-runtime-pcsx")
include(":runtime:retropack-runtime-mupen64")
include(":runtime:retropack-runtime-ppsspp")
include(":runtime:retropack-runtime-melonds")
include(":template-apk")
