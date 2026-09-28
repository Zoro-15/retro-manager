
# RetroPack Project Analysis: Game File Import Pipeline

**RetroPack** is an Android tool that transforms standalone retro game ROMs and disc images into self-contained, installable Android APKs bundled with hardware-accelerated Libretro emulation runtimes.

---

## 1. High-Level Architecture Overview

The project is structured into four main modules:

1. [**`app`**](file:///c:/Users/ok/Documents/retro%20manager/app): Jetpack Compose Manager UI, ViewModels, file picker integration, asset scraping, and build orchestrator.
2. [**`core`**](file:///c:/Users/ok/Documents/retro%20manager/core): Pure Kotlin/JVM domain module containing binary ROM parsers, archive extractors, security/key stores, AXML binary mutators, and the APK packaging engine.
3. [**`runtime`**](file:///c:/Users/ok/Documents/retro%20manager/runtime): C++ JNI and Kotlin bridges connecting Libretro emulator cores (mGBA, Snes9x, Genesis Plus GX, PCSX ReARMed, melonDS, etc.) to Android SurfaceView/AudioTrack.
4. [**`template-apk`**](file:///c:/Users/ok/Documents/retro%20manager/template-apk): The base APK container injected with the game assets, mutated `AndroidManifest.xml`, icons, and runtime configuration during packaging.

---

## 2. End-to-End Game Import Workflow

```
[User Selects File via SAF]
           │
           ▼
[UriUtils: Stream & Query Display Name / Size]
           │
           ▼
[ArchiveExtractor: Transparent Unpacking (ZIP / RAR / 7z / GZ)]
           │
           ▼
[RomParser: Header Inspection & Platform Detection (10+ Consoles)]
           │
           ▼
[StreamChecksum: Parallel SHA-256, SHA-1, MD5, CRC32 Calculation]
           │
           ▼
[Identity & Metadata Derivation: Package Name, Title, Scraping Box-Art]
           │
           ▼
[Optional: IPS/UPS Patching & Multi-Disc (.m3u) Management]
           │
           ▼
[RomAssetInjector & BuildEngine: Inject into APK assets/ & Sign APK]
```

---

## 3. Deep-Dive: How Game Files are Handled at Each Step

### Step 1: Storage Access Framework (SAF) File Selection
- In [**`MainScreen.kt`**](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/ui/screens/MainScreen.kt#L100-L103), file selection uses Android's `ActivityResultContracts.OpenDocument()`:
  ```kotlin
  val romPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
      onResult = { uri -> uri?.let { viewModel.onSelectRom(context, it) } }
  )
  ```
- [**`UriUtils.kt`**](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/util/UriUtils.kt#L11-L43) reads raw bytes securely via `ContentResolver.openInputStream(uri)` and queries file metadata (`OpenableColumns.DISPLAY_NAME` and `OpenableColumns.SIZE`).

---

### Step 2: Transparent Archive Extraction
ROMs often come compressed in `.zip`, `.rar`, `.7z`, or `.gz` archives.
- [**`ArchiveExtractor.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/ArchiveExtractor.kt) automatically:
  - Detects archive signatures (e.g., `PK..` for ZIP, `Rar!` for RAR, `7z¼¯'!` for 7z).
  - Unpacks nested archives up to a recursion depth of 3.
  - Automatically ignores non-game noise (`.nfo`, `.txt`, `.jpg`, `.xml`, `.ds_store`).
  - Matches the inner entry against the comprehensive [**`SUPPORTED_ROM_EXTENSIONS`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/ArchiveExtractor.kt#L33-L55) catalog (`.gba`, `.sfc`, `.smc`, `.nes`, `.md`, `.iso`, `.cue`, `.chd`, `.pbp`, `.z64`, `.nds`, etc.).

---

### Step 3: Binary Inspection & Header Parsing
Instead of relying solely on file extensions, [**`RomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/RomParser.kt#L27-L62) inspects magic bytes, checksums, and cartridge/disc headers across platforms:

| Platform | Dedicated Parser | Detection & Header Logic |
| :--- | :--- | :--- |
| **Game Boy Advance** | [**`GbaRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/GbaRomParser.kt) | Nintendo fixed logo byte `0x96` at `0xB2`, header checksum at `0xBD`, title at `0xA0`. |
| **Game Boy / Color** | [**`GbRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/GbRomParser.kt) | Nintendo logo at `0x104`, CGB flag `0x143`, MBC cartridge type `0x147`. |
| **NES / Famicom** | [**`NesRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/NesRomParser.kt) | iNES / NES 2.0 header magic `NES\x1A` (PRG/CHR sizes, mapper number, battery SRAM). |
| **Sega Genesis / MD** | [**`GenesisRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/GenesisRomParser.kt) | Magic string `SEGA MEGA DRIVE` / `SEGA GENESIS` at `0x100`, product codes, SRAM flags. |
| **Super Nintendo** | [**`SnesRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/SnesRomParser.kt) | LoROM / HiROM header validation, complement checksum verification at `0x7FDC` / `0xFFDC`. |
| **Nintendo 64** | [**`N64RomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/N64RomParser.kt) | Endianness detection (`0x80371240` big-endian `.z64`, `0x37804012` byteswapped `.v64`, little-endian `.n64`). |
| **Nintendo DS** | [**`NdsRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/NdsRomParser.kt) | Banner title, game code (e.g. `NTR-XXXX`), maker code, CRC16 checksum. |
| **Sony PS1 (PSX)** | [**`PsxRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/PsxRomParser.kt) | ISO9660 Primary Volume Descriptor at sector 16 (`0x8000`), `SYSTEM.CNF` boot disc ID (`SLUS`, `SLES`, `SCES`). |
| **PC Engine / TG16** | [**`PceRomParser.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/PceRomParser.kt) | Reset vector & header analysis, headerless / 512-byte header split. |

*Fallback:* If header inspection fails (e.g. unheadered homebrews or arcade binary dumps), it uses an extension-based fallback table.

---

### Step 4: Checksum Calculation & Metadata Extraction
- In [**`StreamChecksum.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/StreamChecksum.kt), the parser calculates **CRC32**, **MD5**, **SHA-1**, and **SHA-256** concurrently.
- [**`RomIdentity.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/model/RomIdentity.kt) automatically computes:
  - Sanitized game title (strips region tags like `(USA)`, `[!]`, etc.).
  - Derived unique Android package name: `com.retro.<platform>.<game_slug>`.
  - Matched emulator core template (e.g., `mgba-unified`, `snes9x-unified`, `pcsx-unified`).
- In [**`MainViewModel.kt`**](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/viewmodel/MainViewModel.kt#L189-L238), box-art scraping is automatically triggered in the background from Libretro Thumbnails and OpenVGDB, synthesized into adaptive Android icon layers by [**`IconSynthesizer.kt`**](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/util/IconSynthesizer.kt).

---

### Step 5: Advanced Import Features (Multi-Disc & Soft Patches)
1. **Multi-Disc Games (PS1 / PCE-CD)**:
   - Added via `onAddDisc(context, uri)` in [**`MainViewModel.kt`**](file:///c:/Users/ok/Documents/retro%20manager/app/src/main/kotlin/com/retropack/manager/viewmodel/MainViewModel.kt#L283-L325).
   - Allows reordering discs, custom labeling ("Disc 1", "Disc 2"), and generating unified `.m3u` playlist indices.
2. **ROM Patching (IPS / UPS)**:
   - Users can select a `.ips` or `.ups` translation/hack file.
   - [**`IpsUpsPatcher.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/domain/rom/IpsUpsPatcher.kt) applies the delta patch in-memory before packaging.

---

### Step 6: Packaging & Injection into APK
During APK build execution in [**`BuildEngine.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/packaging/BuildEngine.kt):
- [**`RomAssetInjector.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/packaging/RomAssetInjector.kt) sanitizes paths against directory traversal attacks (`..`).
- Injects game binaries and configs into the APK asset directory:
  - Single-disc: `assets/game.rom`
  - Multi-disc: `assets/discs/disc_01.chd`, `assets/discs/disc_02.chd`, plus `assets/game.m3u`
  - Configuration: `assets/retropack.json` (controls, shaders, audio latency, aspect ratio, save paths).
- Signs and aligns the resulting APK with v1/v2 signatures using [**`HybridKeystore.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/security/HybridKeystore.kt) and [**`ApkSignerService.kt`**](file:///c:/Users/ok/Documents/retro%20manager/core/src/main/kotlin/com/retropack/packaging/ApkSignerService.kt).

---

### Step 7: Runtime Execution
When the generated standalone APK is launched on Android:
- [**`GameActivity.kt`**](file:///c:/Users/ok/Documents/retro%20manager/template-apk/src/main/kotlin/com/retropack/runtime/GameActivity.kt) reads `assets/retropack.json` and loads `assets/game.rom` directly via native stream/file descriptor into [**`UniversalLibretroCore.kt`**](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/core/UniversalLibretroCore.kt) / [**`NativeCoreBridge.kt`**](file:///c:/Users/ok/Documents/retro%20manager/runtime/retropack-runtime-common/src/main/kotlin/com/retropack/runtime/core/NativeCoreBridge.kt).