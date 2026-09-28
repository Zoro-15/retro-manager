# The Way Ahead — RetroPack Future Architecture & Migration Plan (`thewayahead.md`)

> **Document Status**: Active Strategic Blueprint & Single Source of Truth for Modernization  
> **Target Audience**: Core Developers and Successive AI Agents

---

## 0. Operational Context & Agent Rules (CRITICAL)

> [!IMPORTANT]
> **Active Branch**: All work, refactoring, and commits must take place exclusively on branch **`v2`**. Never push breaking experimental changes to `main`.

> [!CAUTION]
> **No Heavy Local Builds / Verification**: The user's local machine is resource-constrained.
> - **DO NOT** run heavy local Gradle commands (`./gradlew assembleDebug`, NDK multi-core compilation, emulator runs, or complete test suites) on the local device. Doing so causes system freezes and wastes tokens/time.
> - **Exemptions**: Small, lightweight tool calls (e.g. `git status`, `git diff`, file inspections, quick one-file syntax checks) are permitted.
> - **CI-Driven Verification**: Delegate all heavy compilation, building, and page-alignment verification to GitHub Actions CI by pushing to `origin/v2`. Inspect results via CI workflow outputs and diagnostic reports.

---

## 1. Executive Summary & Root Cause Analysis

### The Problem with the Legacy Stale Architecture
* **10 Bespoke JNI Bridges**: Each emulator core (`retropack-runtime-mgba`, `retropack-runtime-snes9x`, `retropack-runtime-fbneo`, etc.) implemented its own custom `*-jni.c` / `*-jni.cpp` file with duplicated ring buffer management, surface rendering, input polling, and state handling.
* **10 Handcrafted CMake Scripts**: Manually scraping thousands of upstream C/C++ files led to broken compiler macros (e.g., `-DINLINE=inline` breaking Musashi CPU in FBNeo), missing generated driver files, and incomplete header stubs (e.g., `libchdr/chd.h` in PPSSPP).
* **Monolithic Template APK Coupling**: `template-apk` declared dependencies on all 10 runtime modules. If a single core failed to compile, the entire build aborted, blocking all standalone game APKs.
* **Severe Build Bloat**: Building the full project compiled over 10 million lines of C/C++ on every commit, taking 30–45 minutes and exceeding CI limits.

### The Modern Solution
Transition completely to a **Universal Libretro C-Host Architecture** inspired by battle-tested implementations like **Lemuroid**:
1. **Universal Libretro Host**: **ONE** single native host (`retropack-runtime-common`) that dynamically loads **ANY** standard Libretro core via `dlopen()` / `dlsym()`.
2. **Decoupled Prebuilt Cores**: Standalone, versioned, 16 KB-aligned `libretro_<core>.so` binaries built in isolated jobs or sourced from upstream releases.
3. **Lean Per-Game APK**: Output APKs contain *only* the single core `.so` needed for the target game (reducing APK sizes from ~50 MB down to ~3–8 MB).

---

## 2. Open-Source Reference Repositories

When implementing the universal runner and Android lifecycle glue, refer directly to these proven open-source implementations:

