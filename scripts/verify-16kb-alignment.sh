#!/usr/bin/env bash
#
# verify-16kb-alignment.sh — Asserts that all compiled ELF shared libraries (.so)
# are aligned to 16 KB (0x4000) boundaries, satisfying Android 15/16 (API 35/36)
# memory page size requirements.
#
set -euo pipefail

echo "=== Android 16 KB Page-Size ELF Alignment Verification ==="

python3 - <<'PY'
import os, sys, glob, struct, subprocess

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

def get_elf64_load_alignment(so_path):
    try:
        with open(so_path, "rb") as f:
            header = f.read(64)
            if len(header) < 64 or header[:4] != b"\x7fELF":
                return None
            ei_class = header[4]
            if ei_class != 2: # 64-bit only (arm64-v8a / x86_64)
                return 0x1000 # 32-bit arches are exempt from 16KB requirement
            ei_data = header[5]
            endian = "<" if ei_data == 1 else ">"

            e_phoff = struct.unpack(f"{endian}Q", header[32:40])[0]
            e_phentsize = struct.unpack(f"{endian}H", header[54:56])[0]
            e_phnum = struct.unpack(f"{endian}H", header[56:58])[0]

            if e_phentsize < 56 or e_phnum == 0:
                return None

            f.seek(e_phoff)
            alignments = []
            for _ in range(e_phnum):
                ph_data = f.read(e_phentsize)
                if len(ph_data) < 56:
                    break
                p_type, _, _, _, _, _, _, p_align = struct.unpack(f"{endian}IIQQQQQQ", ph_data[:56])
                if p_type == 1: # PT_LOAD
                    alignments.append(p_align)

            return min(alignments) if alignments else None
    except Exception as ex:
        print(f"Warning reading {so_path}: {ex}")
        return None

failed = False
verified_count = 0

for so_path in so_files:
    # Filter only 64-bit ABIs: arm64-v8a, x86_64
    if "arm64-v8a" not in so_path and "x86_64" not in so_path and "arm64" not in so_path:
        continue

    # Skip intermediate CMake test files if any
    if "CMakeFiles" in so_path:
        continue

    align = get_elf64_load_alignment(so_path)
    if align is None:
        continue

    if align < 0x4000:
        print(f"❌ FAIL: {so_path} has LOAD alignment {hex(align)} < 0x4000 (16 KB)")
        failed = True
    else:
        print(f"✅ PASS: {so_path} aligned to {hex(align)} (>= 16 KB)")
        verified_count += 1

if failed:
    print("::error::16 KB page-size verification failed on one or more shared libraries.")
    sys.exit(1)

print(f"Successfully certified {verified_count} native binaries for 16 KB page-size execution.")
PY

echo "16 KB page alignment certification complete."

