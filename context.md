# RetroPack — System Context, Vision & Philosophy (`context.md`)

> **Document Status**: Definitive Vision, Philosophy & Objectives Specification (v0.1)  
> **Session Entry Point**: Primary introductory document for incoming AI agents and engineers.  
> **Detailed Cross-References**: Directly points to exact lines in [`architechture.md`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md), [`masterplan.md`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md), [`roadmap.md`](file:///c:/Users/ok/Documents/retro%20manager/roadmap.md), and [`non_goals.md`](file:///c:/Users/ok/Documents/retro%20manager/non_goals.md).

---

## 1. System Mission & Core Paradigm

RetroPack exists to convert user-supplied, legally owned retro game ROMs into autonomous, first-class, standalone Android applications:

$$\text{ROM} \longrightarrow \text{Analyzed Content} \longrightarrow \text{Selected Runtime} \longrightarrow \text{Configured Package} \longrightarrow \text{Standalone APK}$$

* **Architecture Model**: **On-Device Structured APK Transformation Pipeline**. We do not compile C/C++ or DEX on the user's mobile device. We inject assets, mutate structured AXML manifests, align entries to 16 KB page boundaries, and cryptographically sign a precompiled generic runtime template APK.
* **Fundamental Architectural Separation**: **Manager $\neq$ Runtime $\neq$ Template $\neq$ APK Transformer $\neq$ Signing Identity**.
  * The Manager understands runtime **capabilities**, never runtime **implementation**.
  * The Runtime Host understands **emulation contracts**, never specific game titles.
  * The Standalone APK is 100% self-contained and operates without any companion app.

---

## 2. Initial Boundary (Version 0.1)

Version 0.1 focuses on the initial conformance domain:
1. **Game Boy (`.gb`)**
2. **Game Boy Color (`.gbc`)**
3. **Game Boy Advance (`.gba`)**

* **Unified Core Decision**: Canonical **mGBA 0.10.x** is the single unified runtime for all three systems, integrated via a dedicated Android NDK JNI bridge (`retropack-runtime-mgba.aar`). This eliminates multi-core binary bloat and harmonizes battery SRAM, Flash, and save-state handling across platforms.
* *Detailed Implementation Specs*: See [`masterplan.md#L75-L83`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L75-L83).

---

## 3. The 14 Core Engineering Philosophies

These principles govern every architectural and implementation decision:

1. **Architecture Is a Constraint, Not a Suggestion**: Architecture exists to constrain future complexity. Prefer extending a stable boundary over casual modification.
2. **Mechanism Over Policy**: Core infrastructure provides mechanisms; policy belongs above it. The runtime layer tests if it can execute content; the product layer decides what to recommend.
3. **Declarative Over Procedural**: Describe *what* must be built (`BuildRequest`), never make callers script *how* each build step occurs.
4. **Evidence Over Claims**: Every claim ("compatible", "fast", "16 KB ready") requires reproducible test fixtures or physical measurement.
5. **Correctness Before Coverage**: Supporting three consoles correctly is infinitely more valuable than supporting thirty unreliably.
6. **Boring Core, Sophisticated Edges**: Central contracts remain small and auditable. Sophistication belongs in runtime adapters and UI.
7. **No Hidden Coupling**: Explicit contracts only. No accidental coupling via global state, filesystem conventions, or undocumented variables.
8. **Optimize After Measurement**: Performance work begins only with measured profiling data.
9. **Failure Is Data**: Failed builds must identify the failing layer, providing actionable provenance rather than generic errors.
10. **Compatibility Is a Contract**: Support requires concrete evidence across build, install, launch, input, audio/video, and save durability.
11. **User Content Remains User Content**: RetroPack does not own or distribute user ROMs; provenance remains explicit.
12. **Future-Proofing Means Stable Abstractions**: Identify boundaries that future features must respect rather than guessing future features.
13. **Stand on Proven Shoulders (Grounded Architecture)**: Do not use generative AI to write complex emulators, JNI bridges, or packaging engines from scratch. Harvest proven mechanisms from battle-tested repositories (`ksupatcher`, `Retra`, `garnacha-boy-android`, `Ludere`).
14. **Respect Lineage & Attribution**: Always credit original authors, retain licenses, and provide clear open-source provenance.

---

## 4. The 11 System Objectives (O1–O11)

* **O1 — Deterministic Packaging**: For identical inputs, produce functionally equivalent artifacts with traceable build provenance. (See [`architechture.md#L88-L148`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L88-L148)).
* **O2 — Runtime Independence**: The Manager contains no emulator-specific logic; new platforms register behind `RuntimeTemplate`. (See [`architechture.md#L69-L84`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L69-L84)).
* **O3 — Content Independence**: ROM parsing and platform identification are decoupled from execution engines. (See [`masterplan.md#L97-L109`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L97-L109)).
* **O4 — Build Isolation**: Builds consume declarative `BuildRequest` payloads without procedural UI side-effects. (See [`architechture.md#L88-L148`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L88-L148)).
* **O5 — Extensibility Without Architectural Mutation**: New platforms and packaging engines integrate behind frozen boundaries. (See [`architechture.md#L8-L38`](file:///c:/Users/ok/Documents/retro%20manager/architechture.md#L8-L38)).
* **O6 — Reproducibility**: Artifacts are traceable to ROM SHA-256, runtime version, and build environment.
* **O7 — Failure Transparency**: Errors explicitly identify the failing stage (`INGESTION`, `MUTATION`, `ALIGNMENT`, `SIGNING`).
* **O8 — Batch-Native Design**: Single builds and batch builds share the identical Build Engine.
* **O9 — Real-Hardware Validation**: Artifact validation mandates execution on physical ARM64 devices (including 16 KB kernels). (See [`roadmap.md#L182-L208`](file:///c:/Users/ok/Documents/retro%20manager/roadmap.md#L182-L208)).
* **O10 — Measurable Quality**: Every performance and stability claim has an explicit test fixture.
* **O11 — Grounded Foundations**: Adapt established open-source projects rather than writing speculative code. (See [`masterplan.md#L8-L32`](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L8-L32)).

---

## 5. Definition of Success

RetroPack succeeds when:

> Given a supported ROM and a valid runtime, RetroPack deterministically produces an installable, standalone Android application on-device that launches that specific game with expected controls, audio/video synchronization, and crash-consistent battery SRAM persistence.

---

## 6. Master Architectural Navigation Map

For incoming AI agents and engineers, here is the complete index pointing to exact lines across the core specification files:

```text
┌──────────────────────────┬────────────────────────────────────────────────────────────────────────┐
│ If You Need Details On:  │ Consult File & Exact Line Reference                                    │
├──────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ 7 Architectural Layers   │ architechture.md lines 8–38 (Layer models & dependency directions)     │
│ Constitutional Laws 1-22 │ architechture.md lines 40–66 (Inviolable system invariants)            │
│ RuntimeTemplate Contract │ architechture.md lines 69–84 (Descriptors & bytecode SHA-256 anchor)   │
│ BuildRequest JSON Schema │ architechture.md lines 88–148 (Declarative build input contract)       │
│ retropack.json Schema    │ architechture.md lines 150–182 (Injected runtime config contract)      │
│ runtime.json Schema      │ architechture.md lines 184–212 (Capability & protected entries schema) │
│ Mutable/Immutable Bounds │ architechture.md lines 215–235 (Allowlist policy: AXML, icons, assets) │
│ Hybrid Keystore System   │ architechture.md lines 237–262 (AES-GCM master + software RSA/EC keys) │
│ Fully Qualified Activity │ architechture.md lines 266–272 (Preventing ClassNotFoundException)     │
│ 16 KB Page Zipalign      │ architechture.md lines 277–281 (zipflinger alignment ordering)         │
│ Deterministic Package ID │ architechture.md lines 282–288 (Package name formula & update continuity)│
│ SRAM POSIX fsync Contract│ architechture.md lines 289–295 (Durability sequence & dirty check)     │
├──────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ 4 Foundation Repositories│ masterplan.md lines 8–32 (ksupatcher, Retra, garnacha-boy, Ludere)     │
│ Clean-Room Staging Guide │ masterplan.md lines 34–73 (Promotion flow & harvesting matrix)         │
│ Unified mGBA Core Plan   │ masterplan.md lines 75–83 (GB/GBC/GBA single-core strategy)           │
│ Manager App Architecture │ masterplan.md lines 85–119 (Compose M3, Retra parser, collision logic) │
│ Runtime Host Architecture│ masterplan.md lines 121–208 (GameActivity bootstrap & subsystem specs) │
│ Pinned CMakeLists.txt    │ masterplan.md lines 132–166 (NDK r28b+ 16 KB CMake script)             │
│ JNI NativeCore.kt        │ masterplan.md lines 167–189 (Native C-to-Kotlin JNI declarations)      │
│ 15-Step Verified Pipeline│ masterplan.md lines 210–268 (Step-by-step transformation flow)         │
├──────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ Implementation Roadmap   │ roadmap.md lines 23–210 (Phases 0 through 6 & acceptance gates)        │
│ Master Cross-Ref Index   │ roadmap.md lines 212–239 (Full implementation-to-spec index)           │
├──────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ Constitutional Non-Goals │ non_goals.md lines 1–45 (Anti-requirements, legal & store limits)      │
└──────────────────────────┴────────────────────────────────────────────────────────────────────────┘
```
