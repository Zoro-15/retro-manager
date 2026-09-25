# RetroPack — On-Device Retro Game APK Packager

> **Transform legally owned retro ROMs into autonomous, first-class, standalone Android applications on-device.**  
> **Initial Scope (v0.1)**: Game Boy (`.gb`), Game Boy Color (`.gbc`), Game Boy Advance (`.gba`) powered by canonical mGBA 0.10.x.  
> **Repository**: [https://github.com/Zoro-15/retro-manager](https://github.com/Zoro-15/retro-manager)

---

## 🚨 CRITICAL OPERATIONAL MANDATE (TOP PRIORITY)

> [!CAUTION]
> **DO NOT RUN VERIFICATION TESTS OR HEAVY GRADLE TEST SUITES ON THE LOCAL HOST MACHINE.**
> * **Hardware Context**: The user's development laptop is a low-end legacy Pentium machine from 2011. Running heavy local test suites causes extreme latency.
> * **Remote CI Strategy**: All automated tests and verification pipelines run on **GitHub Actions CI** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) automatically upon pushing any commit or pull request.
> * **Phase Completion Strategy**: Proceed directly through the implementation phases without stopping to run local verification tests. Once all phases (Phases 0 through 6) are complete, we will centrally inspect and analyze the CI execution logs and test reports.

---

## 🤝 Active Handover Station (Current Baton)

