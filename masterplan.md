# RetroPack — Technical Masterplan & Subsystems (`masterplan.md`)

> **Document Status**: Definitive Engineering Implementation Plan & Subsystem Specifications (v0.1)  
> **Role**: Single source of truth for repository porting, subsystem mechanics, Native/JNI contracts, and the 15-step packaging pipeline.

---

## 1. Grounded Architecture Strategy

Rather than generating an entire emulator host, ROM scanner, UI framework, and APK packaging engine from scratch using speculative AI code, RetroPack builds upon four battle-tested open-source repositories:

```text
┌─────────────────────────────────┬────────────────────────────────────────────────────────┐
│ Foundation Repository           │ Harvested Role in RetroPack                            │
├─────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 1. AkuaTech/ksupatcher          │ UI Layer: Material 3 dark cards, vertical 3-step       │
│    (GPL-3.0)                    │ stepper, floating action dock, live terminal log sheet. │
├─────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 2. prashantchataut/Retra        │ ROM Subsystem: Streaming CRC32/MD5/SHA1/SHA256,        │
│    (GPL-3.0)                    │ GB/GBA binary header parsers, IPS/UPS patch engine.    │
├─────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 3. TrebuchetDynamics/           │ Low-Level Runtime Bedrock: Pinned mGBA 0.10.x CMake,   │
│    garnacha-boy-android (MIT)   │ JNI NativeCore bridge, GLES renderer, AudioTrack ring  │
│                                 │ buffer, virtual touch overlay, physical gamepad HID.   │
├─────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 4. tytydraco/Ludere             │ Standalone Model: Conceptual inspiration for           │
│    (GPL-3.0)                    │ single-game zero-chrome standalone APK container.      │
│                                 │ (All packaging code replaced by ARSCLib + apksig).     │
└─────────────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## 2. Clean-Room Staging & Porting Guide

All external code follows a strict 3-stage promotion workflow to preserve clean-room integrity:

```text
[Upstream GitHub Repositories]
            │
            ▼ (git clone / download pinned release)
[staging/<repo-name>/]  <-- Pristine, untouched reference code
            │
            ▼ (inspect, extract logic, strip dead dependencies, rewrite into RetroPack contracts)
