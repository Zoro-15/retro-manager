# 📋 FEATURE 1: Multi-Disc Game Management & In-Game Disc Switcher (PS1 & PC Engine CD)

### Target Architectures:
- **Sony PlayStation 1**: `.iso`, `.cue`, `.chd`, `.pbp`, `.bin` (PCSX ReARMed)
- **PC Engine CD-ROM²**: `.cue`, `.chd`, `.iso`, `.bin` (Beetle PCE Fast)

### Technical Specifications:
1. **Core Domain & Staging (`core` module)**:
   - Extend `BuildRequest` and `RomIdentity` to support multi-disc arrays: `List<File> discImages` with disc labels ("Disc 1", "Disc 2", "Disc 3", "Disc 4").
   - Multi-disc compression & staging into `assets/discs/disc_0.chd`, `assets/discs/disc_1.chd`, etc., or generate an automatic `.m3u` playlist in `assets/game.m3u`.
   - Update `RomParser.kt` to detect multi-disc filename patterns (e.g., `(Disc 1)`, `(Disc 2)`, `_CD1`, `_CD2`) and group them automatically.

2. **Native JNI Core Hooks (`runtime` module)**:
   - Add JNI functions in `pcsx-jni.c` and `pce-jni.c`:
     * `nativeEjectDisc()`: Notifies the virtual CD-ROM drive of tray open.
     * `nativeInsertDisc(int discIndex, String discPath)`: Mounts the next disc image.
     * `nativeGetDiscCount()` / `nativeGetCurrentDisc()`: Returns disc count and active index.
   - Expose through `NativeCore.kt` interface.

3. **In-Game Virtual Disc Switcher UI (`template-apk` module)**:
   - When a game triggers a "Please Insert Disc 2" screen, the user taps the Pause Menu -> **"Switch Disc"**.
   - Opens a dialog displaying available discs with active checkmark.
   - Selecting a disc triggers atomic ejection -> ISO unmount -> new image mount -> virtual tray close.

4. **Manager UI (`app` module)**:
   - Add a multi-file disc selector in Step 1 of `MainScreen.kt` when a CD-based console is detected.
   - Displays disc slots with drag-and-drop reordering.

we will run tests in github ci after all features are added , remember not to run test or verification locally
////////////////////////////////////////////////////////

# 📋 FEATURE 2: In-Game Pause HUD & Savestate Quick Slots UI (`template-apk`)

### Technical Specifications:
1. **Floating In-Game HUD & Gesture Trigger (`GameActivity.kt`)**:
   - Add an unobtrusive, semi-transparent Floating Pause Icon with adjustable opacity (or edge-swipe down gesture from the top of the screen).
   - Tapping the icon pauses emulation and animates a frosted-glass bottom sheet / modal HUD.

2. **Visual 10-Slot Savestate Manager (`SaveStateManager.kt` + UI)**:
   - Visual grid of 10 slots (Slot 0 to Slot 9).
   - On every save, capture a scaled $256 \times 144$ PNG thumbnail of the current frame and store it alongside `.state0`, `.state0.png`.
   - Displays screenshot thumbnail, timestamp (e.g., "Today 18:45"), and play duration for each slot.
   - One-tap **Quick Save** and **Quick Load** buttons.

3. **In-Game Quick Controls Panel**:
   - **Fast Forward Toggle**: $1.5\times / 2\times / 4\times / 8\times$ turbo mode with audio pitch preservation.
   - **Screen Aspect Ratio Switcher**: Cycle live through `Aspect Fit (4:3)`, `Integer Scale (Pixel Perfect)`, and `Full Screen Stretch`.
   - **Touch Layout Customizer**: "Edit Controls" button allowing the user to drag, drop, and resize on-screen D-Pad and action buttons, saving custom coordinates to `SharedPreferences`.
   - **Reset Game** and **Exit to Launcher** actions.

///////////////////////////////////////////////////////////////

