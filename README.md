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
> * **Current Phase**: **Phase 4 (Build Engine & 15-Step Transformation Pipeline) is 100% COMPLETE**. The project is ready for **Phase 5: Modern Jetpack Compose UI Experience (`app/`)**.
> * **Active Workstream**: Workstream B (UI & Manager Subsystem) $\rightarrow$ Modern Jetpack Compose Material 3 Stepper, Dark Cards, Floating Dock, and Real-Time Build Log Sheet.
> * **Accomplished in Phase 4 (Build Engine & Transformation Pipeline)**:
>   1. **Part 4.1 — 16 KB Alignment Verification Engine**: [`AlignmentVerifier.kt`](core/src/main/kotlin/com/retropack/packaging/AlignmentVerifier.kt) reads central directory records and local file headers, extracting payload byte offsets and mathematically asserting `(payloadOffset % 16384 == 0)` for uncompressed native libraries (`.so`).
>   2. **Part 4.2 — Native Zipflinger Archive Reassembly**: [`ZipArchiveTransformer.kt`](core/src/main/kotlin/com/retropack/packaging/ZipArchiveTransformer.kt) consumes precompiled generic template APKs, strips stale `META-INF/` signatures/signing blocks, injects mutated AXML, ROMs, configs, and icons, and writes uncompressed `.so` libraries with 16 KB physical page alignment.
>   3. **Part 4.3 — Single-Pass Multi-Scheme Signing**: [`ApkSignerService.kt`](core/src/main/kotlin/com/retropack/packaging/ApkSignerService.kt) applies APK Signature Schemes v1 (JAR), v2 (APK Signing Block), and v3 (Key rotation support) simultaneously in a single deterministic pass using `HybridKeystore` keys.
>   4. **Part 4.4 — Programmatic Integrity Verification**: [`ApkVerificationService.kt`](core/src/main/kotlin/com/retropack/packaging/ApkVerificationService.kt) verifies signature validity across v1, v2, and v3 schemes via `com.android.apksig.ApkVerifier`.
>   5. **Part 4.5 — 15-Step Verified Transformation Pipeline Orchestrator**: [`BuildEngine.kt`](core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt) orchestrates the end-to-end on-device packaging pipeline (ROM checksums $\rightarrow$ template assertion $\rightarrow$ pre-flight check $\rightarrow$ scratch allocation $\rightarrow$ signature stripping $\rightarrow$ asset injection $\rightarrow$ AXML mutation $\rightarrow$ icon injection $\rightarrow$ protected entry verification $\rightarrow$ 16 KB alignment $\rightarrow$ offset assertion $\rightarrow$ v1/v2/v3 signing $\rightarrow$ ApkVerifier assertion $\rightarrow$ atomic rename $\rightarrow$ BuildResult).
>   6. **Part 4.6 — Remote GitHub Actions CI**: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) configured to run all test suites on push/PR.
> * **Next Task**: **Phase 5: Modern Jetpack Compose UI Experience (`app/`)**.

### 📋 Copy-Paste Prompt for the Next Session
Copy and paste the block below into your AI coding assistant or session prompt to resume work immediately:

```text
You are pair programming on RetroPack (an on-device ROM-to-standalone Android APK transformer).
Repository: https://github.com/Zoro-15/retro-manager
Read context.md, masterplan.md, and roadmap.md first.

Current Status:
- Phase 0 (Infrastructure, Staging & Hybrid Keystore) is COMPLETE.
- Phase 1 (Domain Core & ROM Engine) is COMPLETE.
- Phase 2 (Low-Level Runtime Bedrock & mGBA C Bridge) is COMPLETE.
- Phase 3 (Generic Standalone Template APK, Packaging Mutators & Trust Registry) is COMPLETE.
- Phase 4 (Build Engine & 15-Step Transformation Pipeline) is COMPLETE.
- GitHub Actions CI workflow is configured in .github/workflows/ci.yml.

TOP PRIORITY MANDATE:
DO NOT RUN VERIFICATION TESTS OR HEAVY GRADLE RUNS ON THE LOCAL LAPTOP (Host is a low-end 2011 Pentium).
All test suites run in GitHub Actions CI when commits are pushed. We will analyze CI test logs after all phases are complete.

Your Task:
Proceed with Phase 5 (Modern Jetpack Compose UI Experience):
Implement the manager application UI in app/ adapted from AkuaTech/ksupatcher:
- Part 5.1: Material 3 dark theme with custom palette, typography, glassmorphism, and responsive edge-to-edge layout.
- Part 5.2: 3-Step Vertical Stepper (Step 1: Select ROM file with header auto-detection; Step 2: Configure Package Identity & Runtime Settings; Step 3: Select Signing Key & Output Path).
- Part 5.3: Floating Action Dock with live Build Button and progress states.
- Part 5.4: Real-time Live Terminal Log Sheet (ModalBottomSheet / Surface) receiving streaming BuildStageRecord callbacks from BuildEngine during APK packaging.
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
