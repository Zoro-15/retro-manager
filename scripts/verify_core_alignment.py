#!/usr/bin/env python3
"""
verify_core_alignment.py — 16 KB ELF Page-Size Alignment Validator for Android 15

Inspects compiled and staged shared libraries (.so) to verify strict compliance with
Android 15 (API 35+) 16 KB memory page size requirements:
1. Every PT_LOAD segment in 64-bit ELF binaries (arm64-v8a, x86_64) must specify
   p_align >= 0x4000 (16,384 bytes).
2. Congruence condition: (p_vaddr % p_align) == (p_offset % p_align) and
   (p_vaddr % 0x4000) == (p_offset % 0x4000).

Features:
- Pure Python 3 implementation with zero external binary dependencies (no readelf needed).
- Supports ELF32 and ELF64 binaries in Little and Big Endian.
- Cross-references with runtimes/cores.json manifest.
- Configurable search paths and strict missing core validation.
- JSON diagnostic reporting for CI artifact archiving.
"""

import argparse
import glob
import json
import os
import struct
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Default paths relative to workspace root
SCRIPT_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPT_DIR.parent
DEFAULT_MANIFEST = ROOT_DIR / "runtimes" / "cores.json"
DEFAULT_SEARCH_PATHS = [
    ROOT_DIR / "runtimes",
    ROOT_DIR / "runtime",
    ROOT_DIR / "template-apk" / "build",
    ROOT_DIR / "app" / "build",
]

PAGE_SIZE_16KB = 0x4000  # 16,384 bytes
ELF_MAGIC = b"\x7fELF"

# ELF Machine architecture constants
EM_ARM = 0x28
EM_AARCH64 = 0xB7
EM_386 = 0x03
EM_X86_64 = 0x3E
EM_RISCV = 0xF3

MACHINE_NAMES = {
    EM_ARM: "ARM (32-bit)",
    EM_AARCH64: "AArch64 (64-bit)",
    EM_386: "x86 (32-bit)",
    EM_X86_64: "x86_64 (64-bit)",
    EM_RISCV: "RISC-V",
}


def parse_elf_segments(so_path: Path) -> Dict[str, Any]:
    """
    Parse ELF header and program header table of a shared library.
    Returns parsed metadata and PT_LOAD segment details.
    """
    with open(so_path, "rb") as f:
        header = f.read(64)
        if len(header) < 52 or header[:4] != ELF_MAGIC:
            raise ValueError("Not a valid ELF binary")

        ei_class = header[4]  # 1 = 32-bit, 2 = 64-bit
        ei_data = header[5]   # 1 = Little Endian, 2 = Big Endian
        endian = "<" if ei_data == 1 else ">"

        is_64bit = (ei_class == 2)
        e_machine = struct.unpack(f"{endian}H", header[18:20])[0]

        if is_64bit:
            if len(header) < 64:
                raise ValueError("Truncated ELF64 header")
            e_phoff = struct.unpack(f"{endian}Q", header[32:40])[0]
            e_phentsize = struct.unpack(f"{endian}H", header[54:56])[0]
            e_phnum = struct.unpack(f"{endian}H", header[56:58])[0]
            min_entry_size = 56
        else:
            e_phoff = struct.unpack(f"{endian}I", header[28:32])[0]
            e_phentsize = struct.unpack(f"{endian}H", header[42:44])[0]
            e_phnum = struct.unpack(f"{endian}H", header[44:46])[0]
            min_entry_size = 32

        if e_phentsize < min_entry_size or e_phnum == 0:
            raise ValueError(f"Invalid program header table: size={e_phentsize}, count={e_phnum}")

        f.seek(e_phoff)
        pt_loads: List[Dict[str, Any]] = []
        all_segments: List[Dict[str, Any]] = []

        for idx in range(e_phnum):
            ph_raw = f.read(e_phentsize)
            if len(ph_raw) < min_entry_size:
                break

            if is_64bit:
                # Elf64_Phdr: p_type (4), p_flags (4), p_offset (8), p_vaddr (8), p_paddr (8), p_filesz (8), p_memsz (8), p_align (8)
                p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack(
                    f"{endian}IIQQQQQQ", ph_raw[:56]
                )
            else:
                # Elf32_Phdr: p_type (4), p_offset (4), p_vaddr (4), p_paddr (4), p_filesz (4), p_memsz (4), p_flags (4), p_align (4)
                p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack(
                    f"{endian}IIIIIIII", ph_raw[:32]
                )

            seg_info = {
                "index": idx,
                "type": p_type,
                "flags": p_flags,
                "offset": p_offset,
                "vaddr": p_vaddr,
                "paddr": p_paddr,
                "filesz": p_filesz,
                "memsz": p_memsz,
                "align": p_align,
            }
            all_segments.append(seg_info)

            if p_type == 1:  # PT_LOAD
                pt_loads.append(seg_info)

        file_size = so_path.stat().st_size

        return {
            "path": so_path,
            "is_64bit": is_64bit,
            "endian": "Little" if ei_data == 1 else "Big",
            "machine": e_machine,
            "machine_name": MACHINE_NAMES.get(e_machine, f"Unknown (0x{e_machine:X})"),
            "file_size": file_size,
            "ph_count": e_phnum,
            "pt_loads": pt_loads,
            "all_segments": all_segments,
        }


