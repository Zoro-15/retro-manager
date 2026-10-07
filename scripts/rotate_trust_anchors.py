#!/usr/bin/env python3
import hashlib
import json
import os
import re
import sys
import zipfile
from collections import OrderedDict
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
REGISTRY_FILE = ROOT_DIR / "core/src/main/kotlin/com/retropack/domain/runtime/RuntimeRegistry.kt"
DESCRIPTOR_FILE = ROOT_DIR / "core/src/main/kotlin/com/retropack/domain/runtime/RuntimeDescriptor.kt"
RUNTIMES_DIR = ROOT_DIR / "runtimes"

def compute_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def rotate_bundle(bundle_dir: Path):
    template = bundle_dir / "template.apk"
    runtime_json = bundle_dir / "runtime.json"
    if not template.is_file() or not runtime_json.is_file():
        return

    bundle_name = bundle_dir.name
    print(f"=== Rotating trust anchors for {bundle_name} ===")

    apk_bytes = template.read_bytes()
    apk_hash = compute_sha256(apk_bytes)
    print(f"  template.apk: {apk_hash}")

    protected_entries = OrderedDict()
    with zipfile.ZipFile(template, "r") as zf:
        for info in sorted(zf.infolist(), key=lambda i: i.filename):
            if info.filename == "classes.dex" or (info.filename.startswith("lib/arm64-v8a/") and info.filename.endswith(".so")):
                entry_data = zf.read(info.filename)
                entry_hash = compute_sha256(entry_data)
                protected_entries[info.filename] = f"sha256:{entry_hash}"
                print(f"  {info.filename}: {entry_hash}")

    # 1. Update runtime.json
    with open(runtime_json, "r", encoding="utf-8") as f:
        doc = json.load(f, object_pairs_hook=OrderedDict)
    doc["protected_entries"] = protected_entries
    with open(runtime_json, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
    print(f"  updated {runtime_json.relative_to(ROOT_DIR)}")

    # 2. Update RuntimeRegistry.kt
    if REGISTRY_FILE.is_file():
        reg_text = REGISTRY_FILE.read_text(encoding="utf-8")
        const_id = "RUNTIME_" + bundle_name.upper().replace("-", "_")
        
        # Whole-APK hash in TRUSTED_TEMPLATES
        reg_text = re.sub(
            rf'({const_id}\s+to\s+")[0-9a-fA-F]{{64}}(")',
            rf'\g<1>{apk_hash}\g<2>',
            reg_text
        )
        REGISTRY_FILE.write_text(reg_text, encoding="utf-8")
        print(f"  updated {const_id} in {REGISTRY_FILE.name}")

def main():
    target_arg = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else None
    if target_arg:
        rotate_bundle(Path(target_arg).resolve())
    else:
        for d in sorted(RUNTIMES_DIR.iterdir()):
            if d.is_dir():
                rotate_bundle(d)
    print("\nTrust anchors rotated.")

if __name__ == "__main__":
    main()
