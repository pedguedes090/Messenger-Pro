#!/usr/bin/env bash
# Build an installable Facebook 582 APK with the version-specific DEX ad filter.
# Usage: build_582_apk.sh <input.apk> <output.apk>
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <input.apk> <output.apk>" >&2
  exit 2
fi

INPUT="$(realpath "$1")"
OUTPUT="$(realpath -m "$2")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCHER="$SCRIPT_DIR/patch_dex/patch_dex.sh"
APK_TOOL="$SCRIPT_DIR/patch_582_apk.py"
PYTHON="${PYTHON:-python3}"
if [[ -n "${WORK:-}" ]]; then
  WORK="$(realpath -m "$WORK")"
else
  WORK="$(mktemp -d "${TMPDIR:-/tmp}/mpro-facebook-582.XXXXXX")"
fi
RAW_DEX="$WORK/dex"
PATCHED_DEX="$WORK/patched"
UNSIGNED="$WORK/Facebook-582-unsigned.apk"
ALIGNED="$WORK/Facebook-582-aligned.apk"

if [[ "$INPUT" == "$OUTPUT" ]]; then
  echo "output must not overwrite the supplied APK" >&2
  exit 2
fi
if [[ ! -f "$INPUT" || ! -f "$PATCHER" || ! -f "$APK_TOOL" ]]; then
  echo "input APK or Facebook 582 patch tools are missing" >&2
  exit 2
fi
mkdir -p "$(dirname "$OUTPUT")" "$RAW_DEX" "$PATCHED_DEX"

find_android_tool() {
  local tool="$1"
  local override="$2"
  local candidate="${!override:-}"
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    printf '%s\n' "$candidate"
    return 0
  fi
  if [[ -n "${BUILD_TOOLS:-}" && -x "$BUILD_TOOLS/$tool" ]]; then
    printf '%s\n' "$BUILD_TOOLS/$tool"
    return 0
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$sdk" && -d "$sdk/build-tools" ]]; then
    candidate="$(find "$sdk/build-tools" -type f -name "$tool" -perm -u+x 2>/dev/null | sort -V | tail -n 1)"
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi
  command -v "$tool"
}

ZIPALIGN_BIN="$(find_android_tool zipalign ZIPALIGN)" || {
  echo "zipalign not found; set BUILD_TOOLS or ZIPALIGN" >&2
  exit 2
}
APKSIGNER_BIN="$(find_android_tool apksigner APKSIGNER)" || {
  echo "apksigner not found; set BUILD_TOOLS or APKSIGNER" >&2
  exit 2
}
KEYSTORE="${KS:-$HOME/.android/debug.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
  KEYTOOL="${KEYTOOL:-$(command -v keytool || true)}"
  if [[ -z "$KEYTOOL" && -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
  fi
  if [[ -z "$KEYTOOL" ]]; then
    echo "Android debug keystore is missing and keytool was not found: $KEYSTORE" >&2
    exit 2
  fi
  mkdir -p "$(dirname "$KEYSTORE")"
  echo "Creating debug keystore for this build: $KEYSTORE"
  "$KEYTOOL" -genkeypair -noprompt -storetype JKS \
    -keystore "$KEYSTORE" \
    -storepass "${KS_PASS:-android}" \
    -keypass "${KEY_PASS:-android}" \
    -alias "${KEY_ALIAS:-androiddebugkey}" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$PYTHON" "$APK_TOOL" extract --apk "$INPUT" --outdir "$RAW_DEX"

patch_dex() {
  local name="$1"
  local expected_targets="$2"
  local expected_counts="$3"
  local source="$RAW_DEX/$name"
  local dest="$PATCHED_DEX/$name"
  local result
  result="$(bash "$PATCHER" ads "$source" "$dest" facebook-582 2>&1)" || {
    printf '%s\n' "$result" >&2
    return 1
  }
  printf '%s\n' "$result"
  if ! grep -Fq "$expected_targets" <<<"$result" || ! grep -Fq "$expected_counts" <<<"$result"; then
    echo "unexpected Facebook 582 patch targets or verification for $name" >&2
    return 1
  fi
}

patch_dex classes2.dex \
  'affiliateProductOverlay=false brandedAffiliateProductOverlay=false feedReelPreserved=true' \
  'feedEdge=1 shortsPool=0 sponsoredSelector=1 videoAdRequest=0 storyAdFetch=0 affiliateOverlay=0 affiliateOverlayOutputsVerified=true'
patch_dex classes4.dex 'shortsPool=true' \
  'feedEdge=0 shortsPool=1 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=0'
patch_dex classes7.dex 'storyAdFetch=true' \
  'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=1'
patch_dex classes13.dex \
  'videoAdRequest=true storyAdFetch=false affiliateProductOverlay=true brandedAffiliateProductOverlay=true feedReelPreserved=true' \
  'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=1 storyAdFetch=0 affiliateOverlay=2 affiliateOverlayOutputsVerified=true'

"$PYTHON" "$APK_TOOL" replace --apk "$INPUT" --patch-dir "$PATCHED_DEX" --out "$UNSIGNED"
"$ZIPALIGN_BIN" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$ZIPALIGN_BIN" -c -p 4 "$ALIGNED"
"$APKSIGNER_BIN" sign \
  --ks "$KEYSTORE" --ks-pass "pass:${KS_PASS:-android}" \
  --ks-key-alias "${KEY_ALIAS:-androiddebugkey}" \
  --key-pass "pass:${KEY_PASS:-android}" --out "$OUTPUT" "$ALIGNED"
"$APKSIGNER_BIN" verify --verbose "$OUTPUT"
echo "SIGNED_APK=$OUTPUT"
echo "APK_SIZE=$(wc -c < "$OUTPUT" | tr -d ' ')"
