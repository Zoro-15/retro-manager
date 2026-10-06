# RetroPack — Pure C++ NativeActivity Migration Blueprint (`plan2.md`)

> **Document Status**: Multi-Agent Strategic Blueprint & Phased Execution Plan (v2.1)  
> **Target Branch**: `v2`  
> **Core Strategy**: Retain the Android Manager App (`app/`) and Desktop Python CLI (`scripts/builder.py`), completely drop the legacy Kotlin `GameActivity`, and transition to pure C++ `NativeActivity` (`android:hasCode="false"`) standalone game APKs.

---

## 1. Multi-Agent Phased Execution Structure

This plan is strictly partitioned into **4 Self-Contained Phases** designed for successive AI agents. Each phase defines its **Prerequisites**, **Source Files**, **Implementation Tasks**, **Acceptance Tests**, and **Handover Artifacts**.

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  PHASE 1: Pure C++ Native Engine Bedrock (native_engine/)                              │
│  • C++ android_main entrypoint + EGL/GLES2 quad blitter                                │
│  • AAudio low-latency exclusive stream + Libretro core bindings                        │
│  • Compile libretro_engine.so (16 KB page-aligned)                                     │
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

## 2. Phase 1: Pure C++ Native Engine Bedrock

### 2.1 Scope & Objective
Create the minimal, zero-overhead C++ runtime (`native_engine/`) that compiles to `libretro_engine.so`. It directly initializes EGL/OpenGL ES 2.0, sets up an AAudio stream, reads `assets/rom.bin`, and drives the Libretro emulation loop.

### 2.2 Files to Create / Modify
* `native_engine/CMakeLists.txt`
* `native_engine/src/main.cpp`
* `native_engine/src/video/gles_renderer.hpp` & `gles_renderer.cpp`
* `native_engine/src/audio/aaudio_player.hpp` & `aaudio_player.cpp`
* `native_engine/src/core/libretro_bridge.hpp` & `libretro_bridge.cpp`

