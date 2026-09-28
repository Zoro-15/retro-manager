#!/usr/bin/env python3
"""
ci-build-matrix.py — Retropack Multi-Core Resilient CI Build & Diagnostic Matrix

Executes build targets individually across all emulator cores and APK components,
preventing an early failure in one core from blocking inspection of other cores.
Captures all logs, extracts de-duplicated error/warning snippets with a +/- 5 line
context window, and outputs a formatted 'build-diagnostics.txt' summary artifact.
"""

import os
import sys
import subprocess
import time
import re
from pathlib import Path

# Working directory
ROOT_DIR = Path(__file__).resolve().parent.parent
LOGS_DIR = ROOT_DIR / "build-logs"
DIAGNOSTICS_FILE = ROOT_DIR / "build-diagnostics.txt"

# Targeted build pipeline
TARGETS = [
    {
        "id": "runtime-common",
        "name": "Universal Libretro Host Native Runtime",
        "command": "./gradlew :runtime:retropack-runtime-common:assembleDebug --stacktrace",
        "required": True,
    },
    {
        "id": "core-module",
        "name": "Packaging Engine & Domain Core",
        "command": "./gradlew :core:test --stacktrace",
        "required": True,
    },
    {
        "id": "template-apk",
        "name": "Template APK (Standalone Universal Game Runner)",
        "command": "./gradlew :template-apk:assembleDebug :template-apk:test --stacktrace",
        "required": True,
    },
    {
        "id": "verify-16kb",
        "name": "16 KB ELF Page-Size Alignment Gate",
        "command": "bash scripts/verify-16kb-alignment.sh",
        "required": True,
    },
    {
        "id": "verify-cores-catalog",
        "name": "Core Catalog & Staged Binaries Verification",
        "command": "python3 scripts/verify_core_alignment.py",
        "required": False,
    },
    {
        "id": "rotate-anchors",
        "name": "Trust Anchor Rotation & Staging",
        "command": "cp -f template-apk/build/outputs/apk/debug/*.apk runtimes/mgba-unified/template.apk 2>/dev/null || true; bash scripts/rotate-trust-anchors.sh",
        "required": False,
    },
    {
        "id": "app-manager",
        "name": "Manager APK (Main App)",
        "command": "./gradlew :app:assembleDebug --stacktrace",
        "required": True,
    },
    {
        "id": "unit-tests",
        "name": "Unit Tests (App & Runtime Host)",
        "command": "./gradlew :app:testDebugUnitTest :runtime:retropack-runtime-common:test --stacktrace",
        "required": False,
    },
]

ERROR_PATTERNS = [
    re.compile(r':\s*fatal error:', re.IGNORECASE),
    re.compile(r':\s*error:', re.IGNORECASE),
    re.compile(r'clang(\+\+)?: error:', re.IGNORECASE),
    re.compile(r'ld\.lld:\s*error:', re.IGNORECASE),
    re.compile(r'undefined symbol:', re.IGNORECASE),
    re.compile(r'undefined reference to', re.IGNORECASE),
    re.compile(r'use of undeclared identifier', re.IGNORECASE),
    re.compile(r'call to undeclared function', re.IGNORECASE),
    re.compile(r'FAILED:', re.IGNORECASE),
    re.compile(r'CMake Error at', re.IGNORECASE),
    re.compile(r'ninja: build stopped: subcommand failed', re.IGNORECASE),
    re.compile(r'FAILURE: Build failed with an exception', re.IGNORECASE),
    re.compile(r'Execution failed for task', re.IGNORECASE),
    re.compile(r'Exception in thread', re.IGNORECASE),
]

def is_compiler_invocation(line):
    """Detect raw compiler invocation command lines to avoid false error matches on flags like -Werror=..."""
    l = line.strip()
    return (l.startswith(('clang ', 'clang++ ', '/usr/', 'C/C++: /usr/', 'C/C++: /home/', '/home/')) and (' -o ' in l or ' -c ' in l))

