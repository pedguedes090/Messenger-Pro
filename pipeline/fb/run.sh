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

# 1. Static dex patches
echo "== [1/6] patch secondary-5.dex (block story-seen) =="
bash "$HERE/patch_dex/patch_dex.sh" seen "$FB_DEXDIR/classes5.dex" "$WORK/classes5_patched.dex"

echo "== [2/6] patch secondary-1.dex (block feed ads) =="
bash "$HERE/patch_dex/patch_dex.sh" ads "$FB_DEXDIR/classes.dex" "$WORK/classes_ads_patched.dex"

echo "== [3/6] patch story ads (AD_BUCKETS/IN_DISC) =="
bash "$HERE/patch_dex/patch_dex.sh" story "$FB_DEXDIR/classes.dex" "$WORK/classes_story_patched.dex"

echo "== [4/6] patch game ads (quicksilver/AudienceNetwork) =="
bash "$HERE/patch_dex/patch_dex.sh" game "$FB_DEXDIR/classes.dex" "$WORK/classes_game_patched.dex"

# 5. De-superpack: drop .spo, inject 18 secondary-N.dex (secondary-5 = seen-patched)
echo "== [5/6] de-superpack base.apk =="
python3 "$HERE/superpack/desuper.py" \
  --base "$FB_BASE" --dexdir "$FB_DEXDIR" --patched5 "$WORK/classes5_patched.dex" \
  --out "$WORK/base_desuper.apk"

# 6. Clear requiredSplitTypes so the single APK installs without its split
echo "== [7/6] clear requiredSplitTypes =="
python3 "$HERE/superpack/patch_manifest.py" \
  --apk "$WORK/base_desuper.apk" --out "$WORK/base_single_unsigned.apk"

# 6. Binary resource merge (base + split) via ARSCLib
mkdir -p "$WORK/bundle"
cp "$WORK/base_single_unsigned.apk" "$WORK/bundle/base.apk"
cp "$FB_SPLIT" "$WORK/bundle/split_config.xxhdpi.apk"
bash "$HERE/merge/merge.sh" "$WORK/bundle" "$WORK/merged.apk"

# 7. Integrate all patched dexes into the merged APK
python3 "$HERE/superpack/integrate_ads.py" \
  --apk "$WORK/merged.apk" --ads-dex "$WORK/classes_ads_patched.dex" \
  --story-dex "$WORK/classes_story_patched.dex" --game-dex "$WORK/classes_game_patched.dex" \
  --out "$WORK/final_unsigned.apk"

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
  "$BT/zipalign" -f -p 4 "$WORK/final_unsigned.apk" "$WORK/final_aligned.apk"
  "$BT/apksigner" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" \
    --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
    --out "$OUT_DIR/Facebook-576-patched.apk" "$WORK/final_aligned.apk"
  echo "done -> $OUT_DIR/Facebook-576-patched.apk"
fi
