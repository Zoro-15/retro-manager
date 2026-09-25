# RetroPack — Non-Goals

> **Document Status**: Architectural Boundary & Anti-Requirements Specification  
> **Purpose**: Prevent scope creep, preserve legal clarity, and enforce constitutional invariants by explicitly documenting what RetroPack will **never** do.

---

## 1. Distribution & Content Boundaries

* **RetroPack will NEVER distribute or bundle copyrighted commercial ROMs.**
  * The system is an orchestrator and packager for content legally owned and provided by the user.
* **RetroPack will NEVER distribute proprietary BIOS or firmware files.**
  * For platforms requiring proprietary firmware (e.g., PS1, BIOS-reliant systems), the system will require the user to supply the firmware via the Storage Access Framework. For GBA, mGBA's open-source High-Level Emulation (HLE) BIOS is utilized.
* **RetroPack will NEVER become a ROM download service, scraper, or piracy portal.**
  * There are no torrent clients, scraper downloads of game binaries, or integration with unauthorized ROM distribution websites.

---

## 2. Emulation & Runtime Architecture Boundaries

* **RetroPack will NEVER become a generic emulator frontend.**
  * RetroPack is not RetroArch, LaunchBox, or Daijishō. It does not exist to organize a large ROM directory into a carousel menu to play within a single emulator shell.
  * Its sole goal is producing **autonomous, standalone Android game applications**.
* **RetroPack will NEVER maintain a custom fork of upstream emulator cores.**
  * Core emulator engines (such as canonical mGBA) must remain unmodified, pinned upstream releases integrated via standard CMake and NDK submodules. All Android-specific host logic lives strictly in the JNI bridge (`retropack-runtime-mgba`).
* **RetroPack will NEVER implement a shared-companion runtime updater.**
  * Generated APKs are 100% self-contained. RetroPack explicitly rejects the "companion runtime app" model that turns game APKs into dependent shells. If an emulator core is updated, the user updates their game by rebuilding the standalone APK.
* **The runtime will NEVER execute dynamic code from outside the APK.**
  * To comply with Android security standards, generated APKs will not download dynamic DEX files or remote native `.so` libraries at runtime. Everything necessary to execute the game is bundled deterministically inside the APK.

---

## 3. Cryptography & App Store Publishing Boundaries

* **RetroPack will NEVER ship an embedded private release-signing key.**
  * Sideloaded testing uses a locally generated, device-bound Android Keystore key.
  * Public release keys belong strictly to individual users.
* **RetroPack will NOT guarantee Play Store publishing readiness for generated APKs.**
  * Because generated APKs contain game ROMs supplied by users, RetroPack is designed for personal sideloading, preservation, and device portability. It does not provide automated Google Play Console deployment, Play Integrity API bindings, or billing integration.
* **RetroPack will NOT guarantee bit-for-bit identical final signed APKs.**
  * While pre-signed archive structures and build inputs are 100% deterministic, standard cryptographic signing schemes (v2/v3) incorporate random cryptographic nonces. Byte-level determinism is guaranteed up to the pre-signing ZIP state.
* **The native emulator will NOT attempt direct POSIX `fopen()` on APK assets.**
  * Android APK assets do not map to standard Linux file descriptors. The runtime stages the ROM to app-private storage (`filesDir/game.rom`) on first boot, then memory-maps the file directly.