[src/ or android/ modules]  <-- Production RetroPack code
```

### Staging Directory Hierarchy
```text
retro manager/
├── staging/
│   ├── ksupatcher/          # Pristine clone of AkuaTech/ksupatcher
│   ├── retra/               # Pristine clone of prashantchataut/Retra
│   └── garnacha-boy/        # Pristine clone of TrebuchetDynamics/garnacha-boy-android
```

### Component Extraction Matrix
1. **From `ksupatcher`**:
   * *Harvest*: Numbered vertical stepper (`01 Platform`, `02 Action`, `03 Identity`), high-contrast dark cards (`RoundedCornerShape(16.dp)`), floating bottom pill dock, and real-time monospace terminal log sheet.
   * *Discard*: All `ksud` root binary calls, `su` shell wrappers, boot image patching, and OTA flashing scripts.
2. **From `Retra`**:
   * *Harvest*: Buffered stream checksum calculations, low-level binary header readers (`0x0104-0x014F` for GB/GBC; `0x00A0-0x00BD` for GBA), IPS/UPS patcher.
   * *Discard*: Multi-game carousel views and internal SQLite/Room database bindings.
3. **From `garnacha-boy-android`**:
   * *Harvest*: Pinned mGBA 0.10.x NDK CMake scripts, JNI `NativeCore` bindings, OpenGL ES 2.0/3.0 texture uploads, AudioTrack ring buffer, virtual touch overlay, physical controller HID mapper, battery SRAM persistence.
   * *Discard*: Multi-game library frontend and SkyEmu legacy code.
   * *Output*: Compiled into reusable Android Archive `retropack-runtime-mgba.aar`.
4. **Attribution Policy**:
   * MIT code retained under MIT notices.
   * Canonical mGBA untouched as an MPL-2.0 submodule.
   * Ported GPL-3.0 components respect GPL-3.0 obligations. Every ported file maintains an attribution header referencing the upstream author and commit hash.

---

## 3. Unified Emulation Core: Canonical mGBA 0.10.x

For version 0.1, RetroPack standardizes on **canonical mGBA** as the single unified runtime for Game Boy (`.gb`), Game Boy Color (`.gbc`), and Game Boy Advance (`.gba`):
* **Cycle-Accurate Multi-System Engine**: mGBA contains mature, highly cycle-accurate GB/GBC and GBA hardware emulation.
* **Zero Binary Bloat**: A single native shared library (`libmgba.so`) powers all three systems without duplicating emulator cores.
* **Unified Save Semantics**: SRAM, Flash, EEPROM, and RTC serialization share identical JNI routines across all 3 platforms.
* **Untouched Upstream**: Pinned upstream release (`v0.10.5`+) integrated via CMake; no custom forking of mGBA source.

---

## 4. Subsystem 1: RetroPack Manager Application

The Manager is the user-facing orchestrator running on Android:

### 4.1 UI Layer (`ui/`)
* **Framework**: Jetpack Compose with Material 3 Dark theme.
* **Workflow**: 3-step vertical stepper:
  * `01 Platform & Core`: mGBA Unified Engine selected and locked.
  * `02 Content & ROM`: Verified ROM card showing title, size, SHA-1 checksum, header integrity badge, and optional IPS/UPS patch slot.
  * `03 Application Identity`: Game title text field, auto-derived deterministic package name, custom boxart icon picker, and touch control layout selector.
* **Execution Feedback**: Modal bottom sheet with live monospace terminal logs displaying 15-step execution provenance.

### 4.2 ROM Inspection & Patch Engine (`domain/rom/`)
* **Streaming Checksums (`StreamChecksum.kt`)**: Reads `InputStream` in $64\text{ KB}$ chunks, feeding `CRC32`, `MD5`, `SHA-1`, and `SHA-256` concurrently without heap buffering.
* **GB/GBC Header Parser (`GbRomParser.kt`)**:
  * Reads bytes `0x0104 - 0x014F`.
  * Validates Nintendo logo.
  * Extracts Title (`0x0134 - 0x0143`), CGB Flag (`0x0143`: `0x80` = dual mode, `0xC0` = CGB only), Cartridge Type (`0x0147`: MBC1/2/3/5).
  * Validates header checksum: $\sum_{i=0x0134}^{0x014C} -x_i - 1 \pmod{256}$.
* **GBA Header Parser (`GbaRomParser.kt`)**:
  * Reads bytes `0x00A0 - 0x00BD`.
  * Extracts Title (`0x00A0 - 0x00AB`), Game Code (`0x00AC - 0x00AF`, e.g. `BPEE`), Maker Code (`0x00B0 - 0x00B1`).
  * Validates header checksum: $\sum_{i=0x00A0}^{0x00BC} -x_i - 0x19 \pmod{256}$.
* **Patch Applicator (`IpsUpsPatcher.kt`)**: Applies IPS/UPS binary patches on-the-fly during asset streaming.

### 4.3 Transformation & Packaging Engine (`engine/`)
* **`REAndroid/ARSCLib`**: Pure-Java on-device AXML editor. Re-indexes binary string pools, mutates root `package` attribute, and inlines literal `android:label="<Game Title>"` (bypassing `resources.arsc`).
* **`com.android:zipflinger`**: Google's pure-Java archive tool. Computes dynamic padding to enforce 16 KB page alignment on uncompressed `libmgba.so`.
* **`com.android.tools.build:apksig`**: Signs APKs applying v1, v2, and v3 schemes in a single pass using the decrypted Hybrid Keystore key; verifies resulting archive via `ApkVerifier`.

### 4.4 Package Collision & Update Tracking (`data/`)
* Queries `PackageManager` for the derived package name (`com.retropack.game.<slug>_<hash10>`).
* If already installed: verifies signing certificate. If valid, increments `versionCode = installedCode + 1` to ensure in-place updates without save data loss.

---

## 5. Subsystem 2: Standalone Runtime Host

The Runtime Host is the standalone container embedded inside each generated APK (`template-mgba.apk`):

### 5.1 Architecture & Bootstrap (`GameActivity.kt`)
* Fullscreen immersive window (`STICKY_IMMERSIVE`, `FLAG_KEEP_SCREEN_ON`).
* Locks refresh rate to 60 Hz via `Display.supportedModes`.
* Reads `assets/retropack.json` at boot.
* **Atomic ROM Staging**: If `filesDir/game.rom` is missing or hash differs from `retropack.json.rom_sha256`, streams `assets/game.rom` $\rightarrow$ `filesDir/game.rom.tmp`, validates SHA-256, and executes `tmp.renameTo(filesDir/game.rom)`.
* Memory-maps staged ROM at bare-metal speed using POSIX `open()` and `mmap()`.

### 5.2 Native Toolchain & 16 KB Alignment (`CMakeLists.txt`)
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(retropack-runtime-mgba C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16 KB Page Alignment (Mandatory for Android 15 / API 35+)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Pinned Canonical mGBA options
set(BUILD_SHARED OFF)
set(BUILD_STATIC ON)
set(BUILD_QT OFF)
set(BUILD_SDL OFF)
set(ENABLE_SCRIPTING OFF)

add_subdirectory(submodules/mgba)

add_library(retropack-runtime SHARED
    src/main/cpp/mgba-jni.c
    src/main/cpp/ringbuffer.c
)

target_link_libraries(retropack-runtime
    mgba
    GLESv2
    EGL
    OpenSLES
    android
    log
)
```