> [!IMPORTANT]
> **To the Next Collaborator or AI Agent**:
> * **Current Phase**: **Phase 5 (Modern Jetpack Compose UI Experience) is 100% COMPLETE**. The project is ready for **Phase 6: Hardware Testing, CI Verification & Conformance Review**.
> * **Branch**: [`v2`](https://github.com/Zoro-15/retro-manager/tree/v2) (Pushed and tracking origin/v2 with automated CI workflow).
> * **Accomplished in Phase 5 (Modern Jetpack Compose UI Experience)**:
>   1. **Part 5.1 — Material 3 Dark Theme & Design System**: [`Color.kt`](app/src/main/kotlin/com/retropack/manager/ui/theme/Color.kt), [`Type.kt`](app/src/main/kotlin/com/retropack/manager/ui/theme/Type.kt), [`Shape.kt`](app/src/main/kotlin/com/retropack/manager/ui/theme/Shape.kt), [`Theme.kt`](app/src/main/kotlin/com/retropack/manager/ui/theme/Theme.kt) with deep OLED dark palette, neon accents, and edge-to-edge system bars.
>   2. **Part 5.2 — 3-Step Vertical Stepper & Components**: [`StepHeader.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/StepHeader.kt), [`RetroCard.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/RetroCard.kt), [`ActionTile.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/ActionTile.kt), [`RomInspectionCard.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/RomInspectionCard.kt) (with header verification, chip details, and one-tap checksum copy), [`IconPreviewCard.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/IconPreviewCard.kt) (with adaptive icon layer synthesis), [`AdvancedOptionsSection.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/AdvancedOptionsSection.kt) (scaling mode, touch controls, haptics, opacity, gamepad auto-hide), and [`KeystoreDialog.kt`](app/src/main/kotlin/com/retropack/manager/ui/screens/KeystoreDialog.kt).
>   3. **Part 5.3 — Floating Action Dock**: [`FloatingDock.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/FloatingDock.kt) with live progress bar, stage counter, and glowing CTA.
>   4. **Part 5.4 — Live Terminal Execution Sheet**: [`TerminalView.kt`](app/src/main/kotlin/com/retropack/manager/ui/components/TerminalView.kt), [`TerminalBottomSheet.kt`](app/src/main/kotlin/com/retropack/manager/ui/screens/TerminalBottomSheet.kt) with real-time 15-step `BuildStageRecord` streaming, log file export, and direct APK installation / sharing.
>   5. **Reactive State Machine & Tests**: [`MainViewModel.kt`](app/src/main/kotlin/com/retropack/manager/viewmodel/MainViewModel.kt), [`UiState.kt`](app/src/main/kotlin/com/retropack/manager/viewmodel/UiState.kt), [`MainViewModelTest.kt`](app/src/test/kotlin/com/retropack/manager/MainViewModelTest.kt), [`UriUtilsTest.kt`](app/src/test/kotlin/com/retropack/manager/UriUtilsTest.kt), and [`HomebrewConformanceTest.kt`](core/src/test/kotlin/com/retropack/packaging/HomebrewConformanceTest.kt).
> * **Next Task**: **Phase 6: Hardware Testing, CI Verification Analysis & Final Conformance Review**.
>   - Inspect and analyze the GitHub Actions CI execution logs for the pushed `v2` branch.
>   - Verify 100% test pass rate across all 4 modules (`:core`, `:runtime:retropack-runtime-mgba`, `:template-apk`, `:app`).
>   - Review conformance test artifacts for Game Boy (*Tobu Tobu Girl Deluxe*), Game Boy Color (*Dangan GB*), and Game Boy Advance (*Anguna: Warriors of the Demis*).

### 📋 Copy-Paste Prompt for the Next Session (Phase 6)
Copy and paste the block below into your AI coding assistant or session prompt to resume work immediately:

```text
You are pair programming on RetroPack (an on-device ROM-to-standalone Android APK transformer).
Repository: https://github.com/Zoro-15/retro-manager
Branch: v2
Read context.md, masterplan.md, and roadmap.md first.

Current Status:
- Phase 0 (Infrastructure, Staging & Hybrid Keystore) is COMPLETE.
- Phase 1 (Domain Core & ROM Engine) is COMPLETE.
- Phase 2 (Low-Level Runtime Bedrock & mGBA C Bridge) is COMPLETE.
- Phase 3 (Generic Standalone Template APK, Packaging Mutators & Trust Registry) is COMPLETE.
- Phase 4 (Build Engine & 15-Step Transformation Pipeline) is COMPLETE.
- Phase 5 (Modern Jetpack Compose UI Experience & Manager App) is COMPLETE.
- All code has been pushed to remote branch 'v2'.
- GitHub Actions CI workflow is running on branch 'v2'.

TOP PRIORITY MANDATE:
DO NOT RUN HEAVY GRADLE RUNS ON THE LOCAL LAPTOP (Host is a low-end 2011 Pentium).
Inspect GitHub Actions CI build logs and reports remotely via git/gh/workflow or web API.

Your Task:
Proceed with Phase 6 (Hardware Testing, CI Verification Analysis & Conformance Review):
1. Review the GitHub Actions CI workflow execution for branch 'v2' across all 4 modules (:core, :runtime:retropack-runtime-mgba, :template-apk, :app).
2. Validate conformance coverage for the 3 canonical public-domain homebrew ROM fixtures:
   - Game Boy: Tobu Tobu Girl Deluxe (.gb)
   - Game Boy Color: Dangan GB (.gbc)
   - Game Boy Advance: Anguna: Warriors of the Demis (.gba)
3. Verify all 22 Constitutional Invariants (16 KB page alignment, uncompressed .so, fully qualified GameActivity, single-pass v1/v2/v3 signatures, POSIX fsync save durability, Hybrid Keystore key protection).
4. Summarize the end-to-end repository architecture and compile the final project conformance report.
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
│       ├── domain/runtime/     # RuntimeRegistry, RuntimeTemplate, RuntimeDescriptor
│       ├── packaging/          # AxmlMutator, IconInjector, RomAssetInjector, etc.
│       └── security/           # HybridKeystore (AES-GCM at rest, RSA/EC, .p12 export)
├── runtime/                    # Standalone Game Runtime Host & NDK Bridge (mGBA 0.10.x)
├── runtimes/                   # Pinned Runtime Bundles & Verified Templates (template.apk)
├── template-apk/               # Generic Standalone Template APK Skeleton (GameActivity)
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
> [!NOTE]
> **Host System Note**: The user's system is a low-end Pentium from 2011, so verification tests and Gradle runs take more time (~3+ minutes).

```bash
# Clone the repository
git clone https://github.com/Zoro-15/retro-manager.git
cd retro-manager

# Run all unit tests (pure JVM suite across core, runtime, and template-apk)
./gradlew :core:test :runtime:retropack-runtime-mgba:test :template-apk:test

# Build the Manager application
./gradlew :app:assembleDebug
```

---

## 6. License & Attribution

RetroPack is an open-source system respecting all upstream licenses:
* Core host wrapper derived from `garnacha-boy-android` is licensed under MIT.
* Upstream canonical `mGBA` is an untouched submodule licensed under MPL-2.0.
* Components derived from `ksupatcher`, `Retra`, and `Ludere` respect GPL-3.0 obligations.
