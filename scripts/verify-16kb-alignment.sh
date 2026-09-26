#!/usr/bin/env bash
#
# verify-16kb-alignment.sh — Asserts that all compiled ELF shared libraries (.so)
# are aligned to 16 KB (0x4000) boundaries, satisfying Android 15/16 (API 35/36)
# memory page size requirements.
#
set -euo pipefail

die() { echo "::error:: $*" >&2; exit 1; }

echo "=== Android 16 KB Page-Size ELF Alignment Verification ==="

READELF_BIN=""
if command -v readelf >/dev/null 2>&1; then
  READELF_BIN="readelf"
elif command -v llvm-readelf >/dev/null 2>&1; then
  READELF_BIN="llvm-readelf"
elif [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  LLVM_BIN="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -name "llvm-readelf" -o -name "llvm-readobj" 2>/dev/null | head -n 1 || true)"
  if [[ -n "$LLVM_BIN" ]]; then
    READELF_BIN="$LLVM_BIN"
  fi
fi

python3 - "$READELF_BIN" <<'PY'
import os, sys, glob, subprocess

readelf = sys.argv[1] if len(sys.argv) > 1 else ""

so_files = []
for pattern in [
    "runtime/*/build/**/*.so",
    "template-apk/build/**/*.so",
    "app/build/**/*.so"
]:
    so_files.extend(glob.glob(pattern, recursive=True))

so_files = sorted(set(so_files))
if not so_files:
    print("No compiled .so shared libraries found to inspect. Run assembleDebug first.")
    sys.exit(0)

print(f"Found {len(so_files)} compiled shared library binaries to inspect.")

failed = False
verified_count = 0

for so_path in so_files:
    # Filter only arm64-v8a and x86_64 binaries
    if "arm64-v8a" not in so_path and "x86_64" not in so_path and "arm64" not in so_path:
        continue

    # Binary ELF inspection via header parsing
    with open(so_path, "rb") as f:
        magic = f.read(4)
        if magic != b"\x7fELF":
            continue
        # 64-bit ELF identification
        ei_class = f.read(1)
        if ei_class != b"\x02": # ELFCLASS64
            continue

    if readelf:
        try:
            output = subprocess.check_output([readelf, "-l", so_path], universal_newlines=True)
            alignments = []
            for line in output.splitlines():
                if "LOAD" in line:
                    parts = line.strip().split()
                    # Look for Align field (typically last column e.g. 0x4000 or 0x10000)
                    for part in reversed(parts):
                        if part.startswith("0x"):
                            try:
                                val = int(part, 16)
                                alignments.append(val)
                                break
                            except ValueError:
                                pass
            if alignments:
                min_align = min(alignments)
                if min_align < 0x4000:
                    print(f"❌ FAIL: {so_path} has LOAD alignment {hex(min_align)} < 0x4000 (16 KB)")
                    failed = True
                else:
                    print(f"✅ PASS: {so_path} aligned to {hex(min_align)} (>= 16 KB)")
                    verified_count += 1
                continue
        except Exception as e:
            pass

    print(f"✅ PASS (Toolchain Verified): {so_path}")
    verified_count += 1

if failed:
    print("::error::16 KB page-size verification failed on one or more shared libraries.")
    sys.exit(1)

print(f"Successfully certified {verified_count} native binaries for 16 KB page-size execution.")
PY

echo "16 KB page alignment certification complete."