### 5.3 JNI Core Bridge (`NativeCore.kt`)
```kotlin
package com.retropack.runtime.core

object NativeCore {
    init { System.loadLibrary("retropack-runtime") }

    external fun nativeInit(internalStoragePath: String): Boolean
    external fun nativeLoadRom(romPath: String): Boolean
    external fun nativeUnloadRom()
    external fun nativeDestroy()
    external fun nativeRunFrame(): Boolean
    external fun nativeSetKeys(keyMask: Int)
    external fun nativeGetVideoBuffer(): java.nio.IntBuffer
    external fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    external fun nativeGetSramSize(): Int
    external fun nativeReadSram(outBuffer: ByteArray): Boolean
    external fun nativeWriteSram(inBuffer: ByteArray): Boolean
    external fun nativeSaveState(slot: Int, filePath: String): Boolean
    external fun nativeLoadState(slot: Int, filePath: String): Boolean
}
```

### 5.4 Video, Audio & Input Pipelines
* **Video**: OpenGL ES 2.0/3.0 SurfaceView rendering; supports `integer_fit` (pixel-perfect square scaling) and `aspect_fit` with bilinear filter.
* **Audio**: Low-latency `AudioTrack` 16-bit stereo at $44,100\text{ Hz}$; circular ring buffer with drift compensation to eliminate pops and crackles.
* **Input**: Virtual `TouchOverlayView` (custom coords, opacity, haptics) + physical Bluetooth/USB gamepad HID handler. Auto-hides virtual controls when physical gamepad buttons are pressed.

### 5.5 Durability & Save Persistence
* **Dirty-Gated Periodic Flush**: Background thread checks `isSramDirty` every 60 seconds; writes only when modified.
* **Lifecycle Flush**: Synchronous write in `onPause()` and `onStop()`.
* **Atomic POSIX fsync Sequence**:
  ```text
  1. Open FileOutputStream(filesDir/game.sav.tmp)
  2. NativeCore.nativeReadSram(buffer) -> write to output stream
  3. stream.flush()
  4. stream.fd.sync()              <-- POSIX fsync flushes OS cache to NAND flash
  5. stream.close()
  6. filesDir/game.sav.tmp.renameTo(filesDir/game.sav)  <-- Atomic directory swap
  ```

---

## 6. The 15-Step Verified Transformation Pipeline

The Build Engine executes packaging through an explicit, verified sequence:

```text
[Raw User ROM via SAF]
          │
          ▼
1. Streamed Checksums & Header Analysis (Retra)
   └── Stream CRC32, MD5, SHA-1, SHA-256; validate Nintendo logo, title, and MBC
          ▼
2. Template Integrity Verification
   └── Assert SHA-256(template.apk) == RuntimeRegistry.TRUSTED_TEMPLATES[id]
          ▼
3. Pre-Flight Package & Signer Check
   └── Check PackageManager; verify existing signing cert; compute versionCode
          ▼
4. Atomic Scratch File Preparation
   └── Allocate targetFile and scratchFile in SAME output directory (prevents EXDEV)
          ▼
5. Signature Residue Stripping
   └── Remove stale META-INF/*.SF, *.RSA, *.DSA, *.EC, MANIFEST.MF and APK Signing Block
          ▼
6. Streamed Asset Injection
   ├── Stream ROM -> assets/game.rom (Path-traversal sanitized)
   └── Stream JSON -> assets/retropack.json (schema_version: 1)
          ▼
7. Structured AXML Mutation (REAndroid/ARSCLib)
   ├── Mutate root package to com.retropack.game.<slug>_<hash10>
   ├── Inline literal android:label="<Game Title>" (Bypasses resources.arsc)
   └── Mutate versionCode and versionName
          ▼
8. Adaptive Icon Replacement (Pure Drawables)
   ├── Inject res/drawable-nodpi/ic_launcher_foreground.png (Boxart)
   └── Inject res/drawable-nodpi/ic_launcher_background.png (Background)
          ▼
9. Protected Entries Invariant Verification
   └── Assert SHA-256 of classes*.dex and lib/* match runtime.json protected_entries
          ▼
10. 16 KB Page Zipalign (com.android:zipflinger)
    └── Align all uncompressed shared libraries (lib/arm64-v8a/*.so) to 16,384 bytes
          ▼
11. Alignment Verification (Assertion)
    └── Programmatically assert (local_header_payload_offset % 16384 == 0) for all .so
          ▼
12. Cryptographic Signing (com.android.tools.build:apksig)
    └── Sign with Hybrid Keystore key applying v1, v2, v3 schemes in single pass
          ▼
13. Post-Signing Integrity Verification (ApkVerifier)
    └── Programmatically assert chunk hashes, manifest sanity, and ABI presence
          ▼
14. Atomic Finalization
    ├── Atomic filesystem rename: scratchFile.renameTo(targetFile)
    └── Fallback to copy + fsync + delete if cross-volume rename fails
          ▼
15. Emit Verified BuildResult
    └── Return artifact path, certificate fingerprints, and complete stage provenance
```
