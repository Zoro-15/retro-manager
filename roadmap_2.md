# RetroPack — Multi-Core Evolution Roadmap (`roadmap_2.md`)

> **Document Status**: Definitive Engineering Specification for Tier 2 Upstream C/C++ Core Integrations  
> **Mission**: Extend RetroPack from the verified Tier 1 foundation (GB/GBC/GBA via canonical mGBA) to full multi-system execution across all 10 target console architectures.  
> **Core Architectural Law**: Constitutional Law 20 ("Canonical Emulation Over Custom Forks") and Law 21 ("Immutable Template Isolation"). All upstream cores remain untouched submodules; platform glue resides strictly in dedicated NDK JNI bridges with 16 KB page alignment.

---

## 🤝 Active Handover Station (Current Progress & Status)

> [!IMPORTANT]
> **Active Status Summary**:
> * **Phase 7 (Nintendo DS / melonDS)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`melonDS-emu/melonDS`).
>   - C++17 JNI bridge implemented in [`melonds-jni.cpp`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-melonds/src/main/cpp/melonds-jni.cpp) with dual-screen $256 \times 384$ rendering, touch stylus coordinate engine in [`TouchOverlayView.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/input/TouchOverlayView.kt) / [`GameActivity.kt`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt), and FreeBIOS direct boot.
> * **Phase 8 (Super Nintendo / Snes9x)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`snes9xgit/snes9x`).
>   - C++17 JNI bridge implemented in [`snes9x-jni.cpp`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-snes9x/src/main/cpp/snes9x-jni.cpp) with 12-button joypad bitmask mapping, Mode 7 HiRes rendering, APU stereo resampling, and SRAM durability.
> * **Phase 9 (Sega 8/16-bit — Genesis Plus GX)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`ekeeke/Genesis-Plus-GX`).
>   - JNI bridge implemented in [`genesis-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-genesis/src/main/cpp/genesis-jni.c) with 6-button controller mapping, dynamic VDP viewport resolution detection, YM2612 FM audio ring buffer, and 16 KB page alignment.
> * **Phase 10 (NES / Famicom — FCEUmm)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`libretro/libretro-fceumm`).
>   - C JNI bridge implemented in [`fceumm-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fceumm/src/main/cpp/fceumm-jni.c) with 64-color composite NES ARGB palette rasterizer, 8-button joypad mapping with Turbo A/B, 16-bit stereo APU audio ring buffer, and 8 KB battery PRG-RAM durability.
>   - 16 KB page-size linker flags and submodule discovery configured in [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fceumm/CMakeLists.txt).
> * **Phase 11 (PC Engine / TurboGrafx-16 — Beetle PCE Fast)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`libretro/beetle-pce-fast-libretro`).
>   - C JNI bridge implemented in [`pce-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/src/main/cpp/pce-jni.c) with HuC6280 CPU, HuC6270 VDC + HuC6260 VCE 9-bit master RGB palette rasterizer ($256 \times 240$ / $512 \times 242$), 6-button Avenue Pad 6 + Turbo I/II oscillator mapping, 6-channel PSG audio ring buffer, and 2 KB BRAM durability.
>   - 16 KB page alignment configured in [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/CMakeLists.txt) and unit test suite in [`PceNativeCoreTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/src/test/kotlin/com/retropack/runtime/pce/PceNativeCoreTest.kt).
> * **Phase 12 (Sony PlayStation 1 — PCSX ReARMed)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`libretro/pcsx_rearmed`).
>   - C JNI bridge implemented in [`pcsx-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pcsx/src/main/cpp/pcsx-jni.c) with MIPS R3000A dynarec/lightrec, GTE fixed-point coprocessor, SPU ADPCM audio synthesis, HLE BIOS direct boot, DualShock 14-button + analog mapping, and 128 KB Memory Card durability.
>   - 16 KB page alignment configured in [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pcsx/CMakeLists.txt) and unit tests in [`PcsxNativeCoreTest.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pcsx/src/test/kotlin/com/retropack/runtime/pcsx/PcsxNativeCoreTest.kt).
> * **Phase 13.1 (Nintendo 64 — Mupen64Plus-Next)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`libretro/mupen64plus-libretro-nx`).
>   - C JNI bridge implemented in [`mupen64-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-mupen64/src/main/cpp/mupen64-jni.c) with VR4300 64-bit CPU, GLES3 RDP rasterizer, N64 controller mapping (C-buttons, Z-trigger), 44.1 kHz audio ring buffer, and 128 KB FlashRAM durability.
> * **Phase 13.2 (Sony PSP — PPSSPP)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`hrydgard/ppsspp`).
>   - C JNI bridge implemented in [`ppsspp-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-ppsspp/src/main/cpp/ppsspp-jni.c) with Allegrex 333 MHz MIPS CPU, VFPU vector engine, 480x272 widescreen GLES3 pipeline, PSP button bitmask mapping, and Memory Stick durability.
> * **Phase 13.3 (Arcade / Neo Geo — FinalBurn Neo)**: ✅ **BEDROCK COMPLETE**
>   - Submodule registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules) (`libretro/FBNeo`).
>   - C JNI bridge implemented in [`fbneo-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fbneo/src/main/cpp/fbneo-jni.c) with Motorola 68000 + Z80 multi-CPU architecture, Neo Geo 4-button & Arcade 6-button controller mapping, and 64 KB NVRAM durability.
> * **Phase 14 (Multi-Template Bundling & CI Verification Matrix)**: ✅ **BEDROCK COMPLETE**
>   - All 10 on-disk runtime bundle descriptors created under [`runtimes/`](file:///c:/Users/ok/Documents/retro%20manager/runtimes) (`mgba-unified`, `snes9x-unified`, `genesis-unified`, `fceumm-unified`, `pce-unified`, `fbneo-unified`, `pcsx-unified`, `mupen64-unified`, `ppsspp-unified`, `melonds-unified`).
>   - Hardened [`android.yml`](file:///c:/Users/ok/Documents/retro%20manager/.github/workflows/android.yml) and [`tests.yml`](file:///c:/Users/ok/Documents/retro%20manager/.github/workflows/tests.yml) verifying 10-core parallel AAR assembly, test coverage, and Manager APK bundle descriptor embedding.
>   - Multi-core trust anchor rotation script hardened in [`rotate-trust-anchors.sh`](file:///c:/Users/ok/Documents/retro%20manager/scripts/rotate-trust-anchors.sh).
> * **Phase 15 (Android 16 / API 36 16 KB Page Size & Durability Certification)**: ✅ **BEDROCK COMPLETE**
>   - All 10 native shared libraries (`libretropack-runtime-*.so`) link with `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
>   - 16 KB page-size ELF header verification script implemented in [`verify-16kb-alignment.sh`](file:///c:/Users/ok/Documents/retro%20manager/scripts/verify-16kb-alignment.sh) and wired into GitHub Actions CI.
>   - Crash-consistent atomic save sequence (POSIX `fsync` $\rightarrow$ atomic rename swap) verified across [`SaveManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/save/SaveManager.kt) and [`SaveStateManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/save/SaveStateManager.kt).
> * **Phase 16 (Universal Archive Ingestion & Single-Session Diagnostic Logging)**: ✅ **BEDROCK COMPLETE**
>   - Universal archive extraction engine ([`ArchiveExtractor.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/ArchiveExtractor.kt)) supporting transparent unpacking of `.zip`, `.rar`, `.7z`, and `.gz` archives, filtering noise/metadata, recursively resolving candidate ROMs, and re-validating file extensions & binary headers.
>   - Comprehensive extension catalog across all 10 architectures integrated into [`RomParser.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/RomParser.kt) and [`UriUtils.kt`](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/util/UriUtils.kt).
>   - Single-session diagnostic logger ([`AppLogger.kt`](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/util/AppLogger.kt)) persisted in App Data (`latest_session.log`), auto-purging historical sessions to store strictly the last run with fatal uncaught crash trapping and UI terminal inspection.

---

## 1. Multi-Core Architecture & Phased Integration Pipeline

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               RetroPack Manager (Kotlin / Compose M3)                   │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                          Unified Domain Core & 15-Step Packaging Pipeline               │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                  Runtime Registry & Descriptors                         │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌────────────┐ │
│ │  Phase 7:     │ │  Phase 8:     │ │  Phase 9:     │ │  Phase 10:    │ │  Phase 11: │ │
│ │  melonDS      │ │  Snes9x       │ │  Genesis+ GX  │ │  FCEUmm       │ │  BeetlePCE │ │
│ │  (NDS / DSi)  │ │  (SNES / SFC) │ │  (MD/SMS/GG)  │ │  (NES/FC/FDS) │ │  (PCE/TG16)│ │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ └────────────┘ │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐                │
│ │  Phase 12:    │ │  Phase 13.1:  │ │  Phase 13.2:  │ │  Phase 13.3:  │                │
│ │  PCSX ReARMed │ │  Mupen64Plus  │ │  PPSSPP       │ │  FBNeo        │                │
│ │  (PS1 / PSX)  │ │  (N64)        │ │  (PSP)        │ │  (Arcade)     │                │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘                │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────────────────────┐ │
│ │  Phase 14: Multi-Template Bundling & CI Verification Matrix (16 KB / Android 8-16) │ │
│ └────────────────────────────────────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────────────────────────────────┐ │
│ │  Phase 15: Android 16 (API 36) 16 KB Page Size & Durability Certification          │ │
│ └────────────────────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase Breakdown & Acceptance Gates

### 🎯 Phase 7: Nintendo DS / DSi — canonical melonDS Integration [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: Nintendo DS (`.nds`), Nintendo DSi (`.dsi`, `.srl`).
* **Upstream Core**: [`melonDS-emu/melonDS`](https://github.com/melonDS-emu/melonDS) (GPL-3.0) pinned release.
* **Architecture & Reference Strategy**:
  - Reference battle-tested architectures from `melonDS Android` and `libretro-melonDS` for robust timing, JIT memory protections, and audio drift mitigation.
  - Implement zero-dependency DirectBoot / FreeBIOS execution so commercial and homebrew NDS ROMs (e.g. *Pokemon Pearl/Diamond/Platinum*, *New Super Mario Bros*, *Mario Kart DS*) execute directly without requiring dumped proprietary BIOS firmware files.
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Wiring**: Added `melonDS` submodule under [`runtime/retropack-runtime-melonds/submodules/melonds`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-melonds/submodules/melonds) and registered in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules).
  2. **16 KB CMake Toolchain**: Hardened [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-melonds/CMakeLists.txt) compiling C++17 with `-Wl,-z,max-page-size=16384` and `HAVE_MELONDS_CORE` preprocessor bindings.
  3. **Dual-Screen Render Pipeline & Native Video Buffer**:
     - Stacked $256 \times 384$ vertical framebuffer (Top screen rows $0..191$, Bottom screen rows $192..383$).
     - Direct zero-copy `IntBuffer` plumbing via `melondsGetVideoBuffer` for OpenGL ES 2.0/3.0 SurfaceView rendering.
  4. **Touch Stylus Coordinates Engine**:
     - Implemented `onStylusTouch` callback in [`TouchOverlayView.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/input/TouchOverlayView.kt) with aspect-ratio-aware viewport hit-testing.
     - Real-time mapping from Android screen touches to NDS SPI hardware coordinates: $x \in [0, 255]$, $y \in [0, 191]$ wired directly to `MelondsNativeCore.nativeSetTouch(x, y, isTouching)` in [`GameActivity.kt`](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt).
  5. **Low-Latency Audio Ring Buffer**:
     - 16-bit stereo PCM audio streaming through circular ring buffer with drift compensation to eliminate pops and crackles.
  6. **Crash-Consistent Flash/EEPROM Durability**:
     - Supports $512\text{ KB}$ to $32\text{ MB}$ battery save persistence with atomic POSIX `fsync` lifecycle guarantees.
* **Acceptance Gate**: Homebrew ROM fixture (*NDS PoC / Dangan NDS*) and standard NDS games boot to title screen at stable 60 FPS with functional touch input, audio synchronization, and verified battery save persistence.

---

### 🎯 Phase 8: Super Nintendo (SNES) — Snes9x Integration [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: Super Nintendo / Super Famicom (`.sfc`, `.smc`, `.snes`, `.fig`).
* **Upstream Core**: [`snes9xgit/snes9x`](https://github.com/snes9xgit/snes9x) (Snes9x License / GPL).
* **Architecture & Reference Strategy**:
  - Reference upstream `snes9x` C++ core and `Snes9x EX+` Android architecture.
  - Full coprocessor support: SuperFX (Star Fox, Yoshi's Island), SA-1 (Super Mario RPG), DSP-1..4 (Super Mario Kart), S-DD1 (Street Fighter Alpha 2), Cx4 (Mega Man X2/X3), SPC7110.
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Setup & .gitmodules**: Registered `snes9x` submodule under [`runtime/retropack-runtime-snes9x/submodules/snes9x`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-snes9x/submodules/snes9x).
  2. **16 KB Page Alignment & CMake**: Configured [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-snes9x/CMakeLists.txt) compiling C++17 with `-Wl,-z,max-page-size=16384` and `HAVE_SNES9X_CORE` flags.
  3. **High-Resolution & Mode 7 GLES Rendering**:
     - Dynamically manages buffer scaling between standard $256 \times 224$ and HiRes/interlaced $512 \times 448$.
     - Zero-copy direct `java.nio.IntBuffer` mapping via `snesGetVideoBuffer` for OpenGL ES 2.0/3.0.
  4. **Joypad 12-Button Input Engine**:
     - Fast bitwise mapping in [`snes9x-jni.cpp`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-snes9x/src/main/cpp/snes9x-jni.cpp): `RetroKey` $\rightarrow$ `Movie.Pad[0]` ($A, B, X, Y, L, R, \text{Select}, \text{Start}, \text{Up}, \text{Down}, \text{Left}, \text{Right}$).
     - Responsive diamond button layout mapped via `TouchLayout.snes()`.
  5. **32 kHz -> 44.1 kHz APU Sound Mixer**:
     - Blargg 32 kHz SPC700 audio stream resampled to $44,100\text{ Hz}$ 16-bit stereo ring buffer.
  6. **Crash-Consistent SRAM Durability**:
     - $128\text{ KB}$ SRAM persistence backed by periodic dirty-gated flush and POSIX `fsync()` file replacement.
* **Acceptance Gate**: Public domain homebrew (*Classic Kong SNES / N-Warp Daisakusen*) boots, renders high-resolution fonts cleanly, and completes save/load state cycles without audio crackle.

---

### 🎯 Phase 9: Sega 8/16-bit — Genesis Plus GX Integration [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: Sega Mega Drive / Genesis (`.md`, `.smd`, `.gen`), Master System (`.sms`), Game Gear (`.gg`), SG-1000 (`.sg`).
* **Upstream Core**: [`ekeeke/Genesis-Plus-GX`](https://github.com/ekeeke/Genesis-Plus-GX) (GPL-2.0).
* **Architecture & Reference Strategy**:
  - Reference `ekeeke/Genesis-Plus-GX` upstream repository and Genesis Plus GX Android port.
  - Multi-system auto-detection: Sega Genesis/Mega Drive, Master System (SMS), Game Gear (GG), SG-1000.
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Setup & .gitmodules**: Registered `genesis-plus-gx` under [`runtime/retropack-runtime-genesis/submodules/genesis-plus-gx`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-genesis/submodules/genesis-plus-gx).
  2. **16 KB Page Alignment & CMake**: Configured [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-genesis/CMakeLists.txt) compiling with `-Wl,-z,max-page-size=16384` and `HAVE_GENESIS_CORE` flags.
  3. **Multi-Resolution VDP Viewport Engine**:
     - Dynamic viewport handling in [`genesis-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-genesis/src/main/cpp/genesis-jni.c): $320 \times 224$ (Genesis), $256 \times 192$ (SMS), $160 \times 144$ (Game Gear).
     - Zero-copy direct `java.nio.IntBuffer` mapping via `genesisGetVideoBuffer` for OpenGL ES 2.0/3.0.
  4. **6-Button & 3-Button Controller Mapping**:
     - Fast bitwise mapper: `map_retro_keys_to_genesis(mask)` $\rightarrow$ `input.pad[0]` ($A, B, C, X, Y, Z, \text{Start}, \text{Mode}, \text{D-Pad}$).
  5. **YM2612 FM Synthesis & SN76489 PSG Audio Engine**:
     - High-fidelity FM synthesis streamed to the 16-bit stereo circular ring buffer.
  6. **Crash-Consistent SRAM & Save State Durability**:
     - $64\text{ KB}$ SRAM persistence backed by periodic dirty-gated flush and POSIX `fsync()` file replacement.
* **Acceptance Gate**: Genesis homebrew test (*Old Towers MD*) runs at locked 60 FPS with accurate FM audio synthesis and zero frame drops.

---

### 🎯 Phase 10: NES / Famicom — FCEUmm Integration [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: NES / Famicom (`.nes`, `.fds`, `.unf`).
* **Upstream Core**: [`libretro/libretro-fceumm`](https://github.com/libretro/libretro-fceumm) (GPL-2.0).
* **Architecture & Reference Strategy**:
  - Reference `libretro/libretro-fceumm` upstream core and FCEUX Android architecture.
  - Comprehensive mapper engine: iNES / NES 2.0 mappers (Mappers 0 to 255+, MMC1, MMC3, MMC5, VRC6, Sunsoft).
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Setup & .gitmodules**: Registered `fceumm` under [`runtime/retropack-runtime-fceumm/submodules/fceumm`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fceumm/submodules/fceumm).
  2. **16 KB Page Alignment & CMake**: Configured [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fceumm/CMakeLists.txt) with `-Wl,-z,max-page-size=16384` and `HAVE_FCEUMM_CORE` flags.
  3. **64-Color Composite ARGB Palette Rasterizer**:
     - Built-in 2C02 composite color palette in [`fceumm-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-fceumm/src/main/cpp/fceumm-jni.c) translating 8-bit PPU indices to 32-bit ARGB video buffer at $256 \times 240$.
     - Zero-copy direct `java.nio.IntBuffer` mapping via `fceuGetVideoBuffer` for OpenGL ES 2.0/3.0.
  4. **8-Button Joypad & Turbo Button Engine**:
     - Bitwise mapping: `map_retro_keys_to_nes(mask)` $\rightarrow$ standard NES joypad ($A, B, \text{Select}, \text{Start}, \text{Up}, \text{Down}, \text{Left}, \text{Right}$) plus Turbo X $\rightarrow$ A, Turbo Y $\rightarrow$ B.
  5. **44.1 kHz APU Audio Stream & Ring Buffer**:
     - Pulse 1/2, Triangle, Noise, and DPCM channels streamed via 16-bit stereo PCM circular ring buffer with clamp protection.
  6. **Crash-Consistent 8 KB PRG-RAM Battery Durability**:
     - Battery PRG-RAM persistence with POSIX `fsync()` atomic replacement and savestate serialization.
* **Acceptance Gate**: NES homebrew (*Micro Mages Demo / Blade Buster*) executes with correct palette rendering, responsive controls, and battery SRAM persistence.

---

### 🎯 Phase 11: PC Engine / TurboGrafx-16 — Beetle PCE Fast [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: PC Engine / TG-16 / SuperGrafx / PCE CD-ROM² (`.pce`, `.sgx`, `.cue`, `.iso`, `.chd`, `.bin`).
* **Upstream Core**: [`libretro/beetle-pce-fast-libretro`](https://github.com/libretro/beetle-pce-fast-libretro) (GPL-2.0).
* **Architecture & Reference Strategy**:
  - Reference Mednafen PCE Fast / Beetle PCE Fast architecture and `PCE.emu` Android implementation.
  - Hudson Soft HuC6280 8-bit CPU (custom 65C02 hybrid clocked at 1.79 / 7.16 MHz) with built-in MMU (8x 8 KB logical bank windows) and integrated 6-channel PSG sound synthesizer.
  - Hudson Soft HuC6270 16-bit Video Display Controller (VDC) with up to 64 KB VRAM, $8 \times 8$ tilemaps, hardware sprite zooming/flipping, scanline IRQ timer, and HuC6260 Video Color Encoder (VCE).
  - SuperGrafx dual HuC6270 VDCs + HuC6202 Video Priority Controller (VPC) multi-plane background compositing.
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Setup & .gitmodules**:
     - Registered `beetle-pce-fast` under [`runtime/retropack-runtime-pce/submodules/beetle-pce-fast`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/submodules/beetle-pce-fast) in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules).
  2. **16 KB Page Alignment & CMake Toolchain**:
     - Configured [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/CMakeLists.txt) with `-Wl,-z,max-page-size=16384` and `HAVE_BEETLE_PCE_CORE` definitions and modular include directories.
  3. **9-Bit Master RGB Palette Rasterizer & Dynamic Viewports**:
     - 512-entry lookup table mapping 9-bit PCE colors (3-bit Red, 3-bit Green, 3-bit Blue) directly to 32-bit ARGB8888.
     - Dynamic viewport support handling $256 \times 240$ (standard), $341 \times 240$ (mid-res), and $512 \times 242$ (high-res/overscan) modes.
     - Direct zero-copy `java.nio.IntBuffer` mapping via `pceGetVideoBuffer` for OpenGL ES 2.0/3.0.
  4. **6-Button Avenue Pad 6 & Dual Turbo Oscillator Engine**:
     - Bitwise mapper in [`pce-jni.c`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pce/src/main/cpp/pce-jni.c): `RetroKey` $\rightarrow$ PC Engine Joypad bitmask ($I, II, III, IV, V, VI, \text{Select}, \text{Run}, \text{D-Pad}$).
     - Automated Turbo I / Turbo II pulse generator alternating button state on every 2nd frame for hardware-accurate rapid fire.
  5. **44.1 kHz 6-Channel PSG Audio Ring Buffer**:
     - 6-channel 5-bit wavetable synthesis + noise generators streamed to the 16-bit stereo circular ring buffer.
  6. **Crash-Consistent 2 KB BRAM Durability**:
     - 2 KB (`0x800` bytes) internal Backup RAM (BRAM) and external Memory Base 128 persistence backed by atomic POSIX `fsync` file swap.
* **Acceptance Gate**: PCE homebrew (*Reflectron PCE / FX-Unit Demo*) runs at locked 60 FPS with accurate PSG audio and durable BRAM persistence.

---

### 🎯 Phase 12: Sony PlayStation 1 — PCSX ReARMed [STATUS: BEDROCK COMPLETE]
* **Target Platforms**: PS1 / PSX (`.cue`, `.iso`, `.chd`, `.pbp`, `.bin`).
* **Upstream Core**: [`libretro/pcsx_rearmed`](https://github.com/libretro/pcsx_rearmed) (GPL-2.0).
* **Architecture & Reference Strategy**:
  - Reference PCSX ReARMed upstream core and `ePSXe` / `DuckStation` Android design patterns.
  - 32-bit MIPS R3000A CPU (clocked at 33.8688 MHz) with hardware GTE (Geometry Transformation Engine) coprocessor.
  - SPU (Sound Processing Unit) 24-channel ADPCM sound synthesis + CD-DA streaming.
  - High-Level Emulation (HLE) BIOS support enabling immediate boot without requiring proprietary `scph5501.bin` dumps.
* **Technical Deliverables & Subsystem Implementation**:
  1. **Submodule Setup & .gitmodules**:
     - Registered `pcsx_rearmed` under [`runtime/retropack-runtime-pcsx/submodules/pcsx_rearmed`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pcsx/submodules/pcsx_rearmed) in [`.gitmodules`](file:///c:/Users/ok/Documents/retro%20manager/.gitmodules).
  2. **16 KB ARM Dynarec & CMake Toolchain**:
     - Hardened [`CMakeLists.txt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-pcsx/CMakeLists.txt) compiling `lightrec` / ARM64 dynamic recompiler with `-Wl,-z,max-page-size=16384` and `HAVE_PCSX_CORE` flags.
  3. **PSX GPU & GTE 3D Polygon Rasterization**:
     - 16-bit/24-bit VRAM rendering with dithering, texture mapping, Gouraud shading, semi-transparency modes, and dynamic resolution switching ($320 \times 240$ to $640 \times 480$).
     - Direct zero-copy `java.nio.IntBuffer` mapping via `pcsxGetVideoBuffer` for OpenGL ES 2.0/3.0.
  4. **DualShock 14-Button & Dual Analog Input Engine**:
     - Fast bitwise mapping: `RetroKey` $\rightarrow$ PSX Joypad ($Cross, Circle, Square, Triangle, L1, R1, L2, R2, L3, R3, \text{Select}, \text{Start}, \text{D-Pad}$).
     - Dual analog thumbstick normalized coordinate mapping $(X \in [-1.0, 1.0], Y \in [-1.0, 1.0])$ to PSX 8-bit unsigned axes $[0, 255]$.
  5. **SPU 24-Channel ADPCM Audio Ring Buffer**:
     - 44,100 Hz 16-bit stereo CD-quality audio stream resampled into the circular ring buffer.
  6. **128 KB Memory Card & Save State Durability**:
     - 128 KB Memory Card 1 (`.mcd`/`.srm`) battery save persistence with atomic POSIX `fsync` lifecycle guarantees.
* **Acceptance Gate**: PS1 public domain ISO (*Magic Castle / PSXSDK 3D Demo*) boots directly with HLE BIOS, renders 3D textured polygons cleanly, and completes save/load state cycles at steady 60 FPS.

---

### 🎯 Phase 13: High-Performance Consoles (N64, PSP, Arcade) [STATUS: BEDROCK COMPLETE]

#### 🎮 Part 13.1 — Nintendo 64: Mupen64Plus-Next Integration
* **Target Platforms**: Nintendo 64 (`.z64`, `.n64`, `.v64`).
* **Upstream Core**: [`libretro/mupen64plus-libretro-nx`](https://github.com/libretro/mupen64plus-libretro-nx) (GPL-2.0).
* **Architecture**: 64-bit NEC VR4300 CPU (93.75 MHz) + Reality Coprocessor (RCP) with RSP vector signal processor and RDP rasterizer plugin (Glide64mk2 / Angrylion GLES3).
* **Controls & Durability**: N64 Analog stick, 4 C-buttons (C-Up, C-Down, C-Left, C-Right), Z-trigger, L/R bumpers, Start, and 128 KB FlashRAM / Controller Pak persistence.

#### 🎮 Part 13.2 — Sony PSP: PPSSPP Integration
* **Target Platforms**: PlayStation Portable (`.iso`, `.cso`, `.chd`, `.pbp`).
* **Upstream Core**: [`hrydgard/ppsspp`](https://github.com/hrydgard/ppsspp) (GPL-2.0).
* **Architecture**: 333 MHz MIPS Allegrex CPU with VFPU vector unit and GLES3 / Vulkan hardware accelerated rendering with 1x-4x internal resolution upscaling ($480 \times 272 \rightarrow 1920 \times 1088$).
* **Controls & Durability**: Analog nub, PlayStation face buttons, L/R triggers, Memory Stick Duo persistence.

#### 🎮 Part 13.3 — Arcade / Neo Geo: FinalBurn Neo (FBNeo) Integration
* **Target Platforms**: Neo Geo MVS/AES, CPS-1, CPS-2, CPS-3, Arcade (`.zip`, `.7z`, `.neo`).
* **Upstream Core**: [`libretro/FBNeo`](https://github.com/libretro/FBNeo) (GPL-2.0 non-commercial).
* **Architecture**: Dual Motorola 68000 + Zilog Z80 multi-CPU architecture with YM2610 / Yamaha FM sound matrix and hardware tile/sprite rendering.
* **Controls & Durability**: Neo Geo 4-button ($A, B, C, D$) and 6-button Arcade layouts with NVRAM / EEPROM durability.

---

### 🎯 Phase 14: Multi-Template Bundling & Production Packaging [STATUS: BEDROCK COMPLETE]
1. **Dynamic Template Generation & Descriptors**:
   - All 10 on-disk runtime bundle descriptors created under [`runtimes/`](file:///c:/Users/ok/Documents/retro%20manager/runtimes) (`mgba-unified`, `snes9x-unified`, `genesis-unified`, `fceumm-unified`, `pce-unified`, `fbneo-unified`, `pcsx-unified`, `mupen64-unified`, `ppsspp-unified`, `melonds-unified`).
   - Store pinned templates in `runtimes/<runtime-id>/template.apk`.
2. **Bytecode Trust Anchor Synchronization**:
   - Hardened [`rotate-trust-anchors.sh`](file:///c:/Users/ok/Documents/retro%20manager/scripts/rotate-trust-anchors.sh) to calculate and synchronize whole-APK digests into [`RuntimeRegistry.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/runtime/RuntimeRegistry.kt) for all 10 architectures.
3. **Manager Multi-Core Provisioner**:
   - Updated [`RuntimeProvisioner.kt`](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/runtime/RuntimeProvisioner.kt) to provision the exact template matching the selected console, with seamless fallback to the universal multi-core template.
4. **CI Matrix Conformance**:
   - Hardened GitHub Actions CI ([`android.yml`](file:///c:/Users/ok/Documents/retro%20manager/.github/workflows/android.yml) and [`tests.yml`](file:///c:/Users/ok/Documents/retro%20manager/.github/workflows/tests.yml)) to compile and test all 10 runtime AARs and verify template descriptors inside the Manager APK package.

---

### 🎯 Phase 15: Android 16 (API 36) Page Size & Durability Certification [STATUS: BEDROCK COMPLETE]
1. **16 KB Memory Page Verification**:
   - All 10 native shared libraries (`libretropack-runtime-*.so`) link with `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
   - 16 KB page-size ELF header verification script implemented in [`verify-16kb-alignment.sh`](file:///c:/Users/ok/Documents/retro%20manager/scripts/verify-16kb-alignment.sh) and wired into GitHub Actions CI.
2. **Crash-Consistent Atomic File Operations**:
   - Save states and SRAM / BRAM persist via `.tmp` file write $\rightarrow$ `fsync()` $\rightarrow$ atomic rename swap verified across [`SaveManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/save/SaveManager.kt) and [`SaveStateManager.kt`](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/save/SaveStateManager.kt) to prevent corruption on sudden process termination.
