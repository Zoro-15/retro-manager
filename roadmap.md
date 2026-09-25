# RetroPack — Implementation Roadmap (`roadmap.md`)

> **Document Status**: Definitive Chronological Implementation Plan & Conformance Gates (v0.1)  
> **Session Entry Point**: Primary operational document for AI agents and human engineers.  
> **Detailed Cross-References**: Directly points to exact lines in [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md), [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md), and [`non_goals.md`](file:///c:/Users/ok/Documents/retro%20manager/non_goals.md).

---

## 1. Executive Implementation Overview

The engineering execution of RetroPack progresses strictly through seven sequential, gated phases. No phase begins until the acceptance criteria of the preceding phase are cryptographically or programmatically verified:

```text
Phase 0: Staging & Toolchain Setup (COMPLETE)
   │ (Dependencies integrated, clean-room staging verified, HybridKeystore implemented)
   ▼
Phase 1: Domain Core & ROM Engine
   │ (Streaming checksums, binary headers, IPS/UPS patcher)
   ▼
Phase 2: Low-Level Runtime Bedrock (AAR)
   │ (NDK r28b+ 16 KB CMake, JNI bridge, GLES, Audio, SRAM fsync)
   ▼
Phase 3: Generic Standalone Template APK
   │ (Zero-chrome GameActivity, adaptive icon layers, bytecode-compiled SHA-256)
   ▼
Phase 4: Transformation Engine & 15-Step Pipeline
   │ (ARSCLib AXML mutator, zipflinger 16 KB aligner, apksig, ApkVerifier)
   ▼
Phase 5: Modern Jetpack Compose UI
   │ (Material 3 stepper, dark cards, floating dock, live terminal log sheet)
   ▼
Phase 6: Hardware Validation & Conformance
     (Homebrew ROM matrix, Android 8-15/16 16 KB real-device testing)
```

---

## 1.1 Dual-Engineer Collaborative Strategy & Handoff Protocol

To enable seamless, frictionless collaboration between two developers (You and your Friend) without merge conflicts or confusion, the project supports two working modes:

### Mode 1: Serialized Relay ("Passing the Baton")
* **When to Use**: When working at different times (e.g. Developer 1 works, stops, then Developer 2 takes over).
* **The Handoff Protocol**:
  1. The finishing developer commits and pushes all changes to `main`.
  2. The finishing developer updates the **🤝 Active Handover Station** at the top of `README.md` with:
     * Current Phase & Subtask completed.
     * Exact files modified.
     * The ready-to-use **Copy-Paste Prompt for Next Session**.
  3. The finishing developer sends a ping: *"Pushed to GitHub. Check README.md and continue from there!"*
  4. The incoming developer runs `git pull`, reads the handover block in `README.md`, pastes the prompt to their AI agent or starts coding directly.

### Mode 2: Parallel Decoupled Workstreams
* **When to Use**: When both developers are coding concurrently.
* **Zero-Conflict Division of Modules**:
  * **Workstream A: Core Engine & Packaging (Collaborator 1)**:
    * *Focus*: Domain models, ROM parsers, AXML mutation (`ARSCLib`), ZIP alignment (`zipflinger`), APK signing (`apksig`).
    * *Ownership Directory*: `core/` (`com.retropack.domain`, `com.retropack.engine`, `com.retropack.security`).
    * *Active Phases*: **Phase 1** and **Phase 4**.
  * **Workstream B: Runtime Host & UI Experience (Collaborator 2)**:
    * *Focus*: Canonical mGBA NDK r28b+ CMake, JNI `NativeCore`, OpenGL ES, AudioTrack, touch overlay, and Compose M3 UI.
    * *Ownership Directory*: `runtime/` (`retropack-runtime-mgba`, `template-apk`) and `app/` (`com.retropack.manager.ui`).
    * *Active Phases*: **Phase 2**, **Phase 3**, and **Phase 5**.
  * **Joint Conformance**: **Phase 6** (Hardware validation & Homebrew testing).

---

## 2. Phase 0: Infrastructure, Staging & Dependencies [STATUS: COMPLETE]

* **Primary Objective**: Establish clean-room staging from open-source references and integrate core Java/Kotlin dependencies.
* **Detailed Tasks**:
  1. Clone pinned upstream repositories into `staging/` without modification:
     * `AkuaTech/ksupatcher` $\rightarrow$ [`staging/ksupatcher/`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L48-L55)
     * `prashantchataut/Retra` $\rightarrow$ [`staging/retra/`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L48-L55)
     * `TrebuchetDynamics/garnacha-boy-android` $\rightarrow$ [`staging/garnacha-boy/`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L48-L55)
  2. Add verified Gradle packaging dependencies:
     * `com.github.REAndroid:ARSCLib:1.3.1` (Structured AXML mutation; see [`masterplan.md#L110-L114`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L110-L114))
     * `com.android:zipflinger:8.2.0` (16 KB page-size ZIP alignment; see [`architechture.md#L277-L281`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L277-L281))
     * `com.android.tools.build:apksig:8.2.0` (Single-pass v1/v2/v3 signing; see [`architechture.md#L237-L262`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L237-L262))
     * `org.bouncycastle:bcprov-jdk18on:1.78.1` (Software code-signing key and X.509 generation)
  3. Implement `HybridKeystore.kt`:
     * Generate AES-256-GCM master key in `AndroidKeyStore`.
     * Store software RSA-2048 / EC P-256 code-signing key encrypted at rest in app-private files.
     * Verify in-memory export to PKCS#12 (`.p12`) / Java Keystore (`.jks`).
* **Deliverables**: Verified build environment, staged repositories, and functional `HybridKeystore` unit test suite.
* **Acceptance Gate**: Key generation, encryption, decryption, and `.p12` export succeed; `apksig` produces valid signatures using decrypted key bytes.

---

## 3. Phase 1: Domain Core & ROM Engine [STATUS: COMPLETE]

* **Primary Objective**: Implement zero-heap streaming checksum calculation, binary ROM header parsing, and IPS/UPS patch application.
* **Detailed Specifications**:
  * ROM Engine extraction details: [`masterplan.md#L97-L109`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L97-L109).
  * Data contracts: [`architechture.md#L88-L148`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L88-L148).
* **Detailed Tasks**:
  1. Implement `StreamChecksum.kt`: Reads $64\text{ KB}$ buffered stream chunks; calculates CRC32, MD5, SHA-1, and SHA-256 concurrently.
  2. Implement `GbRomParser.kt`:
     * Reads byte slice `0x0104 - 0x014F`.
     * Validates Nintendo scrolling logo.
     * Extracts Title (`0x0134 - 0x0143`), CGB Flag (`0x0143`), Cartridge/MBC Type (`0x0147`).
     * Verifies 8-bit header checksum: $\sum_{i=0x0134}^{0x014C} -x_i - 1 \pmod{256}$.
  3. Implement `GbaRomParser.kt`:
     * Reads byte slice `0x00A0 - 0x00BD`.
     * Extracts Title (`0x00A0 - 0x00AB`), Game Code (`0x00AC - 0x00AF`), Maker Code (`0x00B0 - 0x00B1`).
     * Verifies 8-bit header checksum: $\sum_{i=0x00A0}^{0x00BC} -x_i - 0x19 \pmod{256}$.
  4. Implement `IpsUpsPatcher.kt`: In-stream binary patch applicator.
  5. Assemble immutable domain model `RomIdentity` and serializable `BuildRequest`.
* **Deliverables**: Pure Kotlin domain module passing unit test fixtures against known GB, GBC, and GBA ROM test files.
* **Acceptance Gate**: 100% test pass rate across test ROM suite (27/27 tests passed); zero heap allocation spikes during multi-megabyte stream hashing.

---

## 4. Phase 2: Low-Level Runtime Bedrock (`retropack-runtime-mgba.aar`)

* **Primary Objective**: Compile canonical mGBA 0.10.x with NDK r28b+ for 16 KB page-size compliance and build the hardened JNI runtime host library.
* **Detailed Specifications**:
  * CMake script & compilation flags: [`masterplan.md#L132-L166`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L132-L166).
  * JNI `NativeCore` bindings: [`masterplan.md#L167-L189`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L167-L189).
  * 16 KB Page Linker rules: [`architechture.md#L277-L281`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L277-L281).
  * Durability & POSIX `fsync` contract: [`architechture.md#L289-L295`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L289-L295).
* **Subphase Breakdown & Implementation Status**:
  * ✅ **Part 2.1 — Skeleton & NativeCore Contracts [COMPLETED]**:
    - Runtime module structure: [`runtime/retropack-runtime-mgba`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba).
    - JNI Core Bridge contract: [`NativeCore.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/NativeCore.kt).
    - Hardware Keymask & Builder: [`RetroKey.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/RetroKey.kt).
    - Emulation Lifecycle State Machine: [`EmulationState.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/EmulationState.kt).
    - Display Scaling Modes & Viewport Math: [`ScaleMode.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/ScaleMode.kt).
    - Emulation Engine Interface & Native Driver: [`EmulationEngine.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/EmulationEngine.kt).
    - Consumer ProGuard/R8 Rules: [`consumer-rules.pro`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/consumer-rules.pro).
    - Full Unit Test Suite: 19/19 tests passing (`RetroKeyTest`, `EmulationStateTest`, `ScaleModeTest`, `NativeEmulationEngineTest`).
  * ✅ **Part 2.2 — Save Durability & POSIX fsync Contract [COMPLETED]**:
    - Atomic POSIX fsync durability manager: [`SaveManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/save/SaveManager.kt).
    - Dirty-gated hash check eliminating redundant NAND flash wear.
    - Lifecycle flush guarantees (`onPause`/`onStop`) and backup rotation (`.bak`).
    - Full Unit Test Suite: 7/7 tests passing in [`SaveManagerTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/save/SaveManagerTest.kt) (53/53 tests passing repository-wide).
  * ✅ **Part 2.3 — 16 KB CMake & mGBA C Bridge [COMPLETED]**:
    - Vendored canonical mGBA (`v0.10.5`) as untouched Git submodule under [`runtime/retropack-runtime-mgba/submodules/mgba/`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/submodules/mgba/).
    - Configured [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/CMakeLists.txt) with mandatory 16 KB linker flags (`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`) targeting NDK r28b+.
    - Implemented thread-safe audio ring buffer with drift compensation: [`ringbuffer.h`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/cpp/ringbuffer.h), [`ringbuffer.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/cpp/ringbuffer.c).
    - Implemented full JNI bridge [`mgba-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/cpp/mgba-jni.c) binding mGBA C core to [`NativeCore.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/core/NativeCore.kt).
    - Wired `externalNativeBuild` into [`build.gradle.kts`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/build.gradle.kts) for CI/CD NDK compilation while preserving pure JVM host unit testing (53/53 tests passing repository-wide).
  * ✅ **Part 2.4 — Video & Audio Subsystems [COMPLETED]**:
    - OpenGL ES 2.0/3.0 SurfaceView renderer with `integer_fit` and `aspect_fit` scaling modes: [`RetroGlShader.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/video/RetroGlShader.kt), [`RetroGlRenderer.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/video/RetroGlRenderer.kt), [`RetroSurfaceView.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/video/RetroSurfaceView.kt).
    - Low-latency circular ring-buffered `AudioTrack` 16-bit stereo at $44,100\text{ Hz}$ with dynamic drift compensation: [`AudioSink.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/AudioSink.kt), [`AudioTrackSink.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/AudioTrackSink.kt), [`AudioDriftController.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/AudioDriftController.kt), [`RetroAudioPlayer.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/audio/RetroAudioPlayer.kt).
    - Full Unit Test Suite: 22 new tests added in [`RetroGlShaderTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/video/RetroGlShaderTest.kt), [`RetroGlRendererTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/video/RetroGlRendererTest.kt), [`AudioDriftControllerTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/audio/AudioDriftControllerTest.kt), [`RetroAudioPlayerTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/audio/RetroAudioPlayerTest.kt) (75/75 tests passing repository-wide).
  * ✅ **Part 2.5 — Virtual Touch & HID Gamepad Mapper [COMPLETED]**:
    - Pure geometry & responsive hit-testing engine: [`TouchLayout.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/TouchLayout.kt).
    - Multi-touch visual overlay view with customizable opacity, haptic feedback, and visibility toggles: [`TouchOverlayView.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/TouchOverlayView.kt).
    - Physical controller HID mapper supporting buttons, analog sticks with deadzone, and HAT axes: [`GamepadMapper.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/GamepadMapper.kt).
    - Unified input coordinator with auto-hiding virtual touch controls upon gamepad detection: [`InputCoordinator.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/input/InputCoordinator.kt).
    - Full Unit Test Suite: 18 new tests added across [`TouchLayoutTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/input/TouchLayoutTest.kt), [`TouchOverlayTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/input/TouchOverlayTest.kt), [`GamepadMapperTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/input/GamepadMapperTest.kt), and [`InputCoordinatorTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/input/InputCoordinatorTest.kt).
  * ✅ **Part 2.6 — Runtime Host Bedrock Integration & Phase 2 Completion [COMPLETED]**:
    - Atomic First-Boot ROM staging with POSIX fsync and SHA-256 validation: [`RomStager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/RomStager.kt).
    - Dedicated 60 FPS emulation loop thread with frame pacing and state synchronization: [`EmulationLoop.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/EmulationLoop.kt).
    - Top-level runtime coordinator tying engine, audio, video, input, and lifecycle save durability together: [`EmulationHost.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/EmulationHost.kt).
    - Full Unit Test Suite: 9 new tests added across [`RomStagerTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/host/RomStagerTest.kt) and [`EmulationHostTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/host/EmulationHostTest.kt).
* **Deliverables**: Standalone `retropack-runtime-mgba.aar` compiled for `arm64-v8a` and `x86_64` with complete pure-JVM test coverage (102/102 tests passing repository-wide).
* **Acceptance Gate**: ELF inspection verifies `LOAD` segments aligned to 16,384 bytes (`readelf -l libmgba.so`); audio/video loop executes cleanly at 60 FPS without memory leaks.

---

## 5. Phase 3: Generic Standalone Template APK (`template-mgba.apk`) [STATUS: COMPLETE]

* **Primary Objective**: Build the precompiled, unsigned generic APK skeleton embedding `retropack-runtime-mgba.aar` and establish trust fingerprints.
* **Detailed Specifications**:
  * RuntimeTemplate contract: [`architechture.md#L69-L84`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L69-L84).
  * Manifest invariants: [`architechture.md#L266-L276`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L266-L276).
  * Standalone bootstrap lifecycle: [`masterplan.md#L125-L131`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L125-L131).
* **Subphase Breakdown & Implementation Status**:
  * ✅ **Part 3.1 — Application Skeleton, Resources & Bootstrap [COMPLETED]**:
    - Dual-mode application skeleton: [`template-apk/build.gradle.kts`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/build.gradle.kts), registered in [`settings.gradle.kts`](file:///c:/Users/ok/Documents/retro%20manager/settings.gradle.kts).
    - Hardened AndroidManifest: [`AndroidManifest.xml`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/AndroidManifest.xml) with fully-qualified `<activity android:name="com.retropack.runtime.GameActivity" ... />` (Invariant 1) and `<application android:extractNativeLibs="false" ... />` (Invariant 2).
    - Adaptive icon drawables: [`ic_launcher.xml`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/res/mipmap-anydpi-v26/ic_launcher.xml), [`ic_launcher_round.xml`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) and un-densitied PNG layer binaries in [`res/drawable-nodpi/`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/res/drawable-nodpi/) enabling pure-file ZIP replacement.
    - Injected runtime config model & parser: [`RuntimeConfig.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/host/RuntimeConfig.kt).
    - Standalone bootstrap coordinator: [`GameActivity.kt`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt) with sticky immersive flags, 60 Hz display mode locking, atomic ROM staging via `RomStager`, view hierarchy assembly, input routing, and synchronous POSIX `fsync` save durability on `onPause()` / `onStop()`.
  * ✅ **Part 3.2 — Registry, Template Descriptor & Integrity Fingerprints [COMPLETED]**:
    - Runtime descriptor contract model: [`RuntimeDescriptor.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/runtime/RuntimeDescriptor.kt).
    - Runtime template bundle abstraction: [`RuntimeTemplate.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/runtime/RuntimeTemplate.kt).
    - Discovery engine & bytecode trust anchors: [`RuntimeRegistry.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/runtime/RuntimeRegistry.kt).
    - Pinned runtime bundle: [`runtimes/mgba-unified/runtime.json`](file:///c:/Users/ok/Documents/retro%20manager/runtimes/mgba-unified/runtime.json), [`runtimes/mgba-unified/template.apk`](file:///c:/Users/ok/Documents/retro%20manager/runtimes/mgba-unified/template.apk), [`runtimes/mgba-unified/licenses/LICENSE.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtimes/mgba-unified/licenses/LICENSE.txt).
    - Full Unit Test Suite: 29 new tests added across `:template-apk` and `:core` (131/131 unit tests passing repository-wide with 0 failures).
* **Deliverables**: Hardened `template-apk/` application skeleton, verified `runtimes/mgba-unified/template.apk`, and populated `RuntimeRegistry.kt`.
* **Acceptance Gate**: 100% test pass rate across 28 test suites (131/131 tests passing repository-wide); verified `extractNativeLibs="false"`, fully qualified Activity declarations, and cryptographic trust anchor validation.

---

## 6. Phase 4: Build Engine & Transformation Pipeline

* **Primary Objective**: Implement the 15-step verified on-device APK transformation pipeline.
* **Detailed Specifications**:
  * 15-step pipeline execution: [`masterplan.md#L210-L268`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L210-L268).
  * Allowlist boundary rules: [`architechture.md#L215-L235`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L215-L235).
  * 16 KB page-size ZIP alignment: [`architechture.md#L277-L281`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L277-L281).
* **Detailed Tasks**:
  1. Implement `BuildEngine.kt` orchestrating Steps 1–15:
     * **Step 1**: Streamed checksum calculation and header validation.
     * **Step 2**: Assert `SHA-256(template.apk) == RuntimeRegistry.TRUSTED_TEMPLATES[id]`.
     * **Step 3**: Pre-flight package collision and certificate match check.
     * **Step 4**: Allocate scratch file in output directory (`outputDir/.<uuid>.tmp.apk`).
     * **Step 5**: Strip stale `META-INF/` signatures and APK Signing Block.
     * **Step 6**: Stream ROM $\rightarrow$ `assets/game.rom` and JSON $\rightarrow$ `assets/retropack.json`.
     * **Step 7**: Mutate AXML via `ARSCLib` (set deterministic package ID, inlined literal `android:label`, `versionCode`).
     * **Step 8**: Inject adaptive icon PNG layers into `res/drawable-nodpi/`.
     * **Step 9**: Cryptographically verify SHA-256 of `protected_entries` (`classes*.dex`, `lib/*`).
     * **Step 10**: Reassemble ZIP archive using `com.android:zipflinger`, setting `alignment = 16384` for uncompressed `.so` libraries.
     * **Step 11**: Programmatically verify `(payload_offset % 16384 == 0)`.
     * **Step 12**: Sign APK with `apksig` applying v1, v2, and v3 schemes simultaneously.
     * **Step 13**: Execute programmatic `ApkVerifier.verify()` assertion.
     * **Step 14**: Atomic rename `.tmp.apk` $\rightarrow$ final destination file.
     * **Step 15**: Return verified `BuildResult` record.
* **Deliverables**: Fully functioning, pure-Java/Kotlin `BuildEngine` operational on Android.
* **Acceptance Gate**: Automated pipeline produces installable APK from raw ROM input in $< 800\text{ ms}$; `ApkVerifier` confirms signature validity across v1, v2, and v3 schemes.

---

## 7. Phase 5: Modern Jetpack Compose UI Experience

* **Primary Objective**: Build the user-facing Manager interface adapted from `AkuaTech/ksupatcher`.
* **Detailed Specifications**:
  * UI layer breakdown: [`masterplan.md#L89-L96`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L89-L96).
  * Stepper & dark card mechanics: [`masterplan.md#L58-L60`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L58-L60).
  * Package collision & updates: [`masterplan.md#L115-L118`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L115-L118).
* **Detailed Tasks**:
  1. Implement Material 3 Dark theme tokens, typography, and card components (`DarkCard.kt`).
  2. Implement vertical 3-step stepper (`StepperLayout.kt`):
     * Step 01: Core & Variant card (mGBA Unified).
     * Step 02: Content & ROM card with SAF file picker, hash badge, and patch slot.
     * Step 03: Application Identity card with deterministic package derived, custom icon picker, and touch layout dropdown.
  3. Implement `FloatingDock.kt` pill action bar with "START PACKAGING" primary CTA.
  4. Implement `TerminalBottomSheet.kt`: Live execution log sheet displaying real-time 15-step progress provenance.
  5. Implement `PackageCollisionTracker.kt` and Room database `GameEntity` for tracking installed games and managing in-place updates.
* **Deliverables**: Polished, reactive Android application interface.
* **Acceptance Gate**: User can select a ROM via SAF, configure app identity, observe real-time build logs, and install the resulting APK in $< 3$ taps.

---

## 8. Phase 6: Hardware Testing & Conformance Validation

* **Primary Objective**: Validate runtime performance, crash-free 16 KB loading, and save durability across real physical hardware.
* **Detailed Specifications**:
  * Physical validation requirement: Law 9 & Law 22 in [`architechture.md#L52-L66`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L52-L66).
  * Non-goals & boundaries: [`non_goals.md#L32-L44`](file:///c:/Users/ok/Documents/retro%20manager/non_goals.md#L32-L44).
* **Public-Domain Homebrew ROM Test Suite**:
  1. **Game Boy**: *Tobu Tobu Girl Deluxe* (`.gb`) — Verifies standard GB/DMG audio/video timing and battery save persistence.
  2. **Game Boy Color**: *Dangan GB* (`.gbc`) — Verifies CGB palette rendering and high-frequency display sync.
  3. **Game Boy Advance**: *Anguna: Warriors of the Demis* (`.gba`) — Verifies 32-bit ARM7TDMI execution, Flash/EEPROM saves, and 16 KB memory-mapping.
* **Hardware Matrix & Stress Tests**:
  * **OS Range**: Physical testing on Android 8.0, Android 11, Android 13, Android 14, and Android 15 (16 KB page-size kernel).
  * **Kill Test**: Force-killing the standalone game process during initial ROM staging to verify that atomic temporary swap prevents file corruption.
  * **SRAM Endurance Test**: Verifying that periodic 60-second dirty-gated flush and POSIX `fsync` prevent data loss during sudden battery shutdown.
* **Deliverables**: Comprehensive conformance report and automated physical test suite.
* **Acceptance Gate**: Zero crashes on Android 15 16 KB devices; 100% save-state and SRAM durability across all stress test runs.

---

## 9. Master Cross-Reference Index

| Implementation Topic | Target Source Document | Exact Section & Lines |
| :--- | :--- | :--- |
| **7-Layer Architectural Model** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 1, lines 8–38 |
| **Constitutional Laws 1–22** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 2, lines 40–66 |
| **RuntimeTemplate Specification** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 3, lines 69–84 |
| **Declarative BuildRequest Schema** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 4, lines 88–148 |
| **Runtime Injected retropack.json** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 4, lines 150–182 |
| **Runtime Descriptor runtime.json** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 4, lines 184–212 |
| **Packaging Allowlist & Blocklist** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 5, lines 215–235 |
| **Hybrid Keystore Cryptography** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 6, lines 237–262 |
| **Fully Qualified Activity Invariant** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 7, lines 266–272 |
| **extractNativeLibs & 16 KB Linker** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 7, lines 273–281 |
| **Deterministic Package Naming** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 7, lines 282–288 |
| **Atomic Staging & POSIX fsync** | [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md) | Section 7, lines 289–295 |
| **Grounded Architecture (4 Repos)** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 1, lines 8–32 |
| **Staging & Extraction Guide** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 2, lines 34–73 |
| **Unified mGBA Emulation Core** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 3, lines 75–83 |
| **Manager App Specification** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 4, lines 85–119 |
| **Runtime Host Specification** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 5, lines 121–208 |
| **mGBA NDK CMake Script** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 5.2, lines 132–166 |
| **JNI NativeCore Bindings** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 5.3, lines 167–189 |
| **15-Step Packaging Pipeline** | [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md) | Section 6, lines 210–268 |
| **Constitutional Non-Goals** | [`non_goals.md`](file:///c:/Users/ok/Documents/retro%20manager/non_goals.md) | Sections 1–3, lines 1–45 |