# 📋 FEATURE 3: Packaged Games & Save File Management Hub (`app` module)

### Technical Specifications:
1. **Installed Games Scanner (`InstalledGamesRepository.kt`)**:
   - Query Android `PackageManager` using `getInstalledApplications()` with `GET_META_DATA`.
   - Filter packages matching prefix `com.retropack.game.*` or possessing `com.retropack.runtime` metadata.
   - Extract the game's icon, display title, console platform, package name, install date, and app size.

2. **Dedicated "Library & Saves" Navigation Tab (`InstalledGamesScreen.kt`)**:
   - Grid/List view of all generated RetroPack standalone games installed on the user's device.
   - Quick **"Play"** button to launch the game directly via explicit Intent.

3. **Battery Save Backup & Export (`SaveSyncManager.kt`)**:
   - Reads battery saves (`.srm`, `.sav`, `.mcd`, `.eep`, `.nvram`) from the app's accessible files directory or content provider.
   - **Export Save**: Exports `.sav` to the device's `Downloads/RetroPack/Saves/<GameTitle>/` folder or shares via system intent.

4. **Save Import Tool (PC Emulator $\leftrightarrow$ Android Game)**:
   - "Import Save" file picker accepting standard `.sav`, `.srm`, `.mcd` files from mGBA, Snes9x, PCSX, DuckStation, or PPSSPP.
   - Injects the imported save file with atomic POSIX fsync verification into the target game before boot.
////////////////////////////////////////////////////////


# 📋 FEATURE 4: Custom Bluetooth & USB Controller Button Mapping

### Technical Specifications:
1. **Controller Detection & Discovery (`GamepadManager.kt`)**:
   - Listen for `InputManager.InputDeviceListener` connection/disconnection events.
   - Identify connected controller hardware: Xbox Wireless Controller, PlayStation DualSense / DualShock 4, Nintendo Switch Pro / Joy-Cons, 8BitDo, Razer Kishi.

2. **Visual Controller Remapping Screen (`ControllerMappingDialog.kt`)**:
   - Interactive SVG/Canvas graphic of a gamepad with glowing buttons.
   - Tapping an action button (e.g., "A / Cross", "B / Circle", "D-Pad Up", "L2 Trigger") prompts the user: *"Press button on your controller to bind..."*
   - Captures Android `KeyEvent.keyCode` and `MotionEvent.AXIS_*` values.

3. **Per-Console Button Profiles (`ControllerProfile.kt`)**:
   - Store customizable mappings per console architecture (e.g., SNES Diamond $A/B/X/Y$, Genesis 6-button $A/B/C/X/Y/Z$, N64 $C$-buttons & $Z$-trigger, PS1 DualShock).
   - Deadzone slider calibration for analog sticks ($0\%$ to $30\%$) and trigger sensitivity.
   - Persist profiles in `SharedPreferences` and inject into `retropack.json` during the 15-step packaging pipeline.


////////////////////////////////////////////////////////////////////////

# 📋 FEATURE 5: Retro CRT / LCD Shaders & Hardware GLSL Filters

### Technical Specifications:
1. **GLSL Post-Processing Pipeline (`RetroGlShader.kt` & `RetroGlRenderer.kt`)**:
   - Refactor OpenGL ES 2.0/3.0 renderer to support 2-pass FBO (Framebuffer Object) texture filtering.
   - Pass 1: Render raw emulator RGB framebuffer to off-screen texture.
   - Pass 2: Apply custom GLSL fragment shader program to screen quad.

2. **Built-in Curated Shader Programs**:
   - **`crt_scanlines.frag`**: Simulates authentic CRT TV scanlines with phosphor triads, subtle horizontal curvature, and bloom vignette (perfect for NES, SNES, Genesis, PS1, Arcade).
   - **`lcd_dotmatrix.frag`**: Simulates DMG Game Boy / GBC / GBA liquid crystal sub-pixel dot-matrix grid with authentic pixel borders.
   - **`color_boost.frag`**: Restores original non-backlit GBA / GBC colors with gamma boost and vibrancy tuning.
   - **`sharp_bilinear.frag`**: Pixel-art smoothing that avoids blurry pixels while eliminating uneven pixel shimmering during scrolling.
   - **`dmg_pea_green.frag`**: Authentic 1989 monochrome 4-shade pea-green Game Boy palette LUT.

