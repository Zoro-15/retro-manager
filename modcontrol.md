# RetroPack Native Controller Architecture (`modcontrol.md`)

## 1. Core Architecture Overview
- **Implementation**: Pure C++ OpenGL ES 2.0 vector overlay in `native_engine/src/input/virtual_pad.cpp` & `virtual_pad.hpp`.
- **Selected Styles**: 
  - **Dynamic Floating Thumbstick**: PPSSPP style with dynamic anchor on touch, glowing directional guide ticks, and inner jewel.
  - **Classic Frosted Glass D-Pad**: High-visibility cross with directional arrows (`^`, `v`, `<`, `>`), central hub, and dynamic active lighting on pressed directions & diagonals.
- **Left Input Mode Toggle**: Instant runtime switching between `JOYSTICK` and `DPAD` via in-engine OSD Menu or custom HUD Editor.

---

## 2. Dynamic Floating Thumbstick & Cross D-Pad Specifications
- **Floating Thumbstick**:
  - Dynamic Anchoring: Touching in the left $45\%$ zone anchors stick base at $(X, Y)$.
  - Radius & Travel: Outer base $R = 78 \cdot \text{scale}$, Nub $R = 34 \cdot \text{scale}$, Max travel $= 65 \cdot \text{scale}$, Deadzone $= 12 \cdot \text{scale}$.
  - 8-Way Directional Mapping: Converts $360^\circ$ radial sweep ($\theta = \text{atan2}(\Delta Y, \Delta X)$) to digital bitmasks (`BTN_UP`, `BTN_DOWN`, `BTN_LEFT`, `BTN_RIGHT` and $45^\circ$ diagonals).
- **Cross D-Pad Mode**:
  - Fixed responsive positioning with configurable scale ($0.5\times$ to $2.0\times$) and opacity ($10\%$ to $100\%$).
  - Multi-touch 8-way directional detection with generous bevel hitboxes and diagonal chord triggers.

---

## 3. Free Fire Style Custom HUD Editor Panel
- **Trigger**: In-Engine OSD Menu -> **"Customize Controls"**.
- **Interactive Canvas**:
  - Direct touch selection of any control element on screen.
  - Real-time drag-and-drop movement anywhere across screen coordinates with normalized screen-resolution resilience (`normX`, `normY`).
  - Active selection highlight with glowing gold/cyan borders, corner accent brackets, and floating button name badges.
- **Top Header Toolbar**:
  - `[ < EXIT ]`: Exits custom HUD mode with automatic state commit.
  - `[ < HUD 1 / 2 > ]`: Multi-profile preset switcher (Profile 1 vs Profile 2).
  - `[ ↺ RESET ]`: Reverts current console layout to optimized defaults.
  - `[ 💾 SAVE ]`: Atomic persistence commit to `/files/controls_config.dat`.
  - `[ MODE: STICK / D-PAD ]`: Quick toggle between Floating Thumbstick and Cross D-Pad.
- **Property Inspector Panel** (Collapsible with chevron toggle):
  - Selected button name & target description.
  - **Opacity Slider**: Interactive track bar adjusting opacity from $10\%$ to $100\%$.
  - **Size Slider**: Interactive track bar adjusting scale from $50\%$ to $200\%$.
  - **Nudge D-Pad**: 4 micro-adjustment directional buttons (`[▲]`, `[▼]`, `[◀]`, `[▶]`) for single-pixel precision.

---

## 4. Enlarged Touch-Friendly OSD Pause Menu
- **Dimensions**: Dynamically scaled to $84\%$ screen width and $92\%$ screen height with responsive UI multiplier (`baseScale`).
- **Ergonomics**: Generous button heights ($46\text{px} \cdot \text{scale}$) and increased font sizing for easy thumb operation on all touch screens.
- **Menu Matrix**:
  - Header: `RETROPACK MENU` with glowing accent bar.
  - Row 1: 5 Save/Load Slot Selector Pills (`SLOT 1` - `SLOT 5`) with save presence indicators.
  - Row 2: Large primary `Resume Game` button (Emerald Green).
  - Row 3: Dual columns for `Customize Controls` (Cyan) and `Input: [Joystick / D-Pad]` (Indigo).
  - Row 4: Dual columns for `Save State (Slot X)` (Cobalt Blue) and `Load State (Slot X)` (Purple).
  - Row 5: `Fast-Forward (1x / 2x / 4x / 8x)` (Orange / Slate).
  - Row 6: Dual columns for `Reset Emulation` (Warm Red) and `Exit to Launcher` (Crimson).

---

## 5. Console-Adaptive Layout Matrix
| Platform / Core | Left Input | Action Buttons | Shoulders | Utilities |
|---|---|---|---|---|
| **GBA** (`mgba`) | Stick / D-Pad | Angled A & B ($25^\circ$ thumb sweep arc) | L & R | Select, Start, Menu |
| **NES** (`fceumm`) | Stick / D-Pad | Horizontal A & B (X/Y hidden) | None | Select, Start, Menu |
| **SNES** (`snes9x`) | Stick / D-Pad | Diamond 4-Button (A, B, X, Y) | L & R | Select, Start, Menu |
| **Genesis** (`genesis_plus_gx`) | Stick / D-Pad | 4-Button / 3-Button Cluster | L & R | Start, Menu |
| **PC-Engine** (`mednafen_pce_fast`) | Stick / D-Pad | Buttons I & II (A & B) | None | Select, Run, Menu |

---

## 6. Atomic Persistence Architecture
- **Target File**: `<internalDataPath>/controls_config.dat`.
- **Format**: Structured key-value text profile containing layout version, active profile index, active left input mode, and per-element normalized vectors (`normX`, `normY`, `scale`, `opacity`).
- **Durability**: Written to `.tmp` file with atomic POSIX `rename()` replacement.