def extract_merged_snippets(log_lines, context=4, max_total_lines=150):
    """
    Finds genuine error lines and extracts +/- context lines.
    Prioritizes actual errors and caps output to keep diagnostic logs clean, compact, and fast.
    """
    if not log_lines:
        return []

    error_indices = []
    for i, line in enumerate(log_lines):
        if is_compiler_invocation(line):
            continue
        if any(pat.search(line) for pat in ERROR_PATTERNS):
            error_indices.append(i)

    if not error_indices:
        # Fallback: extract last 35 lines
        start = max(0, len(log_lines) - 35)
        return [{"range": (start + 1, len(log_lines)), "lines": [f"    L{ln+1:04d}: {log_lines[ln].rstrip()}" for ln in range(start, len(log_lines))]}]

    raw_ranges = []
    for idx in error_indices:
        r_start = max(0, idx - context)
        r_end = min(len(log_lines), idx + context + 1)
        raw_ranges.append((r_start, r_end))

    # Merge overlapping or contiguous intervals
    merged = []
    for start, end in raw_ranges:
        if not merged:
            merged.append([start, end])
        else:
            prev_start, prev_end = merged[-1]
            if start <= prev_end:
                merged[-1][1] = max(prev_end, end)
            else:
                merged.append([start, end])

    error_set = set(error_indices)
    snippets = []
    lines_emitted = 0

    for start, end in merged:
        if lines_emitted >= max_total_lines:
            snippets.append({
                "range": (start + 1, end),
                "lines": [f"    [... remaining failure trace truncated; total snippets capped at {max_total_lines} lines ...]"]
            })
            break

        snippet_lines = []
        for line_num in range(start, end):
            prefix = ">>> [ERROR] " if line_num in error_set else "    "
            line_content = log_lines[line_num].rstrip()
            if is_compiler_invocation(line_content):
                line_content = line_content[:140] + " ... [command-line flags truncated]"
            snippet_lines.append(f"{prefix}L{line_num + 1:04d}: {line_content}")
            lines_emitted += 1
            if lines_emitted >= max_total_lines:
                snippet_lines.append(f"    [... trace capped at {max_total_lines} lines; check raw log for full output ...]")
                break

        snippets.append({
            "range": (start + 1, end),
            "lines": snippet_lines
        })

    return snippets

