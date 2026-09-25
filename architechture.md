# RetroPack — Architecture Specification (`architechture.md`)

> **Document Status**: Definitive System Architecture & Technical Contracts (v0.1)  
> **Role**: Single source of truth for architectural layers, constitutional laws, data schemas, security contracts, and platform invariants.

---

## 1. The 7-Layer Architectural Model

The system is partitioned into seven strictly separated, unidirectional layers:

```text
┌─────────────────────────────────────────────────────────────┐
│ 1. UI Layer (Presentation & Stepper Flow)                   │
├─────────────────────────────────────────────────────────────┤
│ 2. Application Layer (Workflow Orchestration)               │
├─────────────────────────────────────────────────────────────┤
│ 3. Domain / Core Layer (Platform-Independent Models)        │
├─────────────────────────────────────────────────────────────┤
│ 4. Runtime Abstraction Layer (RuntimeTemplate & Registry)   │
├─────────────────────────────────────────────────────────────┤
│ 5. Build Engine Layer (Declarative Pipeline Coordinator)    │
├─────────────────────────────────────────────────────────────┤
│ 6. Packaging Backend (AXML Mutator, Zipalign, apksig)      │
├─────────────────────────────────────────────────────────────┤
│ 7. External Runtime Implementations (NDK Host Bedrock)      │
└─────────────────────────────────────────────────────────────┘
```

### Layer Dependency Direction
* **Allowed**: `UI` $\rightarrow$ `Application` $\rightarrow$ `Domain` $\rightarrow$ `Abstractions` $\rightarrow$ `Implementations`.
* **Strictly Forbidden**:
  * Runtime $\rightarrow$ UI (Runtime knows nothing of presentation).
  * ROM Parser $\rightarrow$ Compose (Parsing logic is pure Kotlin/Java).
  * UI $\rightarrow$ Emulator Internals (UI interacts only with `BuildRequest` and descriptors).
  * Domain $\rightarrow$ Specific Emulator (Domain knows only abstract `Platform` and `RomIdentity`).

---

## 2. Constitutional Laws (Laws 1–22)

These laws are inviolable system invariants. Any violation is considered an architectural defect:

* **LAW 1 — The Core Must Not Know Specific Games**: No domain component may contain logic specific to Mario, Pokemon, Zelda, or individual game hashes. Quirks belong in metadata or runtime configuration.
* **LAW 2 — ROM and Runtime Are Separate**: A ROM describes content; a runtime executes content. Neither may become an implicit extension of the other.
* **LAW 3 — The Manager Never Executes Emulator Logic**: The Manager orchestrates; the Runtime executes; the Build Engine packages. No layer steals another layer's responsibility for convenience.
* **LAW 4 — Builds Are Declarative**: Every build must be fully representable as a `BuildRequest`. If the system cannot describe a build without procedural side-effects, the architecture is broken.
* **LAW 5 — One Build Engine**: Single builds and batch builds use the exact same Build Engine. Batch mode schedules multiple jobs; it never introduces a second packaging engine.
* **LAW 6 — Runtime Addition Must Not Require Core Redesign**: Adding a new console/emulator requires implementing the runtime contract and registering it. If adding a console requires rewriting the workflow, the abstraction is rejected.
* **LAW 7 — No Silent Fallbacks**: If a requested runtime, configuration, toolchain, or artifact fails to satisfy a requirement, the build fails explicitly. Never substitute a runtime silently.
* **LAW 8 — Every Artifact Has Provenance**: Every output APK must be traceable to its ROM hash, runtime version, configuration, and build environment.
* **LAW 9 — Build Success Is Not Runtime Success**: Compilation or APK generation does not prove emulator correctness. Runtime validation is a separate concern.
* **LAW 10 — External Repositories Are Dependencies, Not Architecture**: External projects (mGBA, ksupatcher, Retra) sit behind RetroPack-owned boundaries. RetroPack must not become a thin copy of any external repo.
* **LAW 11 — No Architectural Shortcut Becomes Permanent**: Prototypes that violate frozen boundaries must be eliminated before release.
* **LAW 12 — Evidence Is Required for Strong Claims**: No benchmark, compatibility, or security claim is accepted without reproducible evidence.
* **LAW 13 — Security Boundaries Are Explicit**: ROMs, external APKs, and tools are untrusted inputs until explicitly validated.
* **LAW 14 — Legal Distribution Is Not an Architectural Afterthought**: Tests use legally distributable public-domain homebrew content or user-supplied content.
* **LAW 15 — Frozen Means Frozen**: Layer boundaries, domain models, runtime abstractions, build requests, and artifact models are constitutional. Changing them requires formal revision.
* **LAW 16 — The Architecture Must Survive Its Own Growth**: If adding the 10th console is harder than adding the 2nd due to scattered conditionals, stop feature work and repair the abstraction.
* **LAW 17 — No Feature Is Allowed to Corrupt the Core**: A feature that cannot be implemented cleanly must either be redesigned, isolated behind an adapter, or rejected.
* **LAW 18 — The Simplest Correct Boundary Wins**: Prefer abstractions with fewer concepts, fewer states, and fewer special cases.
* **LAW 19 — Ported Code Must Be Encapsulated Behind Clean Adapters**: External models or database types must never leak into the Domain Core.
* **LAW 20 — Canonical Emulation Over Custom Forks**: Upstream emulator cores must remain unmodified submodules. All platform glue lives in the JNI bridge.
* **LAW 21 — Immutable Template Isolation**: The packager may only mutate explicitly allowlisted regions (`assets/`, manifest attributes, launcher icons). Bytecode (`classes*.dex`) and native binaries (`lib/*`) are strictly immutable.
* **LAW 22 — Verification Precedes Release**: No artifact is declared successful merely because signing completed. The pipeline must programmatically verify signature validity (`ApkVerifier`), 16 KB page alignment, and manifest sanity.

