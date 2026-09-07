#!/usr/bin/env bash
# End-to-end Facebook 576 patch: seen-block + ads-block -> single MRV-signed APK.
#
# Inputs (env vars):
#   FB_APKM     path to the .apkm/.apks/.xapk bundle (base + split inside), OR
#   FB_BASE     path to base.apk (arm64)  AND
#   FB_SPLIT    path to split_config.xxhdpi.apk
#   FB_DEXDIR   directory with the 18 decompressed dex
#               (classes.dex, classes2.dex .. classes18.dex)
#   MRV_JAR     (optional) path to MRVPatcher-*.jar -> sign with the fixed MRV key
#               (shared with Messenger). Without it, signs with the debug keystore.
#
# The 18 decompressed dex come from the superpack decompressor (Phase 4). Until
# that lands, provide them via FB_DEXDIR (see PIPELINE.md).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${OUT_DIR:-$HERE/out}"
WORK="${WORK:-$HERE/build}"
mkdir -p "$OUT_DIR" "$WORK"

# Resolve bundle -> base + split
if [ -n "${FB_APKM:-}" ]; then
  echo "unpacking bundle: $FB_APKM"
  BND="$WORK/apkm"
  rm -rf "$BND"; mkdir -p "$BND"
  unzip -o -q "$FB_APKM" -d "$BND"
  FB_BASE="$(ls "$BND"/base.apk 2>/dev/null || true)"
  FB_SPLIT="$(ls "$BND"/split_config.xxhdpi.apk 2>/dev/null || ls "$BND"/split_config.xxxhdpi.apk 2>/dev/null || true)"
fi

FB_BASE="${FB_BASE:?set FB_BASE (or FB_APKM)}"
FB_SPLIT="${FB_SPLIT:?set FB_SPLIT (or FB_APKM)}"
FB_DEXDIR="${FB_DEXDIR:?set FB_DEXDIR to the 18-dex directory}"

# 1. Static dex patches. Each route is patched in the DEX where the 576 map
# places it: feed/async/VideoHome ads in secondary-1/3, story-seen in
# secondary-5, video/Reels ads in secondary-10, and additional sponsored-story
# replenishment in secondary-16.
echo "== [1/6] patch secondary-1.dex (feed + async feed ads) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes.dex" "$WORK/classes1_ads_patched.dex"

echo "== [2/6] patch secondary-3.dex (async-ads runnable) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes3.dex" "$WORK/classes3_ads_patched.dex"

echo "== [3/6] patch secondary-5.dex (block story-seen) =="
bash "$HERE/patch_dex/patch_dex.sh" seen "$FB_DEXDIR/classes5.dex" "$WORK/classes5_patched.dex"

echo "== [4/6] patch secondary-10.dex (video/Reels ad fetch) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes10.dex" "$WORK/classes10_ads_patched.dex"

echo "== [5/7] patch secondary-16.dex (additional sponsored-story fetch) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes16.dex" "$WORK/classes16_ads_patched.dex"

# 6. De-superpack: drop .spo, inject 18 secondary-N.dex with all patches.
echo "== [6/7] de-superpack base.apk =="
python3 "$HERE/superpack/desuper.py" \
  --base "$FB_BASE" --dexdir "$FB_DEXDIR" \
  --patched1 "$WORK/classes1_ads_patched.dex" \
  --patched3 "$WORK/classes3_ads_patched.dex" \
  --patched5 "$WORK/classes5_patched.dex" \
  --patched10 "$WORK/classes10_ads_patched.dex" \
  --patched16 "$WORK/classes16_ads_patched.dex" \
  --out "$WORK/base_desuper.apk"

# 7. Clear requiredSplitTypes so the single APK installs without its split
echo "== [7/7] clear requiredSplitTypes =="
python3 "$HERE/superpack/patch_manifest.py" \
  --apk "$WORK/base_desuper.apk" --out "$WORK/base_single_unsigned.apk"

