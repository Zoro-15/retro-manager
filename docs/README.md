# RetroPack — Product Page

A handcrafted, zero-dependency product page for **[RetroPack](https://github.com/Zoro-15/retro-manager)** —
the on-device Android tool that transforms legally-owned retro game ROMs into autonomous, standalone APK
applications (10 canonical emulator cores, Game Boy through PSP).

The page is built with pure HTML, CSS and vanilla JavaScript. No frameworks, no build step, no trackers —
unzip it and open it.

---

## Quick Start

**Option A — just open it**

```
unzip retropack-product-page.zip
open index.html          (macOS)
```
or double-click `index.html` in any file manager.

**Option B — serve it locally** (recommended, some browsers restrict `file://` font loading)

```bash
cd retropack-product-page
python3 -m http.server 8000
# → http://localhost:8000
```

Deploy anywhere static: GitHub Pages, Netlify, Vercel, Cloudflare Pages, or a plain nginx box.
Point the web root at this folder. Done.

---

## What's Inside

```
retropack-product-page/
├── index.html                  The entire page, semantic HTML5
├── README.md                   This file
└── assets/
    ├── favicon.svg             Pixel-cartridge icon (SVG)
    ├── css/
    │   ├── base.css            Design tokens, reset, typography scale
    │   ├── components.css      Nav, buttons, terminal window, card primitives
    │   ├── sections.css        Hero, stats, forge, pipeline, platforms, bento,
    │   │                       architecture, proof, CTA, footer + responsive rules
    │   └── effects.css         Boot preloader, CRT overlay, cursor, glitch,
    │                           scroll reveals, DMG mode, reduced-motion support
    └── js/
        ├── particles.js        Canvas phosphor-pixel field (parallax, twinkle)
        ├── boot.js             CRT power-on boot sequence
        ├── terminal.js         Live replay of the real 15-step build pipeline
        ├── phone.js            Miniature pixel game inside the hero phone mockup
        └── interactions.js     Nav, cursor, magnetic buttons, 3D tilt, count-ups,
                                shader lab, DMG mode, copy-to-clipboard, reveals
```

Total payload: ~200 KB (uncompressed), no images — every visual is CSS, SVG or canvas.

---

## The Design System

The palette is **not invented** — it is lifted 1:1 from the actual RetroPack Android app
(`app/src/main/kotlin/com/retropack/manager/ui/theme/Color.kt`). The website wears the
product's own clothes.

| Token | Hex | Role |
| --- | --- | --- |
| `--bg-0` | `#05060A` | Deepest OLED black (preloader, footer) |
| `--bg-1` | `#090A0F` | Page background — the app's `RetroDarkBackground` |
| `--bg-2 / 3 / 4` | `#10131B / #171B26 / #1E2433` | Surfaces, cards, elevation |
| `--indigo` | `#6366F1` | Electric Indigo — primary accent |
| `--teal` | `#06B6D4` | Cyber Teal — secondary accent |
| `--violet` | `#A855F7` | Neon Violet — tertiary accent |
| `--green` | `#10B981` | Terminal success / CI green |
| `--amber` | `#F59E0B` | Warnings, "PRESS START" |
| `--p-gb … --p-psp` | 12 platform colors | Per-console brand glows (GB, GBC, GBA, SNES, Genesis, NES, PCE, Arcade, PS1, N64, PSP, NDS) |

**Typography**

| Font | Used for |
| --- | --- |
| Space Grotesk | Display headlines, card titles, buttons |
| Inter | Body copy |
| JetBrains Mono | Terminal, stats, labels, package names |
| Press Start 2P | Pixel eyebrows, chips, boot screen — the retro DNA, used sparingly |

Fonts load from Google Fonts with system-stack fallbacks, so the page still looks
intentional while offline.

---

## Page Tour

| # | Section | Highlight |
| --- | --- | --- |
| 0 | Boot preloader | BIOS-style boot lines, pixel loading bar, CRT power-on flash |
| 1 | Hero | Glitching gradient headline, live pixel-game phone mockup with touch-overlay HUD, floating verification chips, platform marquee |
| 2 | Stats band | Six count-up metrics (10 cores, 16+ platforms, 15 steps, 100% on-device, 0 cloud, 41 CI suites) |
| 3 | The Forge | The app's real 3-step stepper flow as tilt cards |
| 4 | Pipeline | **Live terminal simulation** of the actual 15-step BuildEngine sequence — typed command, spinners turning into checks, synced progress bar, replay button |
| 5 | Platforms | 10 core cards, each glowing in its console's brand color, with chips for all 16+ supported systems |
| 6 | Features bento | Interactive Shader Lab (click crt / lcd / dmg / sharp / boost), Oboe audio waves, haptic bars, controller SVG, bezel preview, save-state slots |
| 7 | Philosophy ticker | Auto-scrolling engineering principles from `context.md` |
| 8 | Architecture | ROM → ANALYZE → RUNTIME → PACKAGE → APK flow diagram + the four separated subsystems |
| 9 | Proof | Animated CI ring, 22/22 laws, 5/5 physics invariants, homebrew fixture badges, the Non-Goals oath |
| 10 | CTA + Footer | "STOP EMULATING. START FORGING.", one-click clone command, full attribution |

**Hidden trick:** press the **DMG** toggle in the nav (or the link in the footer) to bathe
the entire page in authentic 1989 pea-green Game Boy phosphor.

---

## Interactive Effects

- CRT power-on boot sequence (skippable — click or press any key)
- Canvas phosphor-pixel starfield with mouse parallax and scroll drift
- Subtle CRT scanline overlay with a slow refresh-roll band
- Custom cursor: indigo dot + lerped teal ring that expands over interactive elements
- Magnetic buttons that lean toward the pointer
- 3D tilt cards (platforms, forge, architecture, proof)
- Scroll-triggered staggered reveals (IntersectionObserver)
- Glitch bursts on the hero headline
- Infinite marquees (hover to pause)
- Live 15-step terminal build simulation with replay
- Count-up statistics
- Shader Lab: five shader presets applied to a CSS synthwave scene
- Copy-to-clipboard clone command with toast
- DMG pea-green phosphor mode

**Respect built in:** `prefers-reduced-motion` disables the particle field, marquee,
glitch, cursor, preloader and heavy animation — the page remains fully readable.
Touch devices automatically drop the cursor and tilt effects. Everything pauses when
the tab is hidden.

---

## Customization

- **Colors** — edit the token block at the top of `assets/css/base.css`. Every accent,
  surface and platform color is a CSS custom property.
- **Content** — all copy lives in `index.html`; each section is a commented
  `<!-- ═══ SECTION ═══ -->` block.
- **Terminal script** — the 15 steps are defined in the `STEPS` array in
  `assets/js/terminal.js`, mirroring `masterplan.md` §6.
- **Stats** — `data-counter` attributes on the spans in the stats band.
- **Add a platform card** — duplicate a `.plat-card` article and set its
  `style="--plat:#HEX"` to the console's brand color.

---

## Browser Support

Modern evergreen browsers (Chrome, Edge, Firefox, Safari — desktop and mobile).
Uses CSS custom properties, `color-mix()`, `aspect-ratio`, conic gradients,
IntersectionObserver and canvas — all baseline since 2023.
No JavaScript bundler required; scripts are plain ES5-friendly modules wrapped in IIFEs.

---

## Credits & Licensing

This page is a presentation layer for the open-source RetroPack project:

- **RetroPack repository** — https://github.com/Zoro-15/retro-manager
- Runtime foundations: **mGBA** (MPL-2.0), **garnacha-boy-android** (MIT),
  **ksupatcher / Retra / Ludere** (GPL-3.0)
- Build toolchain referenced: ARSCLib, zipflinger, apksig, Oboe, Libretro Thumbnails

RetroPack and this page are not affiliated with Nintendo, Sony, Sega, NEC or SNK.
The page distributes no ROMs, BIOS files or copyrighted assets — only engineering.

Page source: handcrafted HTML/CSS/JS. Do whatever you like with it.
