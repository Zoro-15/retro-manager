# RetroPack — Pure C++ NativeActivity Migration Blueprint (`plan2.md`)

> **Document Status**: Multi-Agent Strategic Blueprint & Phased Execution Plan (v2.1)  
> **Target Branch**: `v2`  
> **Core Strategy**: Retain the Android Manager App (`app/`) and Desktop Python CLI (`scripts/builder.py`), completely drop the legacy Kotlin `GameActivity`, and transition to pure C++ `NativeActivity` (`android:hasCode="false"`) standalone game APKs.

---

## 1. Multi-Agent Phased Execution Structure

This plan is strictly partitioned into **5 Self-Contained Phases / Sub-Phases** designed for successive AI agents. Each phase defines its **Prerequisites**, **Source Files**, **Implementation Tasks**, **Acceptance Tests**, and **Handover Artifacts**.

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  PHASE 1A (Part 1): Native Engine Bedrock & Dynamic Libretro Host Bridge               │
│  • NDK CMake build configuration (libretro_engine.so, 16 KB page-aligned)              │
│  • android_native_app_glue lifecycle engine & android_main loop (main.cpp)             │
│  • Dynamic Libretro core symbol dispatch & ABI bridge (libretro_bridge.hpp/.cpp)        │
│  • Direct APK asset ROM reader (AAssetManager streaming assets/rom.bin)                │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 1B (Part 2): Hardware Accelerated Video (GLES2) & Low-Latency Audio (AAudio)    │
│  • EGL surface initialization & OpenGL ES 2.0 texture quad blitter (gles_renderer)     │
│  • Aspect ratio preservation (3:2 GBA, 4:3 SNES/NES/Genesis) & pixel conversion        │
│  • AAudio low-latency exclusive output stream & lock-free SPSC ring buffer             │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 2: In-Engine Touch Controls, OSD Menu & State Persistence                       │
│  • Rebuilt AInputQueue multi-touch virtual pad (D-Pad, A/B/X/Y/L/R/Start/Select)       │
│  • In-engine floating OSD pause menu (Save/Load States 1–5, Fast-Forward 2x–8x, Exit)  │
│  • POSIX fsync battery SRAM autosave & state serialization                             │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 3: Zero-Code Base Template APK Assembly & Manifest Stripping                    │
│  • Purge legacy template-apk Kotlin code (GameActivity.kt & Java View glue)            │
│  • Assemble stripped AndroidManifest.xml (android:hasCode="false")                     │
│  • Pre-compile universal base templates (template_gba.apk, template_snes.apk, etc.)    │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 4: Dual Packaging Engine Integration & End-to-End Certification                 │
│  • Update Kotlin BuildEngine.kt (Android app on-device sub-second injector)            │
│  • Implement scripts/builder.py (Desktop & CI Python repackager)                       │
│  • End-to-end verification (16 KB alignment, zero classes.dex, <60ms boot latency)     │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1A (Part 1): Native Engine Bedrock & Dynamic Libretro Host Bridge

### 2.1 Scope & Objective
Establish the pure C++ `NativeActivity` engine bedrock (`native_engine/`). Configure the CMake build system for `libretro_engine.so` with strict Android 15+ 16 KB ELF page alignment, implement the `android_native_app_glue` lifecycle event loop (`main.cpp`), and construct the dynamic Libretro ABI host bridge (`libretro_bridge.hpp` / `libretro_bridge.cpp`) capable of dynamically binding core symbols via `dlopen`/`dlsym`, processing Libretro environment callbacks, and streaming ROM payloads directly from APK `AAssetManager`.

### 2.2 Files to Create / Modify
* `native_engine/CMakeLists.txt`
* `native_engine/src/core/libretro.h`
* `native_engine/src/core/libretro_bridge.hpp` & `native_engine/src/core/libretro_bridge.cpp`
* `native_engine/src/main.cpp`

### 2.3 Detailed Task Checklist
1. **NDK CMake Build Configuration (`native_engine/CMakeLists.txt`)**:
   * Shared library target: `libretro_engine.so`.
   * Standard: C++17 / C11, PIC enabled.
   * Compulsory 16 KB ELF page alignment linker flags:
     `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
   * Include Android NDK libraries: `android`, `log`, `EGL`, `GLESv2`, `aaudio`, `dl`.
   * Optimize: `-O3 -flto -fvisibility=hidden`.
2. **Dynamic Libretro Core Loader & ABI Bridge (`libretro_bridge.hpp` & `libretro_bridge.cpp`)**:
   * Dynamic loader resolving all mandatory standard Libretro function pointers (`retro_init`, `retro_deinit`, `retro_load_game`, `retro_unload_game`, `retro_run`, `retro_reset`, `retro_get_system_info`, `retro_get_system_av_info`, `retro_set_environment`, `retro_set_video_refresh`, `retro_set_audio_sample`, `retro_set_audio_sample_batch`, `retro_set_input_poll`, `retro_set_input_state`, `retro_serialize_size`, `retro_serialize`, `retro_unserialize`, `retro_get_memory_data`, `retro_get_memory_size`).
   * Complete Libretro environment callback dispatcher:
     * `RETRO_ENVIRONMENT_SET_PIXEL_FORMAT` (0RGB1555, RGB565, XRGB8888)
     * `RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY` & `RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY`
     * `RETRO_ENVIRONMENT_GET_VARIABLE` & `RETRO_ENVIRONMENT_SET_VARIABLES`
     * `RETRO_ENVIRONMENT_GET_LOG_INTERFACE` (forwarded to Android logcat)
     * `RETRO_ENVIRONMENT_SET_GEOMETRY` & `RETRO_ENVIRONMENT_GET_CAN_DUPE`
     * `RETRO_ENVIRONMENT_GET_PERF_INTERFACE`
   * Direct ROM asset extraction: stream `assets/rom.bin` or filesystem path into memory via Android `AAssetManager`.
3. **Engine State Machine & `android_main` Lifecycle (`main.cpp`)**:
   * Initialize `android_app` glue context and custom `Engine` container.
   * Intercept Android lifecycle callbacks (`APP_CMD_INIT_WINDOW`, `APP_CMD_TERM_WINDOW`, `APP_CMD_GAINED_FOCUS`, `APP_CMD_LOST_FOCUS`, `APP_CMD_CONFIG_CHANGED`, `APP_CMD_LOW_MEMORY`, `APP_CMD_SAVE_STATE`, `APP_CMD_DESTROY`).
   * Orchestrate frame execution rate matching `av_info.timing.fps` using high-resolution monotonic clock timing (`std::chrono::steady_clock`).

### 2.4 Acceptance Criteria
* `native_engine` build script cleanly produces `libretro_engine.so` with 16 KB page alignment.
* Dynamic core loader binds Libretro symbols without symbol resolution leaks.
* Asset manager successfully streams ROM bytes into memory from APK `assets/rom.bin`.
* Engine lifecycle loop transitions reliably across resume, pause, config change, and destroy.

---

## 3. Phase 1B (Part 2): Hardware Accelerated Video (GLES2) & Low-Latency Audio (AAudio)

### 3.1 Scope & Objective
Construct the high-performance hardware rendering and audio output subsystems. Implement an EGL surface manager and OpenGL ES 2.0 quad blitter with aspect-ratio preserving letterboxing and multi-format pixel conversion, alongside a low-latency exclusive AAudio stream powered by a lock-free SPSC circular ring buffer.

### 3.2 Files to Create / Modify
* `native_engine/src/video/gles_renderer.hpp` & `native_engine/src/video/gles_renderer.cpp`
* `native_engine/src/audio/ring_buffer.hpp`
* `native_engine/src/audio/aaudio_player.hpp` & `native_engine/src/audio/aaudio_player.cpp`

### 3.3 Detailed Task Checklist
1. **EGL & OpenGL ES 2.0 Quad Blitter (`gles_renderer.cpp`)**:
   * Acquire `EGLDisplay`, `EGLContext`, `EGLSurface` on `APP_CMD_INIT_WINDOW`.
   * Create dynamic 2D texture (`GL_RGB565` / `GL_RGBA` / `GL_UNSIGNED_SHORT_5_5_5_1`) updated via `glTexSubImage2D`.
   * Render screen-filling textured quad preserving original console aspect ratio (3:2 for GBA, 4:3 for SNES/NES/Genesis, etc.) with viewport letterboxing/pillarboxing.
   * Implement GLSL vertex & fragment shaders with nearest-neighbor / linear filtering options.
2. **Lock-Free SPSC Circular Audio Ring Buffer (`ring_buffer.hpp`)**:
   * Single-producer single-consumer circular buffer using atomic read/write indices with memory barriers (`std::atomic<size_t>`, `std::memory_order_acquire` / `std::memory_order_release`).
   * Zero allocations in audio critical path.
3. **AAudio Low-Latency Exclusive Output Stream (`aaudio_player.cpp`)**:
   * Configure `AAUDIO_PERFORMANCE_MODE_LOW_LATENCY` and `AAUDIO_SHARING_MODE_EXCLUSIVE` (fallback to `AAUDIO_SHARING_MODE_SHARED`).
   * Audio format: `AAUDIO_FORMAT_PCM_I16`, 2 channels, matching core `sample_rate`.
   * Audio callback pulling interleaved stereo frames from the SPSC ring buffer with underrun protection.

### 3.4 Acceptance Criteria
* Video quad renders at steady 60 FPS without tearing or aspect distortion.
* Audio stream plays clear stereo audio with `< 15ms` buffer latency and zero glitches.

---

## 4. Phase 2: In-Engine Touch Controls, OSD Menu & State Persistence

### 4.1 Scope & Objective
Build a clean, responsive virtual touch controller from scratch using `AInputQueue`, implement an in-engine OSD pause menu, and wire atomic battery save (SRAM) and state serialization.

### 4.2 Files to Create / Modify
* `native_engine/src/input/virtual_pad.hpp` & `virtual_pad.cpp`
* `native_engine/src/ui/osd_menu.hpp` & `osd_menu.cpp`
* `native_engine/src/storage/state_manager.hpp` & `state_manager.cpp`

### 4.3 Detailed Task Checklist
1. **Multi-Touch Virtual Controller (`virtual_pad.cpp`)**:
   * Intercept `AINPUT_EVENT_TYPE_MOTION` in `handle_input()` via `android_native_app_glue`.
   * Track multiple touch pointers (`AMotionEvent_getPointerCount`, `AMotionEvent_getPointerId`).
   * Hit-test coordinates against D-Pad (Up, Down, Left, Right) and action buttons (A, B, X, Y, L, R, Start, Select).
   * Render transparent, alpha-blended button sprites directly over the game quad in GLES2.
   * Map active button states to Libretro `RETRO_DEVICE_ID_JOYPAD_*` bitmask.
2. **In-Engine OSD Menu (`osd_menu.cpp`)**:
   * Menu trigger button positioned at top-center of screen.
   * On trigger: pauses emulation and renders clean overlay options:
     * **Save State** (Slots 1–5 selector).
     * **Load State** (Instant restore from slot).
     * **Fast-Forward** (Toggle 2x / 4x / 8x speed; mutes audio during fast-forward).
     * **Reset Game** (`retro_reset`).
     * **Exit** (`ANativeActivity_finish`).
3. **Atomic SRAM & State Persistence (`state_manager.cpp`)**:
   * Battery saves (SRAM): Read `retro_get_memory_data(RETRO_MEMORY_SAVE_RAM)` and write to `filesDir/game.sav.tmp` $\rightarrow$ `fsync()` $\rightarrow$ atomic rename to `filesDir/game.sav`.
   * Trigger auto-save on `APP_CMD_PAUSE`, `APP_CMD_LOST_FOCUS`, and OSD menu exit.
   * Savestate files written to `filesDir/slot_<N>.state`.

### 4.4 Acceptance Criteria
* Multi-touch permits simultaneous D-pad movement and action button presses (e.g. running and jumping).
* OSD menu opens instantly on tap, saves/loads states reliably without crashing.
* Battery SRAM save persists across app kills and phone reboots.

---

## 5. Phase 3: Zero-Code Base Template APK Assembly & Manifest Stripping

### 5.1 Scope & Objective
Purge the legacy `template-apk` Kotlin codebase (`GameActivity.kt`, views, JNI wrappers), create the stripped `android:hasCode="false"` manifest, and assemble the universal pre-compiled base templates (`template_<console>.apk`) in `runtimes/`.

### 5.2 Files to Create / Modify / Delete
* **Delete**: `template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt`
* **Delete**: Legacy Java layouts, dialogs, and unused resource XMLs.
* **Create/Update**: `template-apk/src/main/AndroidManifest.xml` (stripped `NativeActivity` manifest).
* **Create**: `runtimes/template_gba.apk`, `runtimes/template_snes.apk`, `runtimes/template_genesis.apk`, `runtimes/template_nes.apk`, `runtimes/template_pce.apk`.
* **Create/Update**: `.github/workflows/stage-native-templates.yml` (CI workflow to build templates).

### 5.3 Detailed Task Checklist
1. **Manifest Stripping (`AndroidManifest.xml`)**:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
       package="com.retro.game.template"
       android:versionCode="1"
       android:versionName="1.0.0">

       <application
           android:hasCode="false"
           android:label="Retro Game"
           android:icon="@mipmap/ic_launcher"
           android:extractNativeLibs="false"
           android:allowBackup="false"
           android:theme="@android:style/Theme.NoTitleBar.Fullscreen">

           <activity
               android:name="android.app.NativeActivity"
               android:exported="true"
               android:screenOrientation="sensorLandscape"
               android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode"
               android:windowSoftInputMode="adjustNothing">
               <meta-data android:name="android.app.lib_name" android:value="retro_engine" />
               <intent-filter>
                   <action android:name="android.intent.action.MAIN" />
                   <category android:name="android.intent.category.LAUNCHER" />
               </intent-filter>
           </activity>
       </application>
   </manifest>
   ```
