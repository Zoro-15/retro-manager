# RetroPack — On-Device Retro Game APK Packager

> **Transform legally owned retro ROMs into autonomous, first-class, standalone Android applications on-device.**  
> **Initial Scope (v0.1)**: Game Boy (`.gb`), Game Boy Color (`.gbc`), Game Boy Advance (`.gba`) powered by canonical mGBA 0.10.x.  
> **Repository**: [https://github.com/Zoro-15/retro-manager](https://github.com/Zoro-15/retro-manager)

---

## 🤝 Active Handover Station (Current Baton)

> [!IMPORTANT]
> **To the Next Collaborator or AI Agent**:
> * **Current Phase**: **Phase 1 is COMPLETE**. The project is ready for **Phase 2: Low-Level Runtime Bedrock (`retropack-runtime-mgba.aar`)**.
> * **Active Workstream**: Workstream B (Runtime Host & UI Experience) or Workstream A (Packaging Engine prep).
> * **Last Completed Work (Phase 1)**:
>   1. Implemented `StreamChecksum.kt` in `core/src/main/kotlin/com/retropack/domain/rom/`: Zero-heap chunked streaming calculation of CRC32, MD5, SHA-1, and SHA-256.
>   2. Implemented `GbRomParser.kt`: GB/GBC Nintendo logo validation, title extraction, CGB flag (`0x80`/`0xC0`), cartridge MBC type and battery detection, and 8-bit header checksum validation.
>   3. Implemented `GbaRomParser.kt`: GBA title, game code, maker code, fixed value (`0x96`) verification, and 8-bit header checksum validation.
>   4. Implemented `IpsUpsPatcher.kt`: In-stream binary patch applicator supporting IPS, UPS, and BPS patches with checksum verification.
>   5. Implemented `RomIdentity.kt` & `RomParser.kt`: Unified ROM detection facade with deterministic package name derivation (`com.retropack.game.<slug>_<hash10>`).
>   6. Added full test suite (`StreamChecksumTest`, `GbRomParserTest`, `GbaRomParserTest`, `IpsUpsPatcherTest`, `RomParserTest`) with 100% pass rate (27/27 tests passing).

### 📋 Copy-Paste Prompt for the Next Session
Copy and paste the block below into your AI coding assistant or session prompt to resume work immediately:

```text
You are pair programming on RetroPack (an on-device ROM-to-standalone Android APK transformer).
Read context.md, masterplan.md, and roadmap.md first.

Current Status:
- Phase 0 (Infrastructure, Staging & Hybrid Keystore) is COMPLETE.
- Phase 1 (Domain Core & ROM Engine) is COMPLETE:
  - StreamChecksum.kt (zero-heap chunked CRC32, MD5, SHA-1, SHA-256 calculator) in core/src/main/kotlin/com/retropack/domain/rom/.
  - GbRomParser.kt and GbaRomParser.kt (GB/GBC/GBA header parsing and checksum validation).
  - IpsUpsPatcher.kt (streaming IPS/UPS/BPS patch applicator).
  - RomIdentity.kt and RomParser.kt (unified ROM detection and package name derivation).
  - 100% test pass rate (27/27 tests passing across core module).

Your Task:
Proceed with Phase 2 from roadmap.md:
1. Vendor canonical mGBA (v0.10.5+) as a Git submodule under runtime/retropack-runtime-mgba/submodules/mgba/.
2. Configure CMakeLists.txt with mandatory 16 KB page-size linker flags (-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384) targeting NDK r28b+.
3. Implement JNI bridge mgba-jni.c and Kotlin NativeCore.kt interface supporting frame stepping, input keymasks, audio buffers, and SRAM read/write.
4. Implement SaveManager.kt enforcing POSIX fsync durability contract (tmp -> fsync -> rename).
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
