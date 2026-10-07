# RetroPack Native Controller Architecture (`modcontrol.md`)

## 1. Core Architecture Overview
- **Implementation**: Pure C++ OpenGL ES 2.0 vector overlay in `native_engine/src/input/virtual_pad.cpp` & `virtual_pad.hpp`.
- **Selected Style**: PPSSPP Floating Thumbstick + Frosted Glass Contours.
- **Default Opacity**: `30%` (`m_opacity = 0.30f`) with dynamic highlight (`0.75f`) on active touch.

---

## 2. Dynamic Floating Thumbstick Specifications
- **Dynamic Anchoring**: When touching anywhere in the left $45\%$ screen zone, the stick base center anchors at touch $(X, Y)$.
- **Radius & Travel**: Outer base $R = 78 \cdot \text{scale}$, Nub $R = 34 \cdot \text{scale}$, Max travel $= 65 \cdot \text{scale}$, Deadzone $= 12 \cdot \text{scale}$.
- **8-Way Directional Mapping**: Converts $360^\circ$ radial sweep ($\theta = \text{atan2}(\Delta Y, \Delta X)$) to digital bitmasks (`BTN_UP`, `BTN_DOWN`, `BTN_LEFT`, `BTN_RIGHT` and $45^\circ$ diagonals).
- **Auto-Return**: Centering resets smoothly to resting anchor when touch is released.

---

## 3. Console-Adaptive Layout Matrix
| Platform / Core | Left Input | Action Buttons | Shoulders | Utilities |
|---|---|---|---|---|
| **GBA** (`mgba`) | Floating Thumbstick | Angled A & B ($25^\circ$ thumb sweep arc) | L & R | Select, Start, Menu |
| **NES** (`fceumm`) | Floating Thumbstick | Horizontal A & B (X/Y hidden) | None | Select, Start, Menu |
| **SNES** (`snes9x`) | Floating Thumbstick | Diamond 4-Button (A, B, X, Y) | L & R | Select, Start, Menu |
| **Genesis** (`genesis_plus_gx`) | Floating Thumbstick | 4-Button / 3-Button Cluster | L & R | Start, Menu |
| **PC-Engine** (`mednafen_pce_fast`) | Floating Thumbstick | Buttons I & II (A & B) | None | Select, Run, Menu |

---

## 4. Key Modification Hooks for Upcoming Tasks

### A. Auto-Hide On-Screen Controls on Physical Gamepad Connection
- **Mechanism**: Listen to `AINPUT_EVENT_TYPE_KEY` or Android InputDevice attach/detach notifications in `main.cpp` (`handleEngineInput`).
- **Hook**: When `(AInputEvent_getSource(event) & AINPUT_SOURCE_JOYPAD) != 0` or physical button detected, call `ctx.virtualPad.setVisible(false)`.
- **Restore**: If user touches touch screen, re-invoke `ctx.virtualPad.setVisible(true)`.

### B. In-Engine OSD Menu Customization
- **File**: `native_engine/src/ui/osd_menu.cpp` & `osd_menu.hpp`.
- **Trigger**: Top-center `MENU` button triggers `ctx.osdMenu.open()`, automatically pausing core & audio.
- **Available Actions**: Save State (5 slots), Load State, Fast-Forward ($2\times, 4\times, 8\times$), Reset Core, Exit to Launcher.
- **Render Pipeline**: Rendered with alpha blending on top of game quad, finalized with `ctx.renderer.present()`.

### C. Font & Glyph Rendering Engine
- **File**: `native_engine/src/ui/font_renderer.cpp` & `font_renderer.hpp`.
- **Mechanism**: Zero-dependency embedded ASCII bitmap font table uploaded to an OpenGL ES 2.0 alpha texture atlas.
- **Features**: Dual-pass batch quad rendering (subtle drop-shadow + crisp foreground) for razor-sharp button labels ("A", "B", "X", "Y", "L", "R", "SELECT", "START", "MENU") and in-engine OSD menus at any resolution.

### D. Opacity & Button Mapping Customization
- **Runtime Setters**: `virtualPad.setOpacity(float)` and `virtualPad.setConsoleLayout(ConsoleLayout)`.
- **Hitbox Tuning**: Radius multiplier in `hitTestCircle` ($1.35\times$ base) for generous multi-touch registered areas.

