# The Way Ahead — RetroPack Future Architecture & Migration Plan (`thewayahead.md`)

> **Role**: Minimal, high-density blueprint for developers and AI agents to execute the next-generation architecture of RetroPack.

---

## 1. Executive Summary & Problem Statement

### The Problem
* **Current State**: 10 separate handwritten JNI bridges (`*-jni.c`), 10 handcrafted `CMakeLists.txt` scraping upstream C/C++ trees, and a monolithic `template-apk` that fails if any single core has a linker error.
* **Result**: High build fragility, redundant maintenance, compile timeouts, and difficult upstream updates.

### The Solution: 3 Architectural Pillars
1. **Unified Libretro C-Host**: One rock-solid JNI host (`retropack-libretro-host.c`) that runs **any** Libretro core via the standard C-ABI (`retro_init`, `retro_load_game`, `retro_run`, etc.).
2. **Decoupled Prebuilt Core Artifacts**: Build cores in isolated CI pipelines or consume hash-pinned, 16 KB-aligned Libretro Android binaries (`.so` / `.aar`), reducing main project build time from 30+ minutes to < 1 minute.
3. **Lean Per-Game Template APK**: Standalone generated APKs bundle only the single required emulator core `.so` instead of all 10 cores.

---

## 2. Best Open-Source Reference Repositories

When implementing the universal runner and Android lifecycle glue, refer directly to these proven open-source implementations:

| Repository | License | Key Reference Value |
| :--- | :--- | :--- |
| [Swordfish90/Lemuroid](https://github.com/Swordfish90/Lemuroid) | GPLv3 | **Top Recommendation**. Clean modern Kotlin + Jetpack Compose Android frontend with a unified libretro JNI bridge (`lemuroid-core`). Shows ideal audio/video threading, touch overlay, and save management. |
| [libretro/RetroArch](https://github.com/libretro/RetroArch) | GPLv3 | Canonical reference for Libretro frontend implementation (`input/`, `audio/`, `video/drivers/` for Android GLES/Vulkan). |
| [libretro/libretro-samples](https://github.com/libretro/libretro-samples) | MIT/Public Domain | Minimal ~200-line C frontend examples demonstrating the raw Libretro function pointer lifecycle. |

---

## 3. Target Architecture & Layer Design

```text
┌─────────────────────────────────────────────────────────────┐
│ 1. Manager App (Jetpack Compose, ROM Scraping & Packager)   │
├─────────────────────────────────────────────────────────────┤
│ 2. Standalone Runner Shell (template-apk)                   │
│    └── SurfaceView / TextureView + AAudio / OpenSL ES       │
├─────────────────────────────────────────────────────────────┤
│ 3. Unified Libretro Host (retropack-runtime-common)         │
│    └── Native JNI: libretro-host.c (dlopen / dlsym core)    │
│    └── Audio RingBuffer + OpenGL ES 2/3 Blitter             │
├─────────────────────────────────────────────────────────────┤
│ 4. Pluggable Native Core Layer (Standard Libretro ABI)      │
│    ├── libretro_mgba.so                                     │
│    ├── libretro_snes9x.so                                   │
│    ├── libretro_genesis_plus_gx.so                          │
│    ├── libretro_pcsx_rearmed.so                             │
│    └── [Any future core without writing new JNI code]       │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Migration Execution Plan (Step-by-Step)

### Phase 1: Universal Libretro Host Implementation
* Create `retropack-runtime-common/src/main/cpp/libretro_host.c`.
* Implement the Libretro frontend interface:
  - `retro_environment_t` handler (pixel formats, variables, geometry).
  - `retro_video_refresh_t` -> uploads to GL texture.
  - `retro_audio_sample_batch_t` -> writes to `RingBuffer`.
  - `retro_input_poll_t` / `retro_input_state_t` -> reads atomic touch/gamepad bitmask.
  - `retro_serialize` / `retro_unserialize` -> state management.
* Expose unified JNI methods in Kotlin:
  ```kotlin
  object LibretroBridge {
      external fun loadCore(corePath: String): Boolean
      external fun loadGame(romBytes: ByteArray, romPath: String): Boolean
      external fun runFrame(): Unit
      external fun setInputState(port: Int, keyMask: Int, stickX: Short, stickY: Short)
      external fun saveState(): ByteArray?
      external fun loadState(data: ByteArray): Boolean
      external fun destroy(): Unit
  }
  ```

### Phase 2: Decouple & Package Runtimes
* Build or stage cores as standalone `runtimes/<core-id>/lib/<abi>/libretro_<core-id>.so`.
* Ensure each `.so` passes the 16 KB ELF alignment gate:
  `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
* Update `template-apk` to load the active core dynamically from `lib/<abi>/` or assets.

### Phase 3: Deprecate Legacy Bespoke Bridges
* Remove the 10 legacy per-emulator `*-jni.c` / `*-jni.cpp` files.
* Remove bloated subproject CMake scraping configurations once unified host is active.

---

## 5. Core Selection Matrix

| Console | Recommended Core | Format Support | Status |
| :--- | :--- | :--- | :--- |
| **GBA** | `mGBA` | `.gba`, `.bin` | Retain (Gold standard) |
| **SNES** | `Snes9x` | `.sfc`, `.smc` | Retain (Fast & Accurate) |
| **Genesis / MD / MS / GG** | `Genesis Plus GX` | `.md`, `.gen`, `.smd` | Retain (Clean cycle-accurate) |
| **NES** | `FCEUmm` | `.nes`, `.fds` | Retain (Fast mapper support) |
| **PC Engine / TG16** | `Beetle PCE Fast` | `.pce`, `.sgx` | Retain |
| **PS1** | `PCSX ReARMed` | `.iso`, `.bin`, `.cue`, `.chd` | Retain (ARM NEON dynarec) |
| **NDS** | `melonDS` | `.nds` | Retain (Fast 2D/3D) |
| **N64** | `Mupen64Plus-Next` | `.z64`, `.n64`, `.v64` | Retain (Libretro ABI) |
| **Arcade / Neo-Geo** | `FBNeo` (Libretro) | `.zip`, `.neo` | Compile lean NeoGeo driver set |
| **PSP** | `PPSSPP` (Libretro) | `.iso`, `.cso`, `.chd` | Retain via Libretro wrapper |

---

## 6. Guidelines for Successive Agents
* **Do not create new per-emulator JNI bridges**: All platform glue belongs in the universal Libretro host.
* **Keep Core builds isolated**: Changes to one core's submodule or build script must never block compilation of the Manager or other cores.
* **Preserve Android 15 Invariants**: Always verify 16 KB page-size alignment on any output native binary before deployment.
