#!/usr/bin/env bash
#
# rotate-trust-anchors.sh — lockstep trust-anchor rotation for the pinned
# runtime template bundle.
#
# Run this AFTER replacing runtimes/mgba-unified/template.apk (e.g. with a
# freshly built :template-apk output). It re-computes the real SHA-256 digests
# from the binary and rewrites, in lockstep:
#   1. runtimes/mgba-unified/runtime.json           (protected_entries)
#   2. core/.../RuntimeRegistry.kt                  (TRUSTED_TEMPLATES,
#                                                    TRUSTED_PROTECTED_ENTRIES)
#   3. core/.../RuntimeDescriptor.kt                (MGBA_UNIFIED.protectedEntries)
#
# The whole-APK template hash (TRUSTED_TEMPLATES) and the entry hashes must
# always describe the same binary; RuntimeBundleIntegrityTest enforces this
# in CI and fails the build when the three files drift apart.
#
# Usage:  ./scripts/rotate-trust-anchors.sh
#
set -euo pipefail

TEMPLATE="runtimes/mgba-unified/template.apk"
RUNTIME_JSON="runtimes/mgba-unified/runtime.json"
REGISTRY="core/src/main/kotlin/com/retropack/domain/runtime/RuntimeRegistry.kt"
DESCRIPTOR="core/src/main/kotlin/com/retropack/domain/runtime/RuntimeDescriptor.kt"

die() { echo "::error:: $*" >&2; exit 1; }

[[ -f "$TEMPLATE" ]] || die "pinned template not found: $TEMPLATE"
[[ -f "$RUNTIME_JSON" ]] || die "runtime descriptor not found: $RUNTIME_JSON"
[[ -f "$REGISTRY" ]] || die "RuntimeRegistry source not found: $REGISTRY"
[[ -f "$DESCRIPTOR" ]] || die "RuntimeDescriptor source not found: $DESCRIPTOR"

command -v sha256sum >/dev/null || die "sha256sum is required"
command -v unzip >/dev/null || die "unzip is required"

APK_HASH="$(sha256sum "$TEMPLATE" | cut -d' ' -f1)"
DEX_HASH="$(unzip -p "$TEMPLATE" classes.dex | sha256sum | cut -d' ' -f1)"
SO_NAME="libretropack-runtime.so"
SO_HASH="$(unzip -p "$TEMPLATE" "lib/arm64-v8a/${SO_NAME}" | sha256sum | cut -d' ' -f1)"

echo "template.apk      sha256: ${APK_HASH}"
echo "classes.dex       sha256: ${DEX_HASH}"
echo "lib/arm64-v8a/${SO_NAME} sha256: ${SO_HASH}"

# --- 1. runtime.json ---------------------------------------------------------
python3 - "$RUNTIME_JSON" "$APK_HASH" "$DEX_HASH" "$SO_HASH" "$SO_NAME" <<'PY'
import json, sys, collections
path = sys.argv[1]
dex, so, so_name = sys.argv[3:6]
with open(path) as f:
    doc = json.load(f, object_pairs_hook=collections.OrderedDict)
doc["protected_entries"] = collections.OrderedDict([
    ("classes.dex", f"sha256:{dex}"),
    (f"lib/arm64-v8a/{so_name}", f"sha256:{so}"),
])
with open(path, "w") as f:
    json.dump(doc, f, indent=2)
    f.write("\n")
print(f"updated {path}")
PY

# --- 2. RuntimeRegistry.kt ---------------------------------------------------
# TRUSTED_TEMPLATES whole-APK hash (bare hex constant)
perl -0pi -e "s/(RUNTIME_MGBA_UNIFIED to \")[0-9a-fA-F]{64}(\")/\${1}${APK_HASH}\${2}/" "$REGISTRY"
# TRUSTED_PROTECTED_ENTRIES + old-name entries -> canonical new-name entries
perl -0pi -e "s/\"(lib\/arm64-v8a\/(?:libmgba|libretropack-runtime)\.so)\" to \"[0-9a-fA-F]{64}\"/\"lib\/arm64-v8a\/${SO_NAME}\" to \"${SO_HASH}\"/g" "$REGISTRY"
perl -0pi -e "s/(\"classes\.dex\" to \")[0-9a-fA-F]{64}(\")/\${1}${DEX_HASH}\${2}/g" "$REGISTRY"
echo "updated $REGISTRY"

# --- 3. RuntimeDescriptor.kt -------------------------------------------------
perl -0pi -e "s/\"(lib\/arm64-v8a\/(?:libmgba|libretropack-runtime)\.so)\" to \"sha256:[0-9a-fA-F]{64}\"/\"lib\/arm64-v8a\/${SO_NAME}\" to \"sha256:${SO_HASH}\"/g" "$DESCRIPTOR"
perl -0pi -e "s/(\"classes\.dex\" to \"sha256:)[0-9a-fA-F]{64}(\")/\${1}${DEX_HASH}\${2}/g" "$DESCRIPTOR"
echo "updated $DESCRIPTOR"

echo ""
echo "Trust anchors rotated. Verify with:"
echo "  env -u ANDROID_HOME -u ANDROID_SDK_ROOT ./gradlew :core:test --tests 'com.retropack.domain.runtime.RuntimeBundleIntegrityTest'"