3. **UI Integration**:
   - **RetroPack Manager**: Dropdown selector in Step 3 (*"Display & Visual Filter"*) to set the game's default shader.
   - **In-Game Pause HUD**: Real-time shader switcher allowing instant visual comparison during gameplay.


////////////////////////////////////////////////////////////////////
# 📋 FEATURE: 🖼️ Custom Console Bezels & OLED Pure Black Overlays

### Mission:
Transform empty black letterbox/pillarbox borders on modern 19.5:9 / 20:9 smartphone screens into stunning, authentic console bezels or battery-saving OLED pure black frames with integrated aesthetic metadata.

### Technical Architecture & Deliverables:

1. **Bezel Render Engine (`RetroBezelRenderer.kt` in `template-apk`)**:
   - Implement an OpenGL ES 2.0/3.0 dual-layer blending pass:
     * **Layer 0 (Background / Frame)**: Renders the high-resolution console bezel texture filling the entire device screen $(1080 \times 2400 \text{ px})$.
     * **Layer 1 (Game Viewport)**: Renders the active game framebuffer with pixel-perfect aspect ratio scaling aligned precisely to the bezel's transparent display cutout window.
     * **Layer 2 (Glass Reflection & Ambient Shadow)**: Optional semi-transparent glass glare and inner bezel shadow overlay simulating an authentic handheld/CRT screen.

2. **Console Bezel Asset Catalog (`template-apk/src/main/res/drawable-nodpi/`)**:
   - Curated high-resolution PNG bezels with transparent cutouts:
     * **GBA**: Classic Indigo, Glacier, and SP Platinum frames.
     * **Game Boy / GBC**: Authentic DMG-01 off-white casing with purple button accents & Atomic Purple GBC.
     * **SNES / Genesis / NES / PS1**: Retro 90s Trinitron CRT television bezel with speaker grills and subtle scanline vignette.
     * **OLED Pure Black**: `#000000` true black background turning off OLED pixels to save up to 40% battery on AMOLED displays, featuring glowing cyber grid accents.

3. **Responsive Bezel Placement & Cutout Calibration (`BezelConfig.kt`)**:
   - JSON-driven coordinate definition specifying the exact viewport bounding box $(x, y, \text{width}, \text{height})$ for each bezel style.
   - Automatically adapts between Landscape and Portrait phone orientations.

4. **UI Integration in RetroPack Manager (`app` module)**:
   - Add a **"Bezel & Screen Frame"** dropdown in Step 3 of `MainScreen.kt` (*Options: None (OLED Black), Auto Handheld/Console Bezel, 90s CRT TV Frame*).
   - Injected into `retropack.json` during the 15-step packaging engine.

////////////////////////////////////////////////////////////////////

# 📋 FEATURE: ⚡ Native Low-Latency C++ Oboe Audio Engine

### Mission:
Replace the JVM `AudioTrack` playback pipeline with Google's native high-performance C++ **Oboe** library (`AAudio` on Android 8.1+ with zero-allocation `OpenSL ES` fallback), dropping audio output latency from ~60ms down to $< 15\text{ms}$ and eliminating audio underruns / buffer pops.

### Technical Architecture & Deliverables:

1. **Oboe Dependency & CMake Integration (`runtime/retropack-runtime-common`)**:
   - Vendor Google's official [`google/oboe`](https://github.com/google/oboe) native library (v1.9.0+) as a CMake submodule or prebuilt NDK target.
   - Configure `CMakeLists.txt` linking `oboe::oboe` with mandatory 16 KB page-size linker flags (`-Wl,-z,max-page-size=16384`).