---

## 3. The Runtime Abstraction: `RuntimeTemplate`

The Manager interacts with emulation engines strictly through declarative **`RuntimeTemplate`** bundles:

```text
runtimes/<runtime-id>/
├── runtime.json        # Contract metadata, capabilities & schema
├── template.apk        # Precompiled generic runtime APK (unsigned)
└── licenses/           # Bundled legal notices (MIT, MPL-2.0, Apache-2.0)
```

### Runtime Discovery & Trust Anchor
* Discovery is registry-based (`RuntimeRegistry.kt`): Platform + Content $\rightarrow$ Compatible Runtime Set $\rightarrow$ Default Runtime.
* **Trust Anchor**: The trusted SHA-256 fingerprint of `template.apk` is **compiled directly into the Manager’s Kotlin bytecode**. Local `.sha256` text files are never used as trust roots.

---

## 4. Declarative Data Contracts & JSON Schemas

### Contract 1: Declarative Build Request (`BuildRequest`)
Consumed by the `BuildEngine` to execute a transformation:
```json
{
  "$schema": "https://retropack.org/schemas/v1/build-request.json",
  "version": 1,
  "identity": {
    "gameId": "emerald-01H",
    "gameTitle": "Pokemon Emerald",
    "packageName": "com.retropack.game.pokemonemerald_a91c37e4b2",
    "versionCode": 1,
    "versionName": "1.0.0"
  },
  "content": {
    "sourceRom": "pokemon_emerald.gba",
    "platform": "gba",
    "fileSize": 16777216,
    "checksums": {
      "crc32": "f3ae0888",
      "md5": "318d7d8538430213f82a2213ab4203fa",
      "sha1": "f3ae0888b06b0486da525fb3b728a4970707a274",
      "sha256": "a91c37e4b2d1847f9e0a2b4c6d8e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
    },
    "header": {
      "gameCode": "BPEE",
      "makerCode": "01",
      "romVersion": 0
    },
    "appliedPatch": null
  },
  "runtime": {
    "templateId": "mgba-unified",
    "audio": {
      "sampleRate": 44100,
      "bufferSize": 2048
    },
    "video": {
      "scaleMode": "integer_fit",
      "aspectRatio": "3:2"
    }
  },
  "controls": {
    "touch": {
      "enabled": true,
      "opacity": 0.65,
      "haptics": true
    },
    "gamepad": {
      "enabled": true,
      "autoHideTouch": true
    }
  },
  "storage": {
    "saveType": "battery_sram",
    "periodicFlushIntervalSec": 60
  },
  "signing": {
    "profileId": "default_managed"
  }
}
```

