# RetroPack — On-Device Retro Game APK Packager

> **Transform legally owned retro ROMs into autonomous, first-class, standalone Android applications on-device.**  
> **Initial Scope (v0.1)**: Game Boy (`.gb`), Game Boy Color (`.gbc`), Game Boy Advance (`.gba`) powered by canonical mGBA 0.10.x.  
> **Repository**: [https://github.com/Zoro-15/retro-manager](https://github.com/Zoro-15/retro-manager)

---

## 🤝 Active Handover Station (Current Baton)

> [!IMPORTANT]
> **To the Next Collaborator or AI Agent**:
> * **Current Phase**: **Part 2.2 is COMPLETE**. The project is ready for **Part 2.3: 16 KB CMake & mGBA C Bridge**.
> * **Active Workstream**: Workstream B (Runtime Host Subsystem).
> * **Last Completed Work (Part 2.2 — Save Durability & POSIX fsync)**:
>   1. Implemented [`SaveManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/main/kotlin/com/retropack/runtime/save/SaveManager.kt): Enforces atomic POSIX fsync replacement sequence: `write tmp` $\rightarrow$ `stream.flush()` $\rightarrow$ `stream.fd.sync()` $\rightarrow$ `tmp.renameTo(game.sav)`.
>   2. Implemented dirty-gated flush logic comparing SRAM buffer hashes to eliminate redundant NAND flash writes.
>   3. Implemented automatic backup rotation (`game.sav.bak`) and corrupt temporary file pruning on restore.
>   4. Added unit test suite [`SaveManagerTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mgba/src/test/kotlin/com/retropack/runtime/save/SaveManagerTest.kt) with 7/7 tests passing (53/53 tests passing repository-wide).

### 📋 Copy-Paste Prompt for the Next Session
Copy and paste the block below into your AI coding assistant or session prompt to resume work immediately:

```text
You are pair programming on RetroPack (an on-device ROM-to-standalone Android APK transformer).
Read context.md, masterplan.md, and roadmap.md first.

Current Status:
- Phase 0 (Infrastructure, Staging & Hybrid Keystore) is COMPLETE.
- Phase 1 (Domain Core & ROM Engine) is COMPLETE.
- Phase 2, Part 2.1 (Skeleton & NativeCore Contracts) is COMPLETE.
- Phase 2, Part 2.2 (Save Durability & POSIX fsync) is COMPLETE:
  - SaveManager.kt and SaveManagerTest.kt implemented with 100% test pass rate.
  - 53/53 unit tests passing across core and runtime modules.

Your Task:
Proceed with Phase 2, Part 2.3 (16 KB CMake & mGBA C Bridge):
1. Vendor canonical mGBA (v0.10.5+) as an untouched Git submodule under runtime/retropack-runtime-mgba/submodules/mgba/.
2. Configure CMakeLists.txt with mandatory 16 KB page-size linker flags (-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384) targeting NDK r28b+.
3. Implement JNI bridge mgba-jni.c and ringbuffer.c in runtime/retropack-runtime-mgba/src/main/cpp/ binding to NativeCore.kt JNI methods.
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
