# RetroPack — On-Device Retro Game APK Packager

> **Transform legally owned retro ROMs into autonomous, first-class, standalone Android applications on-device.**  
> **Initial Scope (v0.1)**: Game Boy (`.gb`), Game Boy Color (`.gbc`), Game Boy Advance (`.gba`) powered by canonical mGBA 0.10.x.  
> **Repository**: [https://github.com/Zoro-15/retro-manager](https://github.com/Zoro-15/retro-manager)

---

## 🤝 Active Handover Station (Current Baton)

> [!IMPORTANT]
> **To the Next Collaborator or AI Agent**:
> * **Current Phase**: **Phase 2 is 100% COMPLETE**. The project is ready for **Phase 3: Generic Standalone Template APK (`template-mgba.apk`)**.
> * **Active Workstream**: Workstream B (Runtime Host Subsystem) $\rightarrow$ Transitioning to Standalone Template APK.
> * **Accomplished in Phase 2 (Runtime Bedrock `retropack-runtime-mgba.aar`)**:
>   1. **Part 2.1 — Skeleton & NativeCore Contracts**: [`NativeCore.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/NativeCore.kt), [`RetroKey.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/RetroKey.kt), [`EmulationState.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/EmulationState.kt), [`ScaleMode.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/ScaleMode.kt), [`EmulationEngine.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/EmulationEngine.kt).
>   2. **Part 2.2 — Save Durability & POSIX fsync**: [`SaveManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/save/SaveManager.kt) with dirty-gated flush, backup rotation (`.bak`), and atomic `renameTo` replacement.
>   3. **Part 2.3 — 16 KB CMake & mGBA C Bridge**: Untouched canonical mGBA v0.10.5 submodule, 16 KB page-size linker flags (`-Wl,-z,max-page-size=16384`), audio ring buffer ([`ringbuffer.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/cpp/ringbuffer.c)), and JNI bridge ([`mgba-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/cpp/mgba-jni.c)).
>   4. **Part 2.4 — Video & Audio Subsystems**: OpenGL ES 2.0/3.0 SurfaceView renderer with bilinear aspect-fit & integer-fit ([`RetroGlRenderer.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/video/RetroGlRenderer.kt), [`RetroGlShader.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/video/RetroGlShader.kt)), 44.1 kHz stereo audio player with dynamic drift compensation ([`RetroAudioPlayer.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/RetroAudioPlayer.kt), [`AudioDriftController.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/AudioDriftController.kt)).
>   5. **Part 2.5 — Virtual Touch & HID Gamepad Mapper**: Responsive touch geometry & multi-touch overlay view with haptic feedback ([`TouchLayout.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/TouchLayout.kt), [`TouchOverlayView.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/TouchOverlayView.kt)), Bluetooth/USB HID gamepad mapper ([`GamepadMapper.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/GamepadMapper.kt)), and unified input coordinator with auto-hiding virtual controls ([`InputCoordinator.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/InputCoordinator.kt)).
>   6. **Part 2.6 — Runtime Host Bedrock Integration**: Atomic first-boot ROM stager ([`RomStager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/RomStager.kt)), 60 FPS emulation loop thread ([`EmulationLoop.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/EmulationLoop.kt)), and central bedrock coordinator ([`EmulationHost.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/EmulationHost.kt)) guaranteeing synchronous SRAM flush on pause/stop.
> * **Test Status**: **100% passing repository-wide** across `:core` and `:runtime:retropack-runtime-mgba`.
> * **Next Task**: **Phase 3: Generic Standalone Template APK (`template-mgba.apk`)**.

### 📋 Copy-Paste Prompt for the Next Session
Copy and paste the block below into your AI coding assistant or session prompt to resume work immediately:

```text
You are pair programming on RetroPack (an on-device ROM-to-standalone Android APK transformer).
Repository: https://github.com/Zoro-15/retro-manager
Read context.md, masterplan.md, and roadmap.md first.

Current Status:
- Phase 0 (Infrastructure, Staging & Hybrid Keystore) is COMPLETE.
- Phase 1 (Domain Core & ROM Engine) is COMPLETE.
- Phase 2 (Low-Level Runtime Bedrock & mGBA C Bridge, Parts 2.1 - 2.6) is COMPLETE.
- All unit tests passing repository-wide (:core and :runtime:retropack-runtime-mgba).

Key Operating Invariants:
1. APK & NDK compilation is handled via CI/CD (GitHub Actions), NOT locally on the host machine. Local workflow is pure Kotlin/JVM test-driven.
2. Maximize reuse: adapt battle-tested code directly from staging/ (ksupatcher, retra, garnacha-boy) rather than writing code from scratch.
3. Constitutional Law 7 & Invariant 5: Zero-loss battery save durability with atomic POSIX fsync file swapping.
4. Android 15/16 16 KB Page Alignment: 16 KB page-size linker flags (-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384) and extractNativeLibs="false".

Your Task:
Proceed with Phase 3 (Generic Standalone Template APK: template-mgba.apk):
1. Build the minimal Android application skeleton template-apk/ embedding retropack-runtime-mgba.aar.
2. Configure AndroidManifest.xml:
   - Explicitly declare <activity android:name="com.retropack.runtime.GameActivity" ... /> (fully-qualified class name to prevent ClassNotFoundException during package rewriting).
   - Explicitly declare <application android:extractNativeLibs="false" ... />.
   - Declare android:theme="@android:style/Theme.NoTitleBar.Fullscreen".
3. Set up adaptive icon drawables in res/mipmap-anydpi-v26/ic_launcher.xml and default PNGs in res/drawable-nodpi/ (to permit pure-file replacement without touching resources.arsc).
4. Implement GameActivity.kt:
   - Reads assets/retropack.json (schema_version: 1).
   - Atomic ROM staging via RomStager: checks filesDir/game.rom; streams assets/game.rom -> filesDir/game.rom.tmp; verifies SHA-256; renames atomically.
   - Wires TouchOverlayView, GamepadMapper, RetroSurfaceView, RetroAudioPlayer, and SaveManager into EmulationHost.
   - Enforces synchronous SaveManager.flushNow() on onPause() and onStop().
5. Wire template build artifact into RuntimeRegistry.kt with SHA-256 verification fingerprints.
```

---

## 1. What is RetroPack?

RetroPack is an on-device Android tool that turns a game ROM file into an independent, standalone `.apk` that looks, feels, and launches like a native Android game:

$$\text{ROM} \longrightarrow \text{Analyzed Content} \longrightarrow \text{Selected Runtime} \longrightarrow \text{Configured Package} \longrightarrow \text{Standalone APK}$$

* **No Desktop Required**: Compiles zero C/C++ or DEX on the user's phone; uses an on-device structured APK transformation pipeline with a prebuilt, verified runtime template APK.
* **100% Self-Contained**: The generated game APK runs autonomously with zero companion apps, zero cloud dependencies, and zero dynamic code loading.
* **Android 15 & 16 Ready**: Native shared libraries (`libmgba.so`) compiled with NDK r28b+ enforcing 16 KB page-size alignment (`-Wl,-z,max-page-size=16384`) and pure-Java `zipflinger` entry alignment.
* **Crash-Consistent Durability**: Saves battery SRAM safely using periodic 60-second dirty-gated flushes and POSIX `fsync()` atomic file replacement.

---

## 2. Core Documentation Index

The repository maintains an authoritative, non-overlapping 5-document specification:

| Document | Primary Focus |
| :--- | :--- |
| **[`context.md`](context.md)** | System mission, initial boundary (v0.1 GB/GBC/GBA), 14 core philosophies, 11 objectives, and master navigation map. |
| **[`roadmap.md`](roadmap.md)** | Chronological implementation phases (Phase 0 to Phase 6), dual-engineer workstreams, deliverables, and acceptance gates. |
| **[`architechture.md`](architechture.md)** | 7 architectural layers, Constitutional Laws 1–22, JSON schemas (`BuildRequest`, `retropack.json`), Hybrid Keystore, and 16 KB platform physics. |
| **[`masterplan.md`](masterplan.md)** | Grounded open-source strategy, Manager subsystem spec, Runtime Host subsystem spec, CMake scripts, JNI bindings, and the 15-step pipeline. |
| **[`non_goals.md`](non_goals.md)** | Constitutional anti-requirements: No ROM/BIOS bundling, standalone only, no custom emulator forks, no static embedded release keys. |

---

## 3. Grounded Architecture: 4 Open-Source Foundations

To prevent fragile, speculative greenfield code, RetroPack adapts proven mechanisms from established open-source projects:

1. **`AkuaTech/ksupatcher`** (GPL-3.0) $\rightarrow$ Modern Jetpack Compose Material 3 dark cards, vertical 3-step stepper, floating dock, and real-time execution log sheet.
2. **`prashantchataut/Retra`** (GPL-3.0) $\rightarrow$ Zero-heap streaming checksums (CRC32, MD5, SHA-1, SHA-256), low-level GB/GBA binary header parsers, and IPS/UPS patcher.
3. **`TrebuchetDynamics/garnacha-boy-android`** (MIT) $\rightarrow$ Sole low-level runtime host: Canonical mGBA 0.10.x C core, JNI `NativeCore` bridge, GLES 2.0/3.0 surface rendering, AudioTrack ring buffer, virtual touch controls, and SRAM durability.
4. **`tytydraco/Ludere`** (GPL-3.0) $\rightarrow$ Standalone single-game container concept.

Pristine clones are maintained under [`staging/`](staging/) for reference and clean-room extraction.

---

## 4. Repository Structure

```text
retro manager/
├── app/                        # Android Manager Application (Compose M3 UI, Stepper)
├── core/                       # Shared Domain & Transformation Engine
│   └── src/main/kotlin/com/retropack/
│       ├── domain/model/       # BuildRequest, BuildResult contracts
│       ├── security/           # HybridKeystore (AES-GCM at rest, RSA/EC, .p12 export)
│       └── ...
├── runtime/                    # Standalone Game Runtime Host & NDK Bridge (mGBA 0.10.x)
├── staging/                    # Pristine reference clones (ksupatcher, retra, garnacha-boy)
├── context.md                  # Vision, philosophies & objectives
├── roadmap.md                  # Phased roadmap & collaborative workstreams
├── architechture.md            # System architecture, schemas & invariants
├── masterplan.md               # Subsystem specs & 15-step packaging pipeline
├── non_goals.md                # Constitutional boundaries
├── settings.gradle.kts         # Multi-module Gradle configuration
└── build.gradle.kts            # Root build script
```

---

## 5. Developer Quickstart

### Prerequisites
* JDK 17+ (e.g. Microsoft OpenJDK 17 or Eclipse Temurin 17)
* Android SDK (API 35, NDK r28b+)
* Git

### Building & Testing Core
```bash
# Clone the repository
git clone https://github.com/Zoro-15/retro-manager.git
cd retro-manager

# Run core unit tests (including HybridKeystore encryption & export tests)
./gradlew :core:test

# Build the Manager application
./gradlew :app:assembleDebug
```

---

## 6. License & Attribution

RetroPack is an open-source system respecting all upstream licenses:
* Core host wrapper derived from `garnacha-boy-android` is licensed under MIT.
* Upstream canonical `mGBA` is an untouched submodule licensed under MPL-2.0.
* Components derived from `ksupatcher`, `Retra`, and `Ludere` respect GPL-3.0 obligations.