2. **Base Template Packaging**:
   * Bundle `lib/arm64-v8a/libretro_engine.so` + target core (`libretro_mgba.so`, etc.).
   * Include placeholder `res/mipmap-*/ic_launcher.png` and dummy `assets/rom.bin`.
   * Assert **NO `classes.dex`** exists in the resulting `.apk`.
3. **16 KB Page Alignment Verification**:
   * Run `scripts/verify_core_alignment.py` to confirm ELF `PT_LOAD` offsets are congruent modulo `0x4000`.

### 5.4 Acceptance Criteria
* `unzip -l template_gba.apk` confirms zero `.dex` files.
* APK size is `< 4 MB`.
* Verified 16 KB page-aligned native shared libraries.

---

## 6. Phase 4: Dual Packaging Engine Integration & End-to-End Certification

### 6.1 Scope & Objective
Wire up the fast binary injection engine in both the **Android Manager App** ([BuildEngine.kt](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt)) and the **Desktop/CI Tool** (`scripts/builder.py`). Perform end-to-end certification.

### 6.2 Files to Create / Modify
* `core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt` (Update binary injector).
* `core/src/main/kotlin/com/retropack/packaging/AxmlMutator.kt` (Ensure compatibility with pure NativeActivity manifest).
* `scripts/builder.py` (Desktop/CI Python repackager).
* `app/` (Connect Compose UI to generate pure C++ game APKs).