2. **Native Oboe Stream Bridge (`oboe_stream.h` / `oboe_stream.cpp`)**:
   - Create a C++ `OboeAudioSink` implementing `oboe::AudioStreamDataCallback`:
     * Configured for `oboe::AudioFormat::I16`, `Stereo`, `44100 Hz` / `48000 Hz`.
     * Requests `oboe::PerformanceMode::LowLatency` and `oboe::SharingMode::Exclusive` for direct hardware audio DSP access.
   - Directly pull 16-bit PCM stereo audio chunks from the lock-free circular ring buffer (`ringbuffer.c`) inside the native audio callback.
   - Implement dynamic resampler / sample-rate converter (`libsamplerate` / Oboe linear interpolation) to seamlessly match native hardware sample rates ($44.1\text{ kHz} \leftrightarrow 48.0\text{ kHz}$).

3. **JNI Lifecycle Controller (`OboeAudioEngine.kt`)**:
   - Expose JNI methods: `nativeOboeInit()`, `nativeOboeStart()`, `nativeOboePause()`, `nativeOboeStop()`, `nativeOboeGetLatencyMs()`.
   - Wire `OboeAudioEngine` directly into `EmulationHost.kt` and `GameActivity.kt` on `onResume()` / `onPause()`.

4. **Zero-Garbage Collection (GC) Audio Threading**:
   - Eliminates all Java-side audio byte array allocations and JNI cross-boundary buffer copies during frame rendering.
   - Guaranteed $< 15\text{ms}$ glass-to-ear latency.

////////////////////////////////////////////////////////

# 📋 FEATURE 1: 🔄 Instant Resume / Background Auto-Snapshotting ("Never Lose Progress")

### Mission:
Eliminate accidental progress loss and skip repetitive BIOS/title-screen loading sequences by taking an instantaneous, silent savestate snapshot when the game app is minimized or backgrounded, and immediately resuming from the exact frame upon relaunch.

### Technical Architecture & Deliverables:

1. **Lifecycle Event Hooks (`GameActivity.kt`)**:
   - In `onPause()` / `onStop()` / `onTrimMemory()`:
     * Check if emulation is currently active.
     * Trigger a high-priority, non-blocking native savestate capture to an internal atomic file: `<filesDir>/saves/auto_resume.state`.
     * Write POSIX `fsync` to guarantee complete state serialization before the Android OS can terminate the background process.
   - Store metadata in `SharedPreferences` with timestamp and ROM CRC32/SHA-256 validation.

2. **Instant Boot Path (`GameActivity.kt` & `RomStager.kt`)**:
   - In `onResume()` / `onCreate()`:
     * Check for the presence of a valid `auto_resume.state` matching the current ROM hash.
     * If present and enabled in `retropack.json`:
       - Fast-boot the emulation engine directly.
       - Restore `auto_resume.state` on frame 0 before the first OpenGL ES render pass.
       - Result: The player is back in the game in $< 250\text{ms}$ with zero perceptible delay.

3. **User Safety & Conflict Mitigation**:
   - Do NOT overwrite numbered user save slots (Slots 0–9).
   - If an `auto_resume.state` crashes on restore (e.g. corrupt or incompatible state), catch the exception, delete the stale state, and gracefully fall back to a clean boot + battery SRAM reload.
   - Add a toggle in the In-Game Pause HUD: *"Auto-Resume on App Launch: [ON / OFF]"*.


//////////////////////////////////////////////////////////

# 📋 FEATURE 2: 📳 Audio-Reactive & DualSense Hardware Haptic Vibration

### Mission:
Bring games to life with intelligent tactile haptics: authentic motor emulation for rumble-enabled consoles (PS1 DualShock, N64 Rumble Pak, GBA Drill Dozer) combined with audio-reactive bass haptics for classic consoles (NES, Game Boy, SNES, Genesis).

