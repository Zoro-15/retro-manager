#!/usr/bin/env python3
"""
fetch_libretro_cores.py — Automated Libretro Pre-built Core Downloader for RetroPack

Reads runtimes/cores.json manifest and fetches official pre-built Libretro core shared
libraries (.so) for supported Android architectures (arm64-v8a, x86_64).
Extracts the .so from upstream zip archives and stages them into:
    runtimes/<core-id>/lib/<abi>/libretro_<core-id>.so

Features:
- Validates ELF magic header (\x7fELF) upon extraction
- Atomic file writing to prevent corrupted partial files
- Multi-threaded concurrent downloads with configurable retry logic
- SHA-256 integrity digest computation
- Dry-run, selective filtering (--cores, --abis), and clean staging options
- Compatible with Windows, Linux, and macOS Python 3.8+
"""

import argparse
import concurrent.futures
import hashlib
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile
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
DEFAULT_OUTPUT_DIR = ROOT_DIR / "runtimes"
DEFAULT_BASE_URL = "https://buildbot.libretro.com/nightly/android/latest"
DEFAULT_TIMEOUT = 30
DEFAULT_RETRIES = 3
DEFAULT_WORKERS = 4
USER_AGENT = "RetroPack-Core-Fetcher/2.0 (+https://github.com/retropack)"
ELF_MAGIC = b"\x7fELF"