### 6.3 Detailed Task Checklist
1. **Kotlin Packaging Core Updates (`core/BuildEngine.kt`)**:
   * Point template resolution to `runtimes/template_<console>.apk`.
   * Inject user ROM into `assets/rom.bin` and box art into `res/mipmap-*/ic_launcher.png`.
   * Mutate binary AXML: package name `com.retro.game.<slug>_<hash>`, app title, and versionCode.
   * Align uncompressed native libs with `zipalign -p -f 16384`.
   * Sign APK in memory using `apksig` (v1 + v2 + v3).
2. **Desktop / CI Python Repackager (`scripts/builder.py`)**:
   * Implement standalone CLI accepting `template.apk`, `game.rom`, `icon.png`, `title`, and output path.
   * Performs identical binary injection, 16 KB zipalign, and `apksigner` invocation in `< 1.5s`.
3. **End-to-End Validation**:
   * Build a test GBA ROM (e.g. *Pokemon Emerald*) via Android Manager App.
   * Build a test SNES ROM via Python CLI.
   * Verify generated APKs install and launch instantly (`< 60ms`) on Android 12 through Android 15+.

### 6.4 Acceptance Criteria
* APK generation finishes in **< 1.5 seconds**.
* Generated APK cold-boots in **< 60ms** with zero GC stutter and steady 60 FPS audio/video.
* On-device Android Manager App and Desktop Python CLI both produce 100% compliant standalone APKs.

---

## 7. Handover Protocol Between Successive Agents

| Agent Turn | Active Phase | Primary Focus & Deliverables | Handover Verification Command |
| :--- | :--- | :--- | :--- |
| **Agent 1** | **Phase 1A (Part 1)** | `native_engine/` Bedrock (CMake, `main.cpp`, `libretro_bridge.hpp`/`.cpp`, AAssetManager loader) | Header and architecture integrity check |
| **Agent 2** | **Phase 1B (Part 2)** | AV Pipeline (`gles_renderer.cpp`, `aaudio_player.cpp`, `ring_buffer.hpp`) | EGL Quad blit + AAudio stream validation |
| **Agent 3** | **Phase 2** | Rebuild touch controls & OSD menu (`virtual_pad.cpp`, `osd_menu.cpp`, `state_manager.cpp`) | Touch hitbox tests & SRAM fsync validation |
| **Agent 4** | **Phase 3** | Purge Kotlin `template-apk`, assemble `template_<console>.apk` | `python scripts/verify_core_alignment.py` |
| **Agent 5** | **Phase 4** | Wire `BuildEngine.kt` + `builder.py`, end-to-end test | `python scripts/builder.py ...` & APK install test |
