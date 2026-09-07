#!/usr/bin/env bash
# Compile + run the dexlib2 static patchers for Facebook 576.
# Usage: patch_dex.sh <seen|ads> <input.dex> <output.dex>
set -euo pipefail

KIND="${1:?usage: patch_dex.sh <seen|ads> <input.dex> <output.dex>}"
INPUT="${2:?input.dex}"
OUTPUT="${3:?output.dex}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$HERE/lib"
CLASSES="$HERE/classes"
mkdir -p "$LIB" "$CLASSES"

# Maven Central artifacts (dexlib2 2.5.2 toolchain)
fetch() {  # url -> outfile
  if [ ! -f "$2" ]; then
    echo "fetching $(basename "$2")"
    curl -fsSL -o "$2" "$1"
  fi
}
fetch https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar "$LIB/dexlib2-2.5.2.jar"
fetch https://repo1.maven.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar     "$LIB/util-2.5.2.jar"
fetch https://repo1.maven.org/maven2/com/google/guava/guava/27.1-android/guava-27.1-android.jar "$LIB/guava-27.1-android.jar"
fetch https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar "$LIB/jsr305-3.0.2.jar"

CP="$LIB/dexlib2-2.5.2.jar:$LIB/util-2.5.2.jar:$LIB/guava-27.1-android.jar:$LIB/jsr305-3.0.2.jar"

MAIN=PatchDex
[ "$KIND" = "ads" ] && MAIN=PatchAds

javac -cp "$CP" -d "$CLASSES" "$HERE/$MAIN.java"
java -cp "$CP:$CLASSES" "$MAIN" "$INPUT" "$OUTPUT"
echo "wrote $OUTPUT"