### 2.3 Detailed Task Checklist
1. **NDK CMake Build Configuration (`native_engine/CMakeLists.txt`)**:
   * Target: `libretro_engine.so` (shared library).
   * Include Android NDK libraries: `android`, `log`, `EGL`, `GLESv2`, `aaudio`.
   * Enable 16 KB page-alignment linker flags: `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
   * Optimize: `-O3 -flto -fvisibility=hidden`.
2. **EGL & OpenGL ES 2.0 Texture Quad (`gles_renderer.cpp`)**:
   * Initialize EGL surface on `APP_CMD_INIT_WINDOW` (`EGL_OPENGL_ES2_BIT`).
   * Create dynamic 2D texture (`GL_RGB565` / `GL_RGBA8888`) updated via `glTexSubImage2D`.
   * Render screen-filling textured quad preserving original console aspect ratio (3:2 for GBA, 4:3 for SNES/Genesis).
3. **AAudio Native Audio Stream (`aaudio_player.cpp`)**:
   * Configure `AAUDIO_PERFORMANCE_MODE_LOW_LATENCY` and `AAUDIO_SHARING_MODE_EXCLUSIVE`.
   * Format: `AAUDIO_FORMAT_PCM_I16`, 2 channels, 44,100 Hz / 48,000 Hz.
   * Lock-free SPSC ring buffer bridging `retro_audio_sample_batch` to AAudio callback.
4. **Libretro ABI Bridge (`libretro_bridge.cpp`)**:
   * Bind standard Libretro symbols: `retro_init`, `retro_deinit`, `retro_load_game`, `retro_run`, `retro_get_system_av_info`.
   * Stream `assets/rom.bin` from APK `AAssetManager` directly into memory buffer.

### 2.4 Acceptance Criteria
* `libretro_engine.so` compiles cleanly for `arm64-v8a` without warnings.
* Video quad renders at steady 60 FPS without tearing.
* Audio stream plays clear stereo audio with `< 15ms` buffer latency.

---

## 3. Phase 2: In-Engine Touch Controls, OSD Menu & State Persistence

### 3.1 Scope & Objective
Build a clean, responsive virtual touch controller from scratch using `AInputQueue`, implement an in-engine OSD pause menu, and wire atomic battery save (SRAM) and state serialization.

### 3.2 Files to Create / Modify
* `native_engine/src/input/virtual_pad.hpp` & `virtual_pad.cpp`
* `native_engine/src/ui/osd_menu.hpp` & `osd_menu.cpp`
* `native_engine/src/storage/state_manager.hpp` & `state_manager.cpp`

### 3.3 Detailed Task Checklist
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

### 3.4 Acceptance Criteria
* Multi-touch permits simultaneous D-pad movement and action button presses (e.g. running and jumping).
* OSD menu opens instantly on tap, saves/loads states reliably without crashing.
* Battery SRAM save persists across app kills and phone reboots.

---

## 4. Phase 3: Zero-Code Base Template APK Assembly & Manifest Stripping

### 4.1 Scope & Objective
Purge the legacy `template-apk` Kotlin codebase (`GameActivity.kt`, views, JNI wrappers), create the stripped `android:hasCode="false"` manifest, and assemble the universal pre-compiled base templates (`template_<console>.apk`) in `runtimes/`.

### 4.2 Files to Create / Modify / Delete
* **Delete**: `template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt`
* **Delete**: Legacy Java layouts, dialogs, and unused resource XMLs.
* **Create/Update**: `template-apk/src/main/AndroidManifest.xml` (stripped `NativeActivity` manifest).
* **Create**: `runtimes/template_gba.apk`, `runtimes/template_snes.apk`, `runtimes/template_genesis.apk`, `runtimes/template_nes.apk`, `runtimes/template_pce.apk`.
* **Create/Update**: `.github/workflows/stage-native-templates.yml` (CI workflow to build templates).

### 4.3 Detailed Task Checklist
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

### 4.4 Acceptance Criteria
* `unzip -l template_gba.apk` confirms zero `.dex` files.
* APK size is `< 4 MB`.
* Verified 16 KB page-aligned native shared libraries.

---

## 5. Phase 4: Dual Packaging Engine Integration & End-to-End Certification

### 5.1 Scope & Objective
Wire up the fast binary injection engine in both the **Android Manager App** ([BuildEngine.kt](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt)) and the **Desktop/CI Tool** (`scripts/builder.py`). Perform end-to-end certification.

### 5.2 Files to Create / Modify
* `core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt` (Update binary injector).
* `core/src/main/kotlin/com/retropack/packaging/AxmlMutator.kt` (Ensure compatibility with pure NativeActivity manifest).
* `scripts/builder.py` (Desktop/CI Python repackager).
* `app/` (Connect Compose UI to generate pure C++ game APKs).

### 5.3 Detailed Task Checklist
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

### 5.4 Acceptance Criteria
* APK generation finishes in **< 1.5 seconds**.
* Generated APK cold-boots in **< 60ms** with zero GC stutter and steady 60 FPS audio/video.
* On-device Android Manager App and Desktop Python CLI both produce 100% compliant standalone APKs.

---

## 6. Handover Protocol Between Successive Agents

| Agent Turn | Active Phase | Primary Focus & Deliverables | Handover Verification Command |
| :--- | :--- | :--- | :--- |
| **Agent 1** | **Phase 1** | Implement `native_engine/` (EGL quad + AAudio + Libretro bridge) | `cmake --build native_engine/build` |
| **Agent 2** | **Phase 2** | Rebuild touch controls & OSD menu (`virtual_pad.cpp`, `osd_menu.cpp`) | Touch hitbox tests & SRAM fsync validation |
| **Agent 3** | **Phase 3** | Purge Kotlin `template-apk`, assemble `template_<console>.apk` | `python scripts/verify_core_alignment.py` |
| **Agent 4** | **Phase 4** | Wire `BuildEngine.kt` + `builder.py`, end-to-end test | `python scripts/builder.py ...` & APK install test |