### Technical Architecture & Deliverables:

1. **Native Rumble Event Interceptor (`retropack-runtime-common`)**:
   - Add JNI callbacks in `NativeCore.kt`:
     * `onRumble(int motorIndex, int strengthPercent, int durationMs)`
   - Bind native core rumble hooks:
     * **PCSX ReARMed**: DualShock weak/strong vibration actuator callbacks.
     * **Mupen64Plus**: N64 Controller Pak 0x01 (Rumble Pak) register writes.
     * **mGBA**: Game Boy Advance GPIO / Game Boy Color rumble cartridge register pulses.

2. **Audio-Reactive Frequency Analyzer (`AudioHapticEngine.kt`)**:
   - For 8-bit/16-bit systems without native rumble (NES, SNES, Genesis, PCE):
     * Run a lightweight Low-Pass Filter ($f_c < 120\text{ Hz}$) on the incoming 16-bit PCM audio stream inside the ring buffer.
     * Detect sudden high-amplitude sub-bass transients (explosions, heavy hits, jump impacts) with dynamic gain thresholding.
     * Convert detected impacts into subtle haptic micro-pulses.

3. **Modern Android Haptic Framework (`HapticManager.kt`)**:
   - Target Android 12+ (API 31+) `VibratorManager` and `VibrationEffect.Composition`:
     * Use `PRIMITIVE_CLICK`, `PRIMITIVE_THUD`, `PRIMITIVE_HEAVY_CLICK` for punchy retro button presses and impacts.
     * Map continuous rumble strength to `VibrationEffect.createOneShot(duration, amplitude)`.
   - Backward compatible with Android 8.0–11 via legacy `VibrationEffect.createWaveform()`.
   - In-Game HUD settings: *Rumble Strength Slider (0%–100%)* and *Haptic Feedback Type (Native Rumble / Audio-Reactive / Off)*.


///////////////////////////////////////////////

# 📋 FEATURE 3: 🚀 Dynamic Device-Adaptive Variable Refresh Rate (VRR / 90Hz / 120Hz / 144Hz) & Motion Sync

### Mission:
Deliver silky-smooth, judder-free display output by dynamically discovering the user's physical display modes and synchronizing frame presentation with exact integer display cycles, without forcing unsupported modes or draining battery.

### Technical Architecture & Deliverables:

1. **Device Display Mode Discovery (`DisplaySyncManager.kt`)**:
   - Query `window.windowManager.defaultDisplay.supportedModes` on Android:
     * Check if the device hardware supports high refresh rates ($90\text{ Hz}, 120\text{ Hz}, 144\text{ Hz}, 165\text{ Hz}$).
     * Check for Variable Refresh Rate (VRR) / `Surface.setFrameRate()` support (Android 11+ / API 30+).
   - If the user device is a standard $60\text{ Hz}$ screen, do NOT attempt mode switches; maintain native $60\text{ Hz}$ timing.

2. **Dynamic Integer Frame-Rate Synchronization**:
   - On $120\text{ Hz}$ screens:
     * Set `Surface.setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)`.
     * The display flips each 60 FPS emulation frame exactly twice (2 vsync ticks per frame), eliminating 3:2 pull-down micro-stutters and uneven frame pacing.
   - On $144\text{ Hz}$ / $90\text{ Hz}$ screens with VRR:
     * Lock vsync interval dynamically to prevent frame tearing without engaging GPU spin-waiting.

3. **Motion Interpolation & Black Frame Insertion (BFI) (Optional Toggle)**:
   - For devices running at $120\text{ Hz}$:
     * Offer an optional **Black Frame Insertion** toggle in the In-Game HUD (Frame 0: Game, Frame 1: Black, Frame 2: Game, Frame 3: Black), mimicking the motion clarity and zero-persistence phosphor decay of authentic CRT / CRT-arcade monitors.
   - Zero additional battery drain: Display mode negotiation is handled via Android OS Display Manager without waking the CPU between frames.