# 6. Binary resource merge (base + split) via ARSCLib
mkdir -p "$WORK/bundle"
cp "$WORK/base_single_unsigned.apk" "$WORK/bundle/base.apk"
cp "$FB_SPLIT" "$WORK/bundle/split_config.xxhdpi.apk"
bash "$HERE/merge/merge.sh" "$WORK/bundle" "$WORK/merged.apk"

# The merged APK already contains the patched secondary-N.dex assets injected
# above. Keep a direct copy as the final unsigned artifact so metadata hashes
# and the DEX bytes remain exactly the ones tested before the resource merge.
cp "$WORK/merged.apk" "$WORK/final_unsigned.apk"

# Android 11+ requires resources.arsc to be uncompressed and 4-byte aligned.
# The ZIP rewrite above can shift its data offset, so align before publishing or MRV patching.
ZIPALIGN_BIN="${ZIPALIGN:-}"
if [ -z "$ZIPALIGN_BIN" ] && [ -n "${BUILD_TOOLS:-}" ] && [ -x "$BUILD_TOOLS/zipalign" ]; then
  ZIPALIGN_BIN="$BUILD_TOOLS/zipalign"
fi
if [ -z "$ZIPALIGN_BIN" ] && [ -n "${ANDROID_HOME:-}" ]; then
  ZIPALIGN_BIN="$(find "$ANDROID_HOME/build-tools" -type f -name zipalign 2>/dev/null | sort -V | tail -n 1)"
fi
if [ -z "$ZIPALIGN_BIN" ]; then
  ZIPALIGN_BIN="$(command -v zipalign || true)"
fi
if [ -z "$ZIPALIGN_BIN" ] || [ ! -x "$ZIPALIGN_BIN" ]; then
  echo "ERROR: zipalign not found; refusing to publish an Android 11-incompatible APK" >&2
  exit 1
fi
"$ZIPALIGN_BIN" -f -p 4 "$WORK/final_unsigned.apk" "$WORK/final_aligned.apk"
"$ZIPALIGN_BIN" -c -p 4 "$WORK/final_aligned.apk"
mv "$WORK/final_aligned.apk" "$WORK/final_unsigned.apk"

# Publish the exact aligned pre-MRV artifact. Its original appComponentFactory is preserved,
# so users can patch it later with their own MRV installation/key.
cp "$WORK/final_unsigned.apk" "$OUT_DIR/Facebook-576-clean.apk"
echo "clean aligned pre-MRV APK -> $OUT_DIR/Facebook-576-clean.apk"

# 6. Sign: MRV (shared key with Messenger) or debug keystore
if [ -n "${MRV_JAR:-}" ]; then
  echo "== [6/6] MRV patch + sign =="
  java -jar "$MRV_JAR" "$WORK/final_unsigned.apk" -p -o "$OUT_DIR" -f
  echo "done -> $OUT_DIR (look for final_unsigned-mrv.apk)"
else
  echo "== [6/6] zipalign + debug sign =="
  BT="${BUILD_TOOLS:?set BUILD_TOOLS for debug-sign, or set MRV_JAR}"
  KS="${KS:-$HOME/.android/debug.keystore}"
  KS_PASS="${KS_PASS:-android}"
  KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
  KEY_PASS="${KEY_PASS:-android}"
  APKSIGNER_BIN="${APKSIGNER:-$BT/apksigner}"
  if [ ! -f "$APKSIGNER_BIN" ] && [ -f "$BT/apksigner.bat" ]; then
    APKSIGNER_BIN="$BT/apksigner.bat"
  fi
  if [ ! -f "$APKSIGNER_BIN" ]; then
    echo "ERROR: apksigner not found under $BT" >&2
    exit 1
  fi
  "$BT/zipalign" -f -p 4 "$WORK/final_unsigned.apk" "$WORK/final_aligned.apk"
  "$APKSIGNER_BIN" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" \
    --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
    --out "$OUT_DIR/Facebook-576-patched.apk" "$WORK/final_aligned.apk"
  echo "done -> $OUT_DIR/Facebook-576-patched.apk"
fi