def verify_binary_alignment(elf_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Verify 16 KB page alignment invariants for an ELF binary.
    """
    is_64bit = elf_data["is_64bit"]
    machine = elf_data["machine"]
    pt_loads = elf_data["pt_loads"]
    path = elf_data["path"]

    # 32-bit architectures (ARM32, x86-32) are exempt from 16 KB requirement
    is_64bit_target = is_64bit or machine in (EM_AARCH64, EM_X86_64)

    if not pt_loads:
        return {
            "passed": False,
            "exempt": False,
            "min_align": 0,
            "errors": ["No PT_LOAD segments found in binary."],
            "details": [],
        }

    errors: List[str] = []
    details: List[Dict[str, Any]] = []
    min_align = min(seg["align"] for seg in pt_loads)
    all_modulo_match = True

    for seg in pt_loads:
        p_align = seg["align"]
        p_vaddr = seg["vaddr"]
        p_offset = seg["offset"]

        modulo_align = (p_vaddr % p_align) == (p_offset % p_align) if p_align > 0 else False
        modulo_16k = (p_vaddr % PAGE_SIZE_16KB) == (p_offset % PAGE_SIZE_16KB)

        seg_detail = {
            "index": seg["index"],
            "vaddr": hex(p_vaddr),
            "offset": hex(p_offset),
            "align": hex(p_align),
            "align_bytes": p_align,
            "modulo_congruent": modulo_align,
            "modulo_16k_congruent": modulo_16k,
        }
        details.append(seg_detail)

        if not modulo_align:
            all_modulo_match = False
            errors.append(f"Segment {seg['index']}: (vaddr 0x{p_vaddr:X} % align 0x{p_align:X}) != (offset 0x{p_offset:X} % align 0x{p_align:X})")

        if is_64bit_target:
            if p_align < PAGE_SIZE_16KB:
                errors.append(f"Segment {seg['index']}: p_align 0x{p_align:X} < 0x4000 (16 KB requirement).")
            if not modulo_16k:
                errors.append(f"Segment {seg['index']}: (vaddr 0x{p_vaddr:X} % 0x4000) != (offset 0x{p_offset:X} % 0x4000)")

    if not is_64bit_target:
        # 32-bit binaries
        passed = (len(errors) == 0)
        return {
            "passed": passed,
            "exempt": True,
            "min_align": min_align,
            "errors": errors,
            "details": details,
        }

    passed = (len(errors) == 0 and min_align >= PAGE_SIZE_16KB and all_modulo_match)
    return {
        "passed": passed,
        "exempt": False,
        "min_align": min_align,
        "errors": errors,
        "details": details,
    }


def find_so_files(search_paths: List[Path]) -> List[Path]:
    """Find all .so files under search paths, filtering out build intermediates."""
    so_files: List[Path] = []
    for path in search_paths:
        if path.is_file() and path.name.endswith(".so"):
            so_files.append(path.resolve())
        elif path.is_dir():
            for root, _, files in os.walk(path):
                # Skip CMake temporary and intermediate directories
                if "CMakeFiles" in root or ".transforms" in root:
                    continue
                for f in files:
                    if f.endswith(".so"):
                        so_files.append((Path(root) / f).resolve())

    return sorted(set(so_files))


def run_alignment_verification(
    search_paths: Optional[List[Path]] = None,
    manifest_path: Optional[Path] = DEFAULT_MANIFEST,
    target_abis: Optional[List[str]] = None,
    strict_manifest: bool = False,
    verbose: bool = False,
    json_report_path: Optional[Path] = None,
) -> int:
    """Run full verification across discovered shared libraries."""
    start_time = time.time()

    if not search_paths:
        search_paths = DEFAULT_SEARCH_PATHS

    print("=" * 88)
    print("           ANDROID 15 (16 KB PAGE SIZE) ELF ALIGNMENT VALIDATOR")
    print("=" * 88)
    print(f"Standard Requirement : PT_LOAD p_align >= 0x4000 (16 KB) for 64-bit ABIs")
    print(f"Congruence Rule      : (p_vaddr % 0x4000) == (p_offset % 0x4000)")
    print(f"Search Paths         : {len(search_paths)} location(s)")
    for sp in search_paths:
        if sp.exists():
            print(f"  - {sp}")
    print("=" * 88)

    # Manifest Check
    manifest_cores = []
    manifest_abis = ["arm64-v8a", "x86_64"]
    if manifest_path and manifest_path.is_file():
        try:
            with open(manifest_path, "r", encoding="utf-8") as mf:
                mdata = json.load(mf)
                manifest_cores = mdata.get("cores", [])
                manifest_abis = mdata.get("supported_abis", manifest_abis)
            if target_abis:
                manifest_abis = [abi for abi in target_abis if abi in manifest_abis]
            print(f"[*] Loaded catalog manifest: {manifest_path.name} ({len(manifest_cores)} cores declared, ABIs: {', '.join(manifest_abis)})")
        except Exception as ex:
            print(f"[!] Warning reading manifest {manifest_path}: {ex}")

    so_files = find_so_files(search_paths)
    print(f"[*] Discovered {len(so_files)} shared library (.so) binary file(s) across search paths.\n")

    if not so_files and not strict_manifest:
        print("[!] No .so binaries found to inspect. Staging or build required first.")
        return 0

    results: List[Dict[str, Any]] = []
    for so_file in so_files:
        rel_path = so_file.relative_to(ROOT_DIR) if so_file.is_relative_to(ROOT_DIR) else so_file
        try:
            elf_data = parse_elf_segments(so_file)
            verification = verify_binary_alignment(elf_data)
            results.append({
                "path": so_file,
                "rel_path": str(rel_path),
                "elf": elf_data,
                "verification": verification,
                "error": None,
            })
        except Exception as ex:
            results.append({
                "path": so_file,
                "rel_path": str(rel_path),
                "elf": None,
                "verification": {"passed": False, "exempt": False, "min_align": 0, "errors": [str(ex)], "details": []},
                "error": str(ex),
            })

    # Strict Manifest Validation: check if any core declared in cores.json is missing
    missing_manifest_entries: List[str] = []
    if manifest_cores:
        staged_rel_paths = {r["rel_path"].replace("\\", "/") for r in results}
        for c in manifest_cores:
            for abi in manifest_abis:
                expected_rel = f"runtimes/{c['id']}/lib/{abi}/{c['lib_name']}"
                if expected_rel not in staged_rel_paths:
                    missing_manifest_entries.append(expected_rel)

    # Print Summary Table
    print("-" * 88)
    print(f"{'Target Shared Library (.so)':<46} {'Arch':<12} {'PT_LOAD':<8} {'Min Align':<10} {'Status':<10}")
    print("-" * 88)

    for r in results:
        rel_name = r["rel_path"]
        if len(rel_name) > 45:
            rel_name = "..." + rel_name[-42:]

        elf = r["elf"]
        ver = r["verification"]

        if elf:
            arch_str = elf["machine_name"].split()[0]
            pt_count = len(elf["pt_loads"])
            min_align_str = hex(ver["min_align"])
        else:
            arch_str = "Unknown"
            pt_count = 0
            min_align_str = "-"

        if ver["exempt"]:
            status_str = "[EXEMPT]"
        elif ver["passed"]:
            status_str = "[ PASS ]"
        else:
            status_str = "[!FAIL!]"

        print(f"{rel_name:<46} {arch_str:<12} {pt_count:<8} {min_align_str:<10} {status_str:<10}")

        if verbose or not ver["passed"]:
            for err in ver.get("errors", []):
                print(f"       >>> [ERROR] {err}")
            if verbose and elf:
                for seg in ver.get("details", []):
                    print(f"           - Segment {seg['index']}: vaddr={seg['vaddr']} offset={seg['offset']} align={seg['align']} (16k-congruent={seg['modulo_16k_congruent']})")

    print("-" * 88)

    total_scanned = len(results)
    total_passed = sum(1 for r in results if r["verification"]["passed"])
    total_exempt = sum(1 for r in results if r["verification"].get("exempt", False))
    total_failed = sum(1 for r in results if not r["verification"]["passed"] and not r["verification"].get("exempt", False))

    print(f"Total Binaries Scanned : {total_scanned}")
    print(f"Certified Compliant    : {total_passed} (16 KB page-aligned)")
    if total_exempt > 0:
        print(f"Exempt (32-bit)        : {total_exempt}")
    print(f"Non-Compliant / Failed : {total_failed}")

    if missing_manifest_entries:
        print(f"\n[!] Missing Manifest Cores ({len(missing_manifest_entries)} expected):")
        for m in missing_manifest_entries[:10]:
            print(f"    - {m}")
        if len(missing_manifest_entries) > 10:
            print(f"    ... and {len(missing_manifest_entries) - 10} more.")

    # Save JSON report if requested
    if json_report_path:
        report_data = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "total_scanned": total_scanned,
            "total_passed": total_passed,
            "total_failed": total_failed,
            "total_exempt": total_exempt,
            "missing_manifest_count": len(missing_manifest_entries),
            "missing_manifest_cores": missing_manifest_entries,
            "binaries": [
                {
                    "path": r["rel_path"],
                    "passed": r["verification"]["passed"],
                    "exempt": r["verification"].get("exempt", False),
                    "min_align": r["verification"]["min_align"],
                    "errors": r["verification"].get("errors", []),
                    "pt_loads": r["verification"].get("details", []),
                }
                for r in results
            ],
        }
        with open(json_report_path, "w", encoding="utf-8") as jf:
            json.dump(report_data, jf, indent=2)
        print(f"\n[OK] Diagnostic report written to: {json_report_path}")

    elapsed = time.time() - start_time
    print(f"Execution Duration     : {elapsed:.2f}s")
    print("=" * 88)

    if total_failed > 0:
        print("\n[!] Alignment validation failed for one or more 64-bit ELF binaries.")
        return 1

    if strict_manifest and missing_manifest_entries:
        print(f"\n[!] Strict check failed: {len(missing_manifest_entries)} declared manifest core(s) missing.")
        return 1

    print("\n[OK] 16 KB ELF page-size alignment certification passed successfully.")
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Verify Android 15 (16 KB page-size) ELF PT_LOAD alignment for native .so libraries.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="Optional specific files or directories to inspect (default: runtimes, runtime, build outputs)",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="Path to cores.json manifest to cross-reference",
    )
    parser.add_argument(
        "-a",
        "--abis",
        nargs="+",
        help="Filter specific ABIs to expect when verifying manifest compliance (e.g. --abis arm64-v8a)",
    )
    parser.add_argument(
        "-s",
        "--strict",
        action="store_true",
        help="Strict mode: fail if any core declared in manifest is missing",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Display detailed segment mapping and virtual address offsets",
    )
    parser.add_argument(
        "--json-report",
        type=Path,
        default=None,
        help="Optional path to write JSON validation report",
    )

    args = parser.parse_args()

    search_paths = args.paths if args.paths else None
    exit_code = run_alignment_verification(
        search_paths=search_paths,
        manifest_path=args.manifest,
        target_abis=args.abis,
        strict_manifest=args.strict,
        verbose=args.verbose,
        json_report_path=args.json_report,
    )
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