////////////////////////////////////////////////////////
# 📋 FEATURE 4: ⏱️ Time-Stretched Audio during Fast Forward (WSOLA)

### Mission:
Ensure gameplay audio remains intelligible and pleasant during Fast Forward / Turbo speed ($1.5\times / 2\times / 4\times$) by applying a high-performance WSOLA (Waveform Similarity Overlap-Add) time-stretching algorithm, eliminating chipmunk-pitched audio distortion and choppy buffer skips.

### Technical Architecture & Deliverables:

1. **Native WSOLA C++ Time-Stretcher (`timestretch.h` / `timestretch.cpp` in `runtime-common`)**:
   - Implement a lightweight, allocation-free WSOLA audio processor:
     * Maintains constant pitch while adjusting playback speed ratio $R \in [1.0, 4.0]$.
     * Analyzes cross-correlation between overlapping $20\text{ms}$ audio grain windows to find phase-aligned splice points.
     * Smoothly blends overlapping grains using Hann / cosine windowing to prevent clicks.

2. **Integration with Audio Ring Buffer (`ringbuffer.c` & `OboeAudioSink`)**:
   - When the user holds the Fast-Forward / Turbo button ($2\times$ speed):
     * Native emulation runs at 120 FPS.
     * The audio ring buffer receives $88,200\text{ samples/sec}$.
     * The WSOLA filter time-compresses the audio in real time down to $44,100\text{ samples/sec}$ without pitch shifting.
   - The game sounds accelerated naturally (like a fast-talking voice) rather than screeching at double pitch.

3. **Audio Bypass Mode**:
   - For ultra-high speeds ($> 4\times$ or $8\times$), gracefully mute audio to preserve CPU overhead for maximum emulation throughput.
////////////////////////////////////////////////////////////////


# 📋 FEATURE 5: 🖼️ Automated Libretro Thumbnails Boxart Scraping & Adaptive Icon Generator

### Mission:
Enable RetroPack Manager to automatically fetch official, authentic, high-resolution boxart for any scanned ROM from the official open-source Libretro Thumbnails repository (`https://thumbnails.libretro.com/`), and intelligently transform it into high-definition Android Adaptive Icon layers ($108 \times 108\text{ dp}$ foreground + background).

---

### 1. Research & Analysis of `thumbnails.libretro.com`

#### A. Repository Directory Structure:
The repository organizes artwork under URL-encoded system names and standardized categories:
`https://thumbnails.libretro.com/<System_Name>/<Category>/<Sanitized_Game_Title>.png`

Categories available:
1. `Named_Boxarts/` (Primary target: High-res front cover art)
2. `Named_Titles/` (Fallback 1: High-res game title screen)
3. `Named_Snaps/` (Fallback 2: In-game gameplay snapshot)

#### B. Console System Name Mapping Table:
| Console Architecture | Libretro System Directory Name |
| :--- | :--- |
| **Game Boy Advance** | `Nintendo%20-%20Game%20Boy%20Advance` |
| **Game Boy Color** | `Nintendo%20-%20Game%20Boy%20Color` |
| **Game Boy (DMG)** | `Nintendo%20-%20Game%20Boy` |
| **Super Nintendo** | `Nintendo%20-%20Super%20Nintendo%20Entertainment%20System` |
| **NES / Famicom** | `Nintendo%20-%20Nintendo%20Entertainment%20System` |
| **Nintendo 64** | `Nintendo%20-%20Nintendo%2064` |
| **Nintendo DS** | `Nintendo%20-%20Nintendo%20DS` |
| **Sega Genesis / Mega Drive** | `Sega%20-%20Mega%20Drive%20-%20Genesis` |
| **Sega Master System** | `Sega%20-%20Master%20System%20-%20Mark%20III` |
| **Sega Game Gear** | `Sega%20-%20Game%20Gear` |
| **PC Engine / TG-16** | `NEC%20-%20PC%20Engine%20-%20TurboGrafx%2016` |
| **Sony PlayStation 1** | `Sony%20-%20PlayStation` |
| **Sony PlayStation Portable** | `Sony%20-%20PlayStation%20Portable` |
| **Arcade / Neo Geo** | `SNK%20-%20Neo%20Geo` / `FBNeo%20-%20Arcade%20Games` |