def format_bytes(size_bytes: int) -> str:
    """Format bytes into a human-readable string (KB, MB)."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def compute_sha256(data: bytes) -> str:
    """Compute SHA-256 hexadecimal digest for raw bytes."""
    return hashlib.sha256(data).hexdigest()


def load_manifest(manifest_path: Path) -> Dict[str, Any]:
    """Load and validate the cores.json manifest."""
    if not manifest_path.is_file():
        raise FileNotFoundError(f"Manifest file not found: {manifest_path}")
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if "cores" not in data or not isinstance(data["cores"], list):
        raise ValueError("Invalid manifest: missing 'cores' array.")
    return data


class CoreFetchTask:
    """Represents a single core download and staging task for a specific ABI."""

    def __init__(
        self,
        core_id: str,
        display_name: str,
        abi: str,
        upstream_slug: str,
        lib_name: str,
        base_url: str,
        output_dir: Path,
        timeout: int = DEFAULT_TIMEOUT,
        retries: int = DEFAULT_RETRIES,
        force: bool = False,
    ):
        self.core_id = core_id
        self.display_name = display_name
        self.abi = abi
        self.upstream_slug = upstream_slug
        self.lib_name = lib_name
        self.base_url = base_url.rstrip("/")
        self.output_dir = output_dir
        self.timeout = timeout
        self.retries = retries
        self.force = force

        # Target destination: runtimes/<core-id>/lib/<abi>/<lib_name>
        self.dest_dir = self.output_dir / self.core_id / "lib" / self.abi
        self.dest_file = self.dest_dir / self.lib_name
        self.url = f"{self.base_url}/{self.abi}/{self.upstream_slug}"

    def execute(self) -> Dict[str, Any]:
        """Execute download, extraction, validation, and staging."""
        start_time = time.time()
        result: Dict[str, Any] = {
            "core_id": self.core_id,
            "display_name": self.display_name,
            "abi": self.abi,
            "url": self.url,
            "dest_file": self.dest_file,
            "status": "PENDING",
            "size": 0,
            "sha256": "",
            "error": None,
            "elapsed": 0.0,
        }

        # Check if already staged (unless --force is specified)
        if self.dest_file.is_file() and not self.force:
            try:
                with open(self.dest_file, "rb") as f:
                    content = f.read()
                if content.startswith(ELF_MAGIC):
                    result["status"] = "SKIPPED (EXISTS)"
                    result["size"] = len(content)
                    result["sha256"] = compute_sha256(content)
                    result["elapsed"] = time.time() - start_time
                    return result
            except Exception:
                pass  # Fall through to re-download if unreadable

        # Download with retry loop
        last_error = None
        for attempt in range(1, self.retries + 1):
            try:
                req = urllib.request.Request(
                    self.url,
                    headers={"User-Agent": USER_AGENT, "Accept": "application/zip, application/octet-stream"},
                )
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    if resp.status != 200:
                        raise ValueError(f"HTTP response code {resp.status}")
                    zip_data = resp.read()

                # Extract .so from zip archive
                so_data = self._extract_so(zip_data)

                # Validate ELF header
                if not so_data.startswith(ELF_MAGIC):
                    raise ValueError("Extracted binary does not begin with ELF magic header (0x7F 'ELF')")

                # Atomic disk write
                self.dest_dir.mkdir(parents=True, exist_ok=True)
                tmp_file = self.dest_dir / f".tmp_{self.lib_name}.{os.getpid()}"
                with open(tmp_file, "wb") as f:
                    f.write(so_data)
                    f.flush()
                    os.fsync(f.fileno())

                # Atomic replace
                if sys.platform == "win32" and self.dest_file.is_file():
                    self.dest_file.unlink()
                tmp_file.replace(self.dest_file)

                # Set executable permissions on non-Windows
                if os.name == "posix":
                    try:
                        self.dest_file.chmod(0o755)
                    except Exception:
                        pass

                result["status"] = "SUCCESS"
                result["size"] = len(so_data)
                result["sha256"] = compute_sha256(so_data)
                result["elapsed"] = time.time() - start_time
                return result

            except Exception as e:
                last_error = e
                if attempt < self.retries:
                    time.sleep(1.0 * attempt)

        result["status"] = "FAILED"
        result["error"] = str(last_error)
        result["elapsed"] = time.time() - start_time
        return result

    def _extract_so(self, zip_data: bytes) -> bytes:
        """Inspect and extract the shared library (.so) from zip archive bytes."""
        with zipfile.ZipFile(io.BytesIO(zip_data)) as zf:
            namelist = zf.namelist()
            if not namelist:
                raise ValueError("Downloaded archive is empty")

            # Look for exact .so match or any .so in root/subfolders
            so_entries = [name for name in namelist if name.endswith(".so") and not name.startswith("__MACOSX")]
            if not so_entries:
                # If only one file in archive, extract it
                if len(namelist) == 1:
                    return zf.read(namelist[0])
                raise ValueError(f"No .so binary found in archive. Contents: {namelist}")

            # Pick the primary .so entry
            chosen_entry = so_entries[0]
            return zf.read(chosen_entry)


def run_core_fetcher(
    manifest_path: Path,
    output_dir: Path,
    base_url: str,
    target_cores: Optional[List[str]] = None,
    target_abis: Optional[List[str]] = None,
    workers: int = DEFAULT_WORKERS,
    timeout: int = DEFAULT_TIMEOUT,
    retries: int = DEFAULT_RETRIES,
    clean: bool = False,
    force: bool = False,
    dry_run: bool = False,
    continue_on_error: bool = False,
    save_summary_json: Optional[Path] = None,
) -> int:
    """Main orchestration routine to fetch and stage cores."""
    start_total_time = time.time()

    print("=" * 88)
    print("                RETROPACK UNIVERSAL LIBRETRO CORE FETCHER")
    print("=" * 88)
    print(f"Manifest Path : {manifest_path}")
    print(f"Output Base   : {output_dir}")
    print(f"Base Upstream : {base_url}")
    print(f"Concurrency   : {workers} workers | Timeout: {timeout}s | Retries: {retries}")
    if dry_run:
        print("Mode          : *** DRY RUN ONLY (No files will be downloaded or modified) ***")
    print("=" * 88)

    manifest = load_manifest(manifest_path)
    supported_abis = manifest.get("supported_abis", ["arm64-v8a", "x86_64"])
    all_cores = manifest.get("cores", [])

    # Filter ABIs
    if target_abis:
        selected_abis = [abi for abi in target_abis if abi in supported_abis]
        if not selected_abis:
            print(f"[!] Error: None of specified ABIs {target_abis} match supported ABIs {supported_abis}")
            return 1
    else:
        selected_abis = supported_abis

    # Filter Cores
    if target_cores:
        selected_cores = [c for c in all_cores if c["id"].lower() in [tc.lower() for tc in target_cores]]
        if not selected_cores:
            print(f"[!] Error: None of specified cores {target_cores} found in manifest.")
            return 1
    else:
        selected_cores = all_cores

    print(f"Target ABIs   : {', '.join(selected_abis)}")
    print(f"Target Cores  : {len(selected_cores)} core(s) selected")
    for c in selected_cores:
        print(f"  - {c['id']:<20} ({c.get('display_name', c['id'])}) -> {c['lib_name']}")
    print("-" * 88)

    # Clean existing directories if requested
    if clean and not dry_run:
        print("[*] Cleaning existing target core directories...")
        for c in selected_cores:
            for abi in selected_abis:
                target_so = output_dir / c["id"] / "lib" / abi / c["lib_name"]
                if target_so.is_file():
                    target_so.unlink()
                    print(f"    Removed: {target_so.relative_to(ROOT_DIR)}")

    # Build task list
    tasks: List[CoreFetchTask] = []
    for c in selected_cores:
        for abi in selected_abis:
            task = CoreFetchTask(
                core_id=c["id"],
                display_name=c.get("display_name", c["id"]),
                abi=abi,
                upstream_slug=c["upstream_slug"],
                lib_name=c["lib_name"],
                base_url=base_url,
                output_dir=output_dir,
                timeout=timeout,
                retries=retries,
                force=force,
            )
            tasks.append(task)

    print(f"[*] Prepared {len(tasks)} core fetch tasks ({len(selected_cores)} cores x {len(selected_abis)} ABIs).")

    if dry_run:
        print("\n--- DRY RUN TASK LIST ---")
        for idx, task in enumerate(tasks, 1):
            rel_dest = task.dest_file.relative_to(ROOT_DIR) if task.dest_file.is_relative_to(ROOT_DIR) else task.dest_file
            print(f"[{idx:02d}/{len(tasks):02d}] {task.core_id:<18} [{task.abi:<9}]")
            print(f"       From: {task.url}")
            print(f"       To  : {rel_dest}")
        print("\n[OK] Dry run completed. No files fetched.")
        return 0

    print(f"\n[*] Starting downloads with {workers} worker threads...\n")
    results: List[Dict[str, Any]] = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        future_to_task = {executor.submit(task.execute): task for task in tasks}
        for future in concurrent.futures.as_completed(future_to_task):
            task = future_to_task[future]
            try:
                res = future.result()
            except Exception as exc:
                res = {
                    "core_id": task.core_id,
                    "display_name": task.display_name,
                    "abi": task.abi,
                    "url": task.url,
                    "dest_file": task.dest_file,
                    "status": "FAILED",
                    "size": 0,
                    "sha256": "",
                    "error": str(exc),
                    "elapsed": 0.0,
                }
            results.append(res)

            status_color = "[ OK ]" if res["status"] in ("SUCCESS", "SKIPPED (EXISTS)") else "[FAIL]"
            size_str = format_bytes(res["size"]) if res["size"] > 0 else "-"
            print(f"{status_color} {res['core_id']:<18} [{res['abi']:<9}] {res['status']:<18} {size_str:>9} ({res['elapsed']:.2f}s)")
            if res.get("error"):
                print(f"       >>> Error: {res['error']}")

    total_elapsed = time.time() - start_total_time
    total_success = sum(1 for r in results if r["status"] in ("SUCCESS", "SKIPPED (EXISTS)"))
    total_downloaded = sum(1 for r in results if r["status"] == "SUCCESS")
    total_skipped = sum(1 for r in results if "SKIPPED" in r["status"])
    total_failed = sum(1 for r in results if r["status"] == "FAILED")
    total_bytes = sum(r["size"] for r in results if r["status"] == "SUCCESS")

    # Display Final Report Table
    print("\n" + "=" * 88)
    print("                    LIBRETRO CORE FETCHING SUMMARY REPORT")
    print("=" * 88)
    print(f"{'Core ID':<18} {'ABI':<10} {'Status':<16} {'Size':<10} {'SHA-256 Digest (Prefix)':<24} {'Time':<8}")
    print("-" * 88)

    # Sort results for reproducible output
    results.sort(key=lambda r: (r["core_id"], r["abi"]))
    for r in results:
        sha_prefix = r["sha256"][:16] + "..." if r["sha256"] else "-"
        size_str = format_bytes(r["size"]) if r["size"] > 0 else "-"
        print(f"{r['core_id']:<18} {r['abi']:<10} {r['status']:<16} {size_str:<10} {sha_prefix:<24} {r['elapsed']:>5.2f}s")

    print("-" * 88)
    print(f"Total Tasks   : {len(results)}")
    print(f"Successful    : {total_success} ({total_downloaded} downloaded, {total_skipped} cached)")
    print(f"Failed        : {total_failed}")
    print(f"Transferred   : {format_bytes(total_bytes)}")
    print(f"Total Duration: {total_elapsed:.2f}s ({int(total_elapsed // 60)}m {int(total_elapsed % 60)}s)")
    print("=" * 88)

    # Save summary artifact JSON if requested
    if save_summary_json:
        summary_payload = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "total_tasks": len(results),
            "successful": total_success,
            "failed": total_failed,
            "total_bytes": total_bytes,
            "results": [
                {
                    "core_id": r["core_id"],
                    "display_name": r["display_name"],
                    "abi": r["abi"],
                    "status": r["status"],
                    "size_bytes": r["size"],
                    "sha256": r["sha256"],
                    "rel_path": str(r["dest_file"].relative_to(ROOT_DIR)) if r["dest_file"].is_relative_to(ROOT_DIR) else str(r["dest_file"]),
                    "error": r["error"],
                }
                for r in results
            ],
        }
        with open(save_summary_json, "w", encoding="utf-8") as f:
            json.dump(summary_payload, f, indent=2)
        print(f"[OK] Saved staging metadata summary to: {save_summary_json}")

    if total_failed > 0 and not continue_on_error:
        print(f"\n[!] Core fetching finished with {total_failed} failure(s).")
        return 1

    print("\n[OK] All target Libretro cores staged successfully.")
    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Download and stage official pre-built Libretro cores into runtimes/<core-id>/lib/<abi>/",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--manifest",
        "--cores-json",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="Path to cores.json manifest file",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Base directory where cores will be staged (e.g., runtimes/)",
    )
    parser.add_argument(
        "--base-url",
        type=str,
        default=DEFAULT_BASE_URL,
        help="Base URL for Libretro nightly builds",
    )
    parser.add_argument(
        "-c",
        "--cores",
        nargs="+",
        help="Filter specific core IDs to fetch (e.g. --cores mgba snes9x)",
    )
    parser.add_argument(
        "-a",
        "--abis",
        nargs="+",
        help="Filter specific ABIs to fetch (e.g. --abis arm64-v8a x86_64)",
    )
    parser.add_argument(
        "-w",
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        help="Number of concurrent download worker threads",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
        help="HTTP network timeout in seconds",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=DEFAULT_RETRIES,
        help="Number of retry attempts per core download",
    )
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Remove existing staged .so files for targeted cores before fetching",
    )
    parser.add_argument(
        "-f",
        "--force",
        action="store_true",
        help="Force overwrite existing staged files even if valid",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulate execution without downloading or staging files",
    )
    parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Do not return error exit code on partial failures",
    )
    parser.add_argument(
        "--save-summary",
        type=Path,
        default=None,
        help="Optional path to output staging metadata JSON summary",
    )

    args = parser.parse_args()

    exit_code = run_core_fetcher(
        manifest_path=args.manifest,
        output_dir=args.output_dir,
        base_url=args.base_url,
        target_cores=args.cores,
        target_abis=args.abis,
        workers=args.workers,
        timeout=args.timeout,
        retries=args.retries,
        clean=args.clean,
        force=args.force,
        dry_run=args.dry_run,
        continue_on_error=args.continue_on_error,
        save_summary_json=args.save_summary,
    )
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