def run_build_matrix():
    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    results = []
    total_start_time = time.time()

    print("=" * 88)
    print("           RETROPACK RESILIENT MULTI-CORE CI BUILD & DIAGNOSTICS MATRIX")
    print("=" * 88)
    print(f"Working Directory: {ROOT_DIR}")
    print(f"Logs Output Dir  : {LOGS_DIR}")
    print(f"Total Targets    : {len(TARGETS)}")
    print("=" * 88)
    sys.stdout.flush()

    for idx, target in enumerate(TARGETS, 1):
        target_id = target["id"]
        target_name = target["name"]
        cmd = target["command"]
        log_file_path = LOGS_DIR / f"{target_id}.log"

        print(f"\n[{idx:02d}/{len(TARGETS):02d}] Building: {target_name} ...", flush=True)
        print(f"       Command : {cmd}", flush=True)

        start_time = time.time()
        try:
            # Run command and capture combined stdout & stderr
            proc = subprocess.run(
                cmd,
                shell=True,
                cwd=str(ROOT_DIR),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace"
            )
            elapsed = time.time() - start_time
            exit_code = proc.returncode
            output = proc.stdout or ""
        except Exception as e:
            elapsed = time.time() - start_time
            exit_code = -1
            output = f"Exception running target {target_id}: {str(e)}\n"

        # Save complete raw output log
        with open(log_file_path, "w", encoding="utf-8") as f:
            f.write(output)

        passed = (exit_code == 0)
        status_str = "PASS" if passed else "FAIL"
        status_icon = "[ PASS ]" if passed else "[!FAIL!]"
        print(f"       Status  : {status_icon} in {elapsed:.2f}s (Exit code: {exit_code})", flush=True)

        log_lines = output.splitlines()
        snippets = []
        if not passed:
            snippets = extract_merged_snippets(log_lines, context=5)
            # Print immediate short excerpt to stdout
            print(f"       --- Diagnostic Snippets ({len(snippets)} merged block(s)) ---", flush=True)
            for snip in snippets[:3]: # show first 3 blocks in stdout
                r_start, r_end = snip["range"]
                print(f"       [Lines {r_start}-{r_end}]:", flush=True)
                for sl in snip["lines"][:12]:
                    print(f"         {sl}", flush=True)
                if len(snip["lines"]) > 12:
                    print(f"         ... ({len(snip['lines']) - 12} more lines in full log)", flush=True)

        results.append({
            "index": idx,
            "target": target,
            "passed": passed,
            "exit_code": exit_code,
            "elapsed": elapsed,
            "log_file": log_file_path,
            "snippets": snippets,
            "line_count": len(log_lines),
        })

    total_elapsed = time.time() - total_start_time
    total_passed = sum(1 for r in results if r["passed"])
    total_failed = len(results) - total_passed

    # -------------------------------------------------------------------------
    # Generate build-diagnostics.txt Report
    # -------------------------------------------------------------------------
    diag_lines = []
    diag_lines.append("=" * 88)
    diag_lines.append("                  RETROPACK CI MULTI-CORE BUILD & DIAGNOSTICS REPORT")
    diag_lines.append("=" * 88)
    diag_lines.append(f"Generated At    : {time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())}")
    diag_lines.append(f"Total Execution : {total_elapsed:.2f}s ({int(total_elapsed // 60)}m {int(total_elapsed % 60)}s)")
    diag_lines.append(f"Overall Result  : {total_passed}/{len(results)} Targets Passed ({total_failed} Failed)")
    diag_lines.append("=" * 88)
    diag_lines.append("")
    diag_lines.append("TARGET EXECUTION SUMMARY MATRIX:")
    diag_lines.append("-" * 88)
    diag_lines.append(f"{'Idx':<4} {'Target Component':<38} {'Status':<10} {'Duration':<10} {'Log File':<20}")
    diag_lines.append("-" * 88)

    for r in results:
        t = r["target"]
        st = "PASS" if r["passed"] else "FAIL [!]"
        diag_lines.append(f"[{r['index']:02d}] {t['name']:<38} {st:<10} {r['elapsed']:>6.2f}s     {r['log_file'].name}")

    diag_lines.append("-" * 88)
    diag_lines.append("")

    # Detailed Failing Sections
    failing_results = [r for r in results if not r["passed"]]
    if failing_results:
        diag_lines.append("=" * 88)
        diag_lines.append("                      DETAILED FAILURE LOG DIAGNOSTICS")
        diag_lines.append("=" * 88)
        diag_lines.append("Note: Error and warning lines are highlighted with '>>> [ERROR]' or '! [WARN]'.")
        diag_lines.append("      Overlapping +/- 5 context lines are de-duplicated and merged continuously.")
        diag_lines.append("=" * 88)

        for r in failing_results:
            t = r["target"]
            diag_lines.append("")
            diag_lines.append(f"################################################################################")
            diag_lines.append(f"### Target [{r['index']:02d}]: {t['name']} (ID: {t['id']})")
            diag_lines.append(f"### Command : {t['command']}")
            diag_lines.append(f"### ExitCode: {r['exit_code']} | Log File: build-logs/{r['log_file'].name}")
            diag_lines.append(f"################################################################################")

            if not r["snippets"]:
                diag_lines.append("    [No specific compiler error patterns matched; showing tail of log:]")
                try:
                    with open(r["log_file"], "r", encoding="utf-8") as f:
                        lines = f.readlines()
                    for l in lines[-40:]:
                        diag_lines.append(f"    {l.rstrip()}")
                except Exception as ex:
                    diag_lines.append(f"    Error reading log file: {ex}")
            else:
                for s_idx, snip in enumerate(r["snippets"], 1):
                    r_start, r_end = snip["range"]
                    diag_lines.append(f"\n--- [Snippet #{s_idx} | Lines {r_start} to {r_end}] ---")
                    for sl in snip["lines"]:
                        diag_lines.append(sl)

            diag_lines.append("")

    else:
        diag_lines.append("=" * 88)
        diag_lines.append("                       ALL BUILD TARGETS PASSED SUCCESSFULLY! ✅")
        diag_lines.append("=" * 88)

    # Write out build-diagnostics.txt
    with open(DIAGNOSTICS_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(diag_lines) + "\n")

    print("\n" + "=" * 88)
    print(f"Summary report written to: {DIAGNOSTICS_FILE}")
    print(f"Total: {total_passed} Passed, {total_failed} Failed.")
    print("=" * 88)

    # Exit code: fail if any required target failed
    critical_failures = [r for r in results if not r["passed"] and r["target"].get("required", True)]
    if critical_failures:
        print(f"\n[!] Build matrix completed with {len(critical_failures)} required component failure(s).")
        sys.exit(1)
    else:
        print("\n[✓] All required build targets completed successfully.")
        sys.exit(0)

if __name__ == "__main__":
    run_build_matrix()