| Repository | License | Key Reference Value |
| :--- | :--- | :--- |
| [Swordfish90/Lemuroid](https://github.com/Swordfish90/Lemuroid) | GPLv3 | **Top Recommendation**. Clean Kotlin + Compose frontend with a unified `lemuroid-core` JNI bridge. Demonstrates ideal audio/video threading, touch overlay, AAudio/OpenSL ES, and save-state serialization. |
| [libretro/RetroArch](https://github.com/libretro/RetroArch) | GPLv3 | Canonical reference for Libretro frontend implementation (`input/`, `audio/`, `video/drivers/` for Android GLES/Vulkan). |
| [libretro/libretro-samples](https://github.com/libretro/libretro-samples) | MIT/Public Domain | Minimal ~200-line C frontend examples demonstrating the raw Libretro function pointer lifecycle. |

---

## 3. Architecture Comparison (Before vs. After)

```text
BEFORE (Legacy Fragile Monolith):
┌─────────────────────────────────────────────────────────────────────────────┐
│ 10 Submodules + 10 bespoke JNI files + 10 handcrafted CMake scripts         │
│ (Heavy monorepo, 45-min builds, fragile defines, cascading breakage)        │
└─────────────────────────────────────────────────────────────────────────────┘

AFTER (Modern Universal Libretro Architecture):
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Manager App (Jetpack Compose, ROM Scraping & Declarative Build Engine)   │
├─────────────────────────────────────────────────────────────────────────────┤
│ 2. Standalone Runner Shell (template-apk)                                   │
│    └── SurfaceView / TextureView + AAudio / OpenSL ES                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ 3. Universal Libretro Host (retropack-runtime-common)                       │
│    └── Native JNI: libretro_host.c (dlopen / dlsym core)                    │
│    └── Audio RingBuffer + OpenGL ES 2/3 Blitter + Atomic Input Poller       │
├─────────────────────────────────────────────────────────────────────────────┤
│ 4. Pluggable Native Core Layer (Standard Libretro C ABI)                    │
│    ├── libretro_mgba.so                                                     │
│    ├── libretro_snes9x.so                                                   │
│    ├── libretro_genesis_plus_gx.so                                          │
│    ├── libretro_pcsx_rearmed.so                                             │
│    └── [Any future core without writing new JNI code]                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Comparative Metrics

| Metric | Before (Bespoke Monolith) | After (Universal Libretro Host) |
| :--- | :--- | :--- |
| **CI Build Time** | 30–45 minutes | **< 1 minute** |
| **Adding a New Console** | 2–3 days of wrestling CMake & custom JNI | **5 minutes** (drop `.so` into `runtimes/`) |
| **Output APK Size** | ~40–50 MB (all 10 cores bundled) | **~3–8 MB** (only the game's core) |
| **Code Fragility** | 10 JNI files + 10 CMake files | **1 Universal C++ Host** |
| **Upstream Updates** | High risk of macro/build breakage | **Zero risk** (isolated binary update) |

---

## 4. Universal Libretro Host Specification

The unified host in `retropack-runtime-common` fulfills the existing Kotlin contract [NativeCoreBridge.kt](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/core/NativeCoreBridge.kt).

### Function Pointer Table (`dlsym` bindings)
```c
struct LibretroCore {
    void* handle;
    void (*retro_init)(void);
    void (*retro_deinit)(void);
    unsigned (*retro_api_version)(void);
    void (*retro_get_system_info)(struct retro_system_info *info);
    void (*retro_get_system_av_info)(struct retro_system_av_info *info);
    void (*retro_set_environment)(retro_environment_t);
    void (*retro_set_video_refresh)(retro_video_refresh_t);
    void (*retro_set_audio_sample)(retro_audio_sample_t);
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*retro_set_input_poll)(retro_input_poll_t);
    void (*retro_set_input_state)(retro_input_state_t);
    void (*retro_set_controller_port_device)(unsigned port, unsigned device);
    void (*retro_reset)(void);
    void (*retro_run)(void);
    size_t (*retro_serialize_size)(void);
    bool (*retro_serialize)(void *data, size_t size);
    bool (*retro_unserialize)(const void *data, size_t size);
    bool (*retro_load_game)(const struct retro_game_info *game);
    void (*retro_unload_game)(void);
    void *(*retro_get_memory_data)(unsigned id);
    size_t (*retro_get_memory_size)(unsigned id);
};
```

### Core Subsystems
1. **Video Pipeline**:
   - Handles `RETRO_PIXEL_FORMAT_0RGB1555`, `RETRO_PIXEL_FORMAT_XRGB8888`, and `RETRO_PIXEL_FORMAT_RGB565`.
   - Blits directly to OpenGL ES 2/3 texture or `AHardwareBuffer`.
2. **Audio Pipeline**:
   - `retro_audio_sample_batch` writes interleaved 16-bit stereo samples directly into `RingBuffer`.
   - Consumed by `RetroAudioPlayer` (AAudio / OpenSL ES) at low latency.
3. **Input Pipeline**:
   - `retro_input_state` maps RetroPad digital masks and analog stick coordinates (`RetroKey`).
4. **Durability & Saves**:
   - Battery saves (SRAM) are synchronized via `retro_get_memory_data(RETRO_MEMORY_SAVE_RAM)`.
   - Savestates are serialized via `retro_serialize` / `retro_unserialize`.

---

## 5. Migration Roadmap (4-Phase Execution)

### Phase 1: Universal Libretro Host Implementation
* Create `retropack-runtime-common/src/main/cpp/libretro_host.c`.
* Implement Libretro frontend callbacks (environment, video, audio, input, memory).
* Wire JNI exports to `com.retropack.runtime.core.UniversalLibretroCore` implementing `NativeCoreBridge`.

### Phase 2: Decoupled Core Artifact Staging
* Stage verified, 16 KB page-aligned Libretro `.so` files into `runtimes/<core-id>/lib/<abi>/libretro_<core-id>.so`.
* Ensure every `.so` passes the 16 KB ELF alignment verification:
  `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.

### Phase 3: Template APK & Packaging Refactoring
* Update `template-apk/build.gradle.kts` to depend **only** on `retropack-runtime-common`.
* Update the Build Engine (`core/`) to inject only the required single `.so` into `lib/<abi>/` of the generated game APK.
* `GameActivity` dynamically loads the injected core via `UniversalLibretroCore.loadCore("libretro_<core>.so")`.

### Phase 4: Purge Stale Bespoke Subprojects
* Delete the 10 legacy subprojects (`retropack-runtime-fbneo`, `retropack-runtime-mupen64`, `retropack-runtime-ppsspp`, etc.).
* Remove submodule entries from `.gitmodules`.
* Simplify `settings.gradle.kts` to `:app`, `:core`, `:template-apk`, and `:runtime:retropack-runtime-common`.

---

## 6. Core Selection Matrix

| Console | Recommended Core | ROM Extensions | Reference Notes |
| :--- | :--- | :--- | :--- |
| **GBA** | `mGBA` | `.gba`, `.bin` | Gold standard for accuracy & performance |
| **SNES** | `Snes9x` | `.sfc`, `.smc` | High compatibility, low resource footprint |
| **Genesis / MD / MS / GG** | `Genesis Plus GX` | `.md`, `.gen`, `.smd` | Cycle-accurate, lightweight |
| **NES** | `FCEUmm` | `.nes`, `.fds` | Fast mapper support |
| **PC Engine / TG16** | `Beetle PCE Fast` | `.pce`, `.sgx` | Fast and accurate |
| **PS1** | `PCSX ReARMed` | `.iso`, `.bin`, `.cue`, `.chd` | Optimized ARM NEON dynarec |
| **NDS** | `melonDS` | `.nds` | Accurate 2D/3D dual-screen renderer |
| **N64** | `Mupen64Plus-Next` | `.z64`, `.n64`, `.v64` | Libretro GLES surface wrapper |
| **Arcade / Neo-Geo** | `FBNeo` (Libretro) | `.zip`, `.neo` | Lean Neo-Geo driver configuration |
| **PSP** | `PPSSPP` (Libretro) | `.iso`, `.cso`, `.chd` | Libretro standalone core wrapper |

---

## 7. Guidelines for Successive Agents
* **Do not create new per-emulator JNI bridges**: All platform glue belongs in the universal Libretro host in `retropack-runtime-common`.
* **Keep Core builds isolated**: Changes to one core's build script must never block compilation of the Manager or other cores.
* **Preserve Android 15 Invariants**: Always verify 16 KB page-size alignment on any output native binary before deployment.
