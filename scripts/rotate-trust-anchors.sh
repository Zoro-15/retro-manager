#!/usr/bin/env bash
#
# rotate-trust-anchors.sh — lockstep trust-anchor rotation for pinned
# runtime template bundles.
#
# Usage:
#   ./scripts/rotate-trust-anchors.sh [runtime_dir]
#
# If runtime_dir is provided, only that bundle is rotated.
# Otherwise, all bundles in runtimes/ with template.apk and runtime.json are rotated.
#
set -euo pipefail

REGISTRY="core/src/main/kotlin/com/retropack/domain/runtime/RuntimeRegistry.kt"
DESCRIPTOR="core/src/main/kotlin/com/retropack/domain/runtime/RuntimeDescriptor.kt"

die() { echo "::error:: $*" >&2; exit 1; }

[[ -f "$REGISTRY" ]] || die "RuntimeRegistry source not found: $REGISTRY"
[[ -f "$DESCRIPTOR" ]] || die "RuntimeDescriptor source not found: $DESCRIPTOR"

command -v sha256sum >/dev/null || die "sha256sum is required"
command -v unzip >/dev/null || die "unzip is required"

rotate_bundle() {
  local bundle_dir="$1"
  local template="${bundle_dir}/template.apk"
  local runtime_json="${bundle_dir}/runtime.json"

  [[ -f "$template" ]] || { echo "Skipping $bundle_dir: template.apk not found"; return 0; }
  [[ -f "$runtime_json" ]] || { echo "Skipping $bundle_dir: runtime.json not found"; return 0; }

  echo "=== Rotating trust anchors for $bundle_dir ==="

  local apk_hash="$(sha256sum "$template" | cut -d' ' -f1)"
  local dex_hash="$(unzip -p "$template" classes.dex | sha256sum | cut -d' ' -f1)"

  # Detect native library name inside APK (e.g. libretropack-runtime.so or libretropack-runtime-*.so)
  local so_path="$(unzip -l "$template" | grep -oE 'lib/arm64-v8a/lib[^ ]+\.so' | head -n 1 || true)"
  local so_name=""
  local so_hash=""
  if [[ -n "$so_path" ]]; then
    so_name="$(basename "$so_path")"
    so_hash="$(unzip -p "$template" "$so_path" | sha256sum | cut -d' ' -f1)"
  fi

  echo "  template.apk: $apk_hash"
  echo "  classes.dex:  $dex_hash"
  if [[ -n "$so_name" ]]; then
    echo "  $so_name: $so_hash"
  fi

  # --- 1. runtime.json ---
  python3 - "$runtime_json" "$dex_hash" "$so_hash" "$so_name" <<'PY'
import json, sys, collections
path = sys.argv[1]
dex = sys.argv[2]
so = sys.argv[3] if len(sys.argv) > 3 else ""
so_name = sys.argv[4] if len(sys.argv) > 4 else ""

with open(path) as f:
    doc = json.load(f, object_pairs_hook=collections.OrderedDict)

entries = [("classes.dex", f"sha256:{dex}")]
if so_name and so:
    entries.append((f"lib/arm64-v8a/{so_name}", f"sha256:{so}"))

doc["protected_entries"] = collections.OrderedDict(entries)

with open(path, "w") as f:
    json.dump(doc, f, indent=2)
    f.write("\n")
print(f"  updated {path}")
PY

  # If this runtime is registered in RuntimeRegistry.kt, update its compiled-in whole-APK anchor
  local runtime_id="$(basename "$bundle_dir")"
  local const_name="RUNTIME_${runtime_id//-/_}"
  const_name="${const_name^^}"
  
  if grep -q "$const_name" "$REGISTRY"; then
    perl -0pi -e "s/(${const_name} to \")[0-9a-fA-F]{64}(\")/\${1}${apk_hash}\${2}/" "$REGISTRY"
    echo "  updated $const_name in $REGISTRY"
  fi

  if [[ "$runtime_id" == "mgba-unified" ]]; then
    if [[ -n "$so_name" ]]; then
      perl -0pi -e "s/\"(lib\/arm64-v8a\/(?:libmgba|libretropack-runtime[^\"]*)\.so)\" to \"[0-9a-fA-F]{64}\"/\"lib\/arm64-v8a\/${so_name}\" to \"${so_hash}\"/g" "$REGISTRY"
      perl -0pi -e "s/\"(lib\/arm64-v8a\/(?:libmgba|libretropack-runtime[^\"]*)\.so)\" to \"sha256:[0-9a-fA-F]{64}\"/\"lib\/arm64-v8a\/${so_name}\" to \"sha256:${so_hash}\"/g" "$DESCRIPTOR"
    fi
    perl -0pi -e "s/(\"classes\.dex\" to \")[0-9a-fA-F]{64}(\")/\${1}${dex_hash}\${2}/g" "$REGISTRY"
    perl -0pi -e "s/(\"classes\.dex\" to \"sha256:)[0-9a-fA-F]{64}(\")/\${1}${dex_hash}\${2}/g" "$DESCRIPTOR"
    echo "  updated $REGISTRY & $DESCRIPTOR"
  fi

}

if [[ $# -ge 1 ]]; then
  rotate_bundle "$1"
else
  for dir in runtimes/*; do
    if [[ -d "$dir" ]]; then
      rotate_bundle "$dir"
    fi
  done
fi

echo ""
echo "Trust anchors rotated. Verify with:"
echo "  env -u ANDROID_HOME -u ANDROID_SDK_ROOT ./gradlew :core:test --tests 'com.retropack.domain.runtime.RuntimeBundleIntegrityTest'"