#### C. Libretro Special Character Sanitization Rules:
In Libretro naming conventions, characters that are illegal across Linux/Windows filesystems are replaced with `_`:
- `&`, `*`, `:`, `/`, `<`, `>`, `?`, `\`, `|`, `"` $\rightarrow$ `_`
- Example: *The Legend of Zelda: The Minish Cap* $\rightarrow$ `The Legend of Zelda_ The Minish Cap`
- Titles following library conventions: *Legend of Zelda, The - The Minish Cap (USA)*

---

### 2. Multi-Tier Resolution & Candidate Fallback Strategy

When a ROM is loaded into RetroPack Manager, the scraping engine (`LibretroThumbnailsScraper.kt`) generates search candidates in order of specificity:

1. **Exact No-Intro Full Candidate**:
   - `"${cleanedTitle} (${region}).png"` (e.g. `Pokemon - Emerald Version (USA, Europe).png`)
2. **Standard Title with Country**:
   - `"${cleanedTitle} (USA).png"` $\rightarrow$ `"${cleanedTitle} (Europe).png"` $\rightarrow$ `"${cleanedTitle} (Japan).png"`
3. **Clean Title Only**:
   - `"${cleanedTitle}.png"`
4. **Normalized Title Re-ordering**:
   - Converts *"The Legend of Zelda"* $\leftrightarrow$ *"Legend of Zelda, The"*
5. **Category Fallback**:
   - If `Named_Boxarts` returns HTTP 404 $\rightarrow$ check `Named_Titles` $\rightarrow$ check `Named_Snaps` $\rightarrow$ fall back to high-res vector icon.

---

### 3. Smart Android Adaptive Icon Generator (`AdaptiveIconComposer.kt`)

Raw boxarts from Libretro come in different aspect ratios (e.g., GBA vertical boxart $3:4$, SNES horizontal boxart $4:3$, PS1 square boxart $1:1$). To satisfy Android's adaptive icon standard ($108 \times 108\text{ dp}$ with $72\text{ dp}$ safe-zone mask):

1. **Background Layer (`ic_launcher_background.png`)**:
   - Sample the scraped boxart image to extract the dominant vibrant color using AndroidX Palette (`Palette.from(bitmap).generate()`).
   - Generate a subtle, high-res dark radial gradient based on the dominant game color with 10% dark vignette.
2. **Foreground Layer (`ic_launcher_foreground.png`)**:
   - Center the scraped boxart within the inner $72\text{ dp}$ circular safe zone.
   - Apply rounded corners ($12\text{ px}$ radius) and a subtle 3D drop shadow $(0, 4\text{ dp}, \text{blur } 8\text{ dp}, \alpha = 0.4)$ so the boxart stands out cleanly against the background on any Android launcher (Pixel Launcher, Samsung OneUI, Nova, etc.).
3. **Disk Cache**:
   - Cache downloaded PNGs in `<cacheDir>/boxarts/<crc32>.png` to prevent redundant network requests.

---

### 4. Manager UI Integration (`IconPreviewCard.kt`)

- In Step 2 of `MainScreen.kt`:
  - Show a shimmer loading state: *"Fetching official boxart from Libretro Thumbnails..."*
  - Display the scraped boxart in real time with an interactive **"Adaptive Icon Live Preview"** (showing how the icon looks on Squircle, Circle, and Rounded Rectangle launchers).
  - Provide a **"Change Artwork"** button allowing the user to either:
    1. Search alternate Libretro artwork.
    2. Pick custom PNG/JPEG from device storage.



//////////////////////////