### Contract 2: Standalone Injected Config (`assets/retropack.json`)
Injected into the generated standalone APK and parsed at boot by `GameActivity`:
```json
{
  "$schema": "https://retropack.org/schemas/v1/runtime-config.json",
  "schema_version": 1,
  "game": {
    "id": "emerald-01H",
    "title": "Pokemon Emerald",
    "platform": "gba",
    "rom_sha256": "a91c37e4b2d1847f9e0a2b4c6d8e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
  },
  "runtime": {
    "core": "mgba",
    "audio_sample_rate": 44100,
    "audio_buffer_size": 2048,
    "video_scale_mode": "integer_fit"
  },
  "controls": {
    "touch_enabled": true,
    "touch_opacity": 0.65,
    "haptics": true
  },
  "storage": {
    "save_type": "battery_sram",
    "periodic_flush_interval_sec": 60
  },
  "provenance": {
    "manager_version": "0.1.0",
    "build_timestamp_utc": "2026-09-25T13:30:00Z"
  }
}
```

### Contract 3: Runtime Descriptor (`runtime.json`)
```json
{
  "id": "mgba-unified",
  "version": "0.10.5",
  "runtime_api": 1,
  "supported_platforms": ["gb", "gbc", "gba"],
  "supported_abis": ["arm64-v8a", "x86_64"],
  "min_sdk": 24,
  "target_sdk": 35,
  "rom_extensions": [".gb", ".gbc", ".gba"],
  "rom_asset_path": "assets/game.rom",
  "config_asset_path": "assets/retropack.json",
  "config_schema_version": 1,
  "capabilities": {
    "save_states": 4,
    "rewind": true,
    "fast_forward": true,
    "touch_controls": true,
    "physical_gamepad": true,
    "bios_optional": true
  },
  "protected_entries": {
    "classes.dex": "sha256:4a8b7c3d2e1f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b",
    "lib/arm64-v8a/libmgba.so": "sha256:9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e"
  }
}
```

---

## 5. Packaging Boundaries & Allowlist Policy

To guarantee security and eliminate arbitrary binary corruption, packaging adheres to a strict allowlist:

### Mutable Regions (Permitted Modifications)
1. `assets/game.rom` (Injected ROM bytes; path traversal sanitized).
2. `assets/retropack.json` (Injected runtime configuration).
3. `AndroidManifest.xml` (Root `package`, inlined literal `android:label`, `versionCode`, `versionName`).
4. `res/drawable-nodpi/ic_launcher_foreground.png` (Boxart PNG layer).
5. `res/drawable-nodpi/ic_launcher_background.png` (Background PNG layer).

### Immutable Regions (Strictly Forbidden from Modification)
1. `classes*.dex` (All compiled bytecode, `GameActivity`, JNI wrappers).
2. `lib/*` (All native shared libraries, `libmgba.so`).
3. Android Permissions (No runtime permissions may be added or removed).
4. Component Definitions (`<activity>`, `<intent-filter>` declarations).
5. Compiled Resource Tables (`resources.arsc` is never touched).

*Integrity Check*: The `BuildEngine` computes the SHA-256 of all `protected_entries` prior to signing. If any hash differs from `runtime.json`, the build fails closed.

---

## 6. Cryptography & Hybrid Keystore Lifecycle

To eliminate the conflict between hardware non-exportability and user migration/backup requirements:

```text
[Master Key: Android Keystore System] (Hardware TEE/StrongBox, AES-256-GCM, Non-Exportable)
                     │
                     ▼ (Protects at rest)
[Game Signing Key: Software RSA-2048 / EC P-256] (Stored encrypted in app-private storage)
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
  [In-Memory Signing]    [Password-Protected Export]
  apksig (v1 + v2 + v3)  Standard .p12 / .jks file for migration
```

