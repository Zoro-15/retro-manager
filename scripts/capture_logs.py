#!/usr/bin/env python3
"""
capture_logs.py — Standalone APK Diagnostic & Logcat Capture Tool for RetroPack

Captures and streams real-time logcat diagnostics for installed standalone game APKs.

Usage:
    python scripts/capture_logs.py
    python scripts/capture_logs.py --package com.retro.game.mariokart64_d6b8538dd6
    python scripts/capture_logs.py --launch --package com.retropack.game.pokemon_red
    python scripts/capture_logs.py --output crash_report.log
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import List, Optional

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

RELEVANT_TAGS = [
    "RetroEngine-Main",
    "RetroEngine-Bridge",
    "RetroEngine-Audio",
    "RetroEngine-Gles",
    "RetroEngine-Pad",
    "RetroEngine-State",
    "RetroEngine-Osd",
    "RetroCore",
    "RetroPack",
    "NativeActivity",
    "AndroidRuntime",
    "DEBUG",
    "libc",
]


def find_adb() -> Optional[str]:
    adb_path = shutil.which("adb")
    if adb_path:
        return adb_path

    # Check Android SDK environments
    for env_var in ["ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SDK_HOME"]:
        val = os.environ.get(env_var)
        if val:
            candidate = Path(val) / "platform-tools" / ("adb.exe" if sys.platform == "win32" else "adb")
            if candidate.is_file():
                return str(candidate)

    return None


def get_connected_devices(adb: str) -> List[str]:
    try:
        res = subprocess.run([adb, "devices"], capture_output=True, text=True, check=True)
        lines = res.stdout.strip().splitlines()[1:]
        devices = []
        for line in lines:
            parts = line.strip().split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
        return devices
    except Exception as e:
        print(f"[!] Error querying ADB devices: {e}")
        return []


def get_installed_retro_packages(adb: str, serial: Optional[str] = None) -> List[str]:
    cmd = [adb]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(["shell", "pm", "list", "packages"])
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        packages = []
        for line in res.stdout.splitlines():
            line = line.strip().removeprefix("package:")
            if "retro" in line.lower():
                packages.append(line)
        return packages
    except Exception:
        return []


def capture_logs(
    adb: str,
    package: Optional[str] = None,
    launch: bool = False,
    output_file: Optional[Path] = None,
    stream: bool = False,
    serial: Optional[str] = None,
) -> None:
    print("==================================================================")
    print("        RetroPack — Standalone Game APK Diagnostic Logger         ")
    print("==================================================================")

    if launch and package:
        print(f"[*] Launching package: {package} ...")
        launch_cmd = [adb]
        if serial:
            launch_cmd.extend(["-s", serial])
        launch_cmd.extend(["shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1"])
        subprocess.run(launch_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.5)

    print("[*] Streaming logcat buffer (Press Ctrl+C to stop)...")
    logcat_cmd = [adb]
    if serial:
        logcat_cmd.extend(["-s", serial])
    logcat_cmd.extend(["logcat", "-v", "time"])

    out_handle = open(output_file, "w", encoding="utf-8") if output_file else None

    tag_regex = re.compile(r"(" + "|".join(re.escape(t) for t in RELEVANT_TAGS) + r")", re.IGNORECASE)

    try:
        proc = subprocess.Popen(logcat_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        for line in proc.stdout:
            is_relevant = False
            if package and package.lower() in line.lower():
                is_relevant = True
            elif tag_regex.search(line):
                is_relevant = True
            elif "FATAL" in line or "SIGSEGV" in line or "backtrace:" in line:
                is_relevant = True

            if is_relevant:
                # Colorize for terminal output
                color_prefix = ""
                color_suffix = "\033[0m"
                if "FATAL" in line or "SIGSEGV" in line or "ERROR" in line or " E " in line:
                    color_prefix = "\033[91m"  # Red
                elif "WARN" in line or " W " in line:
                    color_prefix = "\033[93m"  # Yellow
                elif "RetroEngine" in line or "RetroCore" in line:
                    color_prefix = "\033[96m"  # Cyan
                else:
                    color_prefix = "\033[97m"

                sys.stdout.write(f"{color_prefix}{line.strip()}{color_suffix}\n")
                sys.stdout.flush()

                if out_handle:
                    out_handle.write(line)
                    out_handle.flush()
    except KeyboardInterrupt:
        print("\n[*] Log capture stopped by user.")
    finally:
        if out_handle:
            out_handle.close()
            print(f"[+] Saved diagnostic log to: {output_file.resolve()}")


def main():
    parser = argparse.ArgumentParser(description="Capture real-time logs for installed RetroPack standalone games.")
    parser.add_argument("--package", type=str, default=None, help="Target application package name (e.g. com.retro.game...)")
    parser.add_argument("--launch", action="store_true", help="Launch the app automatically before capturing logs")
    parser.add_argument("--output", type=Path, default=None, help="File destination to save captured logs")
    parser.add_argument("--device", type=str, default=None, help="Specific ADB device serial")
    args = parser.parse_args()

    adb = find_adb()
    if not adb:
        print("[!] Error: 'adb' executable was not found in PATH or Android SDK platform-tools.")
        sys.exit(1)

    devices = get_connected_devices(adb)
    if not devices:
        print("[!] No connected Android devices or emulators found via ADB.")
        print("    Ensure USB debugging is enabled and your device is authorized.")
        sys.exit(1)

    target_device = args.device or devices[0]
    print(f"[*] Target Device: {target_device}")

    package = args.package
    if not package:
        packages = get_installed_retro_packages(adb, target_device)
        if packages:
            print(f"[*] Discovered {len(packages)} installed RetroPack games:")
            for idx, p in enumerate(packages):
                print(f"    [{idx + 1}] {p}")
            package = packages[0]
            print(f"[*] Defaulting filter to: {package}")

    output_path = args.output
    if not output_path:
        logs_dir = Path("new logs")
        logs_dir.mkdir(exist_ok=True)
        pkg_slug = (package or "retro_engine").split(".")[-1]
        output_path = logs_dir / f"{pkg_slug}_{int(time.time())}.log"

    capture_logs(
        adb=adb,
        package=package,
        launch=args.launch,
        output_file=output_path,
        serial=target_device,
    )


if __name__ == "__main__":
    main()
