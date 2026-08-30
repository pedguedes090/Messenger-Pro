#!/usr/bin/env bash
# End-to-end Facebook 576 patch: seen-block + ads-block -> single signed APK.
#
# Required inputs (env vars or edit the defaults below):
#   FB_BASE     path to base.apk (arm64, from the .apks bundle)
#   FB_SPLIT    path to split_config.xxhdpi.apk (or split_config.arm64_v8a.apk)
#   FB_DEXDIR   directory with the 18 decompressed dex
#               (classes.dex, classes2.dex .. classes18.dex)
#   BUILD_TOOLS Android SDK build-tools dir (contains zipalign + apksigner)
#
# Optional signing (defaults to the Android debug keystore):
#   KS         keystore  (default ~/.android/debug.keystore)
#   KS_PASS    keystore password (default android)
#   KEY_ALIAS  alias (default androiddebugkey)
#   KEY_PASS   key password (default android)
#
# The 18 decompressed dex come from the superpack decompressor (Phase 4). Until
# that lands, extract them on a device/adb from the running Facebook app, or
# provide them another way -- see PIPELINE.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${OUT_DIR:-$HERE/out}"
WORK="${WORK:-$HERE/build}"
mkdir -p "$OUT_DIR" "$WORK"

FB_BASE="${FB_BASE:?set FB_BASE to base.apk}"
FB_SPLIT="${FB_SPLIT:?set FB_SPLIT to split_config.xxhdpi.apk}"
FB_DEXDIR="${FB_DEXDIR:?set FB_DEXDIR to the 18-dex directory}"
BT="${BUILD_TOOLS:?set BUILD_TOOLS to Android SDK build-tools dir}"

KS="${KS:-$HOME/.android/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KEY_PASS="${KEY_PASS:-android}"

# 1. Static dex patches
echo "== [1/7] patch secondary-5.dex (block story-seen) =="
bash "$HERE/patch_dex/patch_dex.sh" seen "$FB_DEXDIR/classes5.dex" "$WORK/classes5_patched.dex"

echo "== [2/7] patch secondary-1.dex (block feed ads) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes.dex" "$WORK/classes_ads_patched.dex"

# 2. De-superpack: remove .spo, inject 18 secondary-N.dex (secondary-5 = seen-patched)
echo "== [3/7] de-superpack base.apk =="
python3 "$HERE/superpack/desuper.py" \
  --base "$FB_BASE" --dexdir "$FB_DEXDIR" --patched5 "$WORK/classes5_patched.dex" \
  --out "$WORK/base_desuper.apk"

# 3. Remove requiredSplitTypes so the single APK installs without its split
echo "== [4/7] clear requiredSplitTypes in manifest =="
python3 "$HERE/superpack/patch_manifest.py" \
  --apk "$WORK/base_desuper.apk" --out "$WORK/base_single_unsigned.apk"

# 4. Binary resource merge (base + split) via ARSCLib
echo "== [5/7] merge base + split into single APK =="
mkdir -p "$WORK/bundle"
cp "$WORK/base_single_unsigned.apk" "$WORK/bundle/base.apk"
cp "$FB_SPLIT" "$WORK/bundle/split_config.xxhdpi.apk"
bash "$HERE/merge/merge.sh" "$WORK/bundle" "$WORK/merged.apk"

# 5. Integrate ads-patched secondary-1.dex into the merged APK
echo "== [6/7] integrate ads-patched secondary-1.dex =="
python3 "$HERE/superpack/integrate_ads.py" \
  --apk "$WORK/merged.apk" --ads-dex "$WORK/classes_ads_patched.dex" \
  --out "$WORK/final_unsigned.apk"

# 6. zipalign (resources.arsc must be stored + 4-byte aligned for Android 16) + sign
echo "== [7/7] align + sign =="
"$BT/zipalign" -f -p 4 "$WORK/final_unsigned.apk" "$WORK/final_aligned.apk"
"$BT/apksigner" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT_DIR/Facebook-576-patched.apk" "$WORK/final_aligned.apk"

echo "done -> $OUT_DIR/Facebook-576-patched.apk"