1. **Portable Managed Keystore (Default)**:
   * Software key pair generated in memory (BouncyCastle RSA-2048 or EC P-256).
   * 30-year self-signed X.509 certificate with `KeyUsage(digitalSignature)` and `ExtendedKeyUsage(codeSigning)`.
   * Private key bytes encrypted at rest in app-private storage using an AES-256-GCM master key bound to `AndroidKeyStore`.
   * *Signing Compatibility*: Because private key bytes are accessible to `apksig` in memory, `PrivateKey.getEncoded()` returns valid ASN.1 DER data, allowing single-pass **v1, v2, and v3 signing without crashes**.
2. **Exportable User Backup**: Users can export their signing identity to a standard password-protected `.p12` or `.jks` file for backup or cross-device transfer.
3. **Custom Keystore**: Advanced users can supply an existing external `.jks` file.
4. **Device-Bound Keystore (Paranoid Opt-in)**: Pure hardware `AndroidKeyStore` key pair (drops v1 signing; non-migratable).

---

## 7. Low-Level Android Invariants & Physics

### Invariant 1: Fully-Qualified Activity Identifiers
* In `template.apk`'s manifest, activities are declared using their fully-qualified class names:
  ```xml
  <activity android:name="com.retropack.runtime.GameActivity" android:exported="true" ... />
  ```
* *Physics*: If shorthand notation (`android:name=".GameActivity"`) were used, mutating `<manifest package="...">` would cause Android to look for `GameActivity` under the new game package, triggering `ClassNotFoundException`. Using fully-qualified names keeps class resolution intact.

### Invariant 2: Uncompressed Native Libraries (`extractNativeLibs="false"`)
* Manifest declares `<application android:extractNativeLibs="false" ...>`.
* Forces Android 15 (API 35+) to memory-map uncompressed shared libraries directly from the APK container at runtime.

### Invariant 3: 16 KB Page Alignment & ZIP Ordering
* Canonical mGBA linked with NDK r28b+ flags: `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
* *Archive Assembly Order*: Mutating AXML or injecting assets alters byte lengths and shifts subsequent ZIP offsets. The packaging engine uses `com.android:zipflinger` to compute physical alignment padding **after** all variable-length entries are sized and sequenced. Uncompressed `.so` entries are written with `alignment = 16384`.
* Programmatic verification asserts `(local_header_payload_offset % 16384 == 0)`.

### Invariant 4: Deterministic Package Identity
* Package name formula:
  $$\text{PackageName} = \text{"com.retropack.game."} + \text{slug} + \text{"\_"} + \text{hash10}$$
  * `slug`: Lowercase ASCII alphanumeric `[a-z0-9]`, max 16 chars (prefixed with `g_` if starting with a digit).
  * `hash10`: First 10 hex characters of `SHA-256(sourceRomBytes)`.
  * *Update Guarantee*: Rebuilding or patching reuses the package name and signing profile, incrementing `versionCode`. Android OS installs the update in-place, preserving SRAM saves and save-states in `filesDir/`.

### Invariant 5: Atomic Staging & POSIX `fsync` Durability
* **Atomic First-Boot ROM Staging**: Native mGBA requires standard POSIX paths. On launch, `GameActivity` checks if `filesDir/game.rom` exists and matches `rom_sha256`. If missing or updated, it streams `assets/game.rom` $\rightarrow$ `filesDir/game.rom.tmp`, verifies SHA-256, and executes `tmp.renameTo(filesDir/game.rom)`.
* **Crash-Consistent Battery SRAM Save**:
  * 60-second periodic background check (flushes only if SRAM is marked dirty).
  * Immediate flush in `onPause()` and `onStop()`.
  * Write sequence: write `filesDir/game.sav.tmp` $\rightarrow$ call `fileDescriptor.sync()` (POSIX `fsync`) $\rightarrow$ atomic `renameTo(filesDir/game.sav)`.
