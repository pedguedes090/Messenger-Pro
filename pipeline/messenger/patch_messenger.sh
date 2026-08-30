#!/usr/bin/env bash
# Patch Messenger with MRVPatcher (LSPatch fork). VERIFIED: output byte-identical
# config + same MRV signing cert as the hand-patched Messenger (release v1.2.9).
#
# The MRVPatcher jar embeds the fixed MRV private keystore (mrvkey.jks, pass
# "123456") and the MRV public signature, so no keystore args are needed.
#
# Usage:
#   MESSENGER_APK=/path/Messenger.apk \
#   MODULES="tn.amin.mpro2 app.neonorbit.chatheadenabler" \
#   OUT_DIR=./out bash patch_messenger.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$HERE/lib/MRVPatcher-5.8.1.jar"

if [ ! -f "$JAR" ]; then
  mkdir -p "$(dirname "$JAR")"
  echo "fetching MRVPatcher-5.8.1.jar"
  curl -fsSL -o "$JAR" \
    https://github.com/NeonOrbit/MRVPatcher/releases/download/5.8.1/MRVPatcher-5.8.1.jar
fi

IN="${MESSENGER_APK:?set MESSENGER_APK to the stock Messenger APK}"
OUT_DIR="${OUT_DIR:-$HERE/out}"
MODULES="${MODULES:-tn.amin.mpro2}"
mkdir -p "$OUT_DIR"

# Flags match the hand-patched Messenger config.json:
#   confFixed=false, fallback=false, pkgMasked=false, exModules=[MODULES]
java -jar "$JAR" "$IN" -o "$OUT_DIR" -f --modules $MODULES

echo "done -> $OUT_DIR (look for *-mrv.apk)"
