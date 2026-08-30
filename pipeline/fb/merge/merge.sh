#!/usr/bin/env bash
# Merge a split APK bundle into a single APK using REAndroid ARSCLib.
# apktool/aapt2 cannot recompile Facebook's obfuscated resources, so the
# resources.arsc tables are merged in binary form.
# Usage: merge.sh <bundleDir> <output.apk>
set -euo pipefail

DIR="${1:?bundle dir containing base.apk + split_config.xxhdpi.apk}"
OUT="${2:?output apk}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$HERE/lib"
CLASSES="$HERE/classes"
mkdir -p "$LIB" "$CLASSES"

# com.github.REAndroid:ARSCLib:V1.4.0 from JitPack (no transitive deps)
if [ ! -f "$LIB/ARSCLib.jar" ]; then
  echo "fetching ARSCLib.jar"
  curl -fsSL -o "$LIB/ARSCLib.jar" \
    https://jitpack.io/com/github/REAndroid/ARSCLib/V1.4.0/ARSCLib-V1.4.0.jar
fi

javac -cp "$LIB/ARSCLib.jar" -d "$CLASSES" "$HERE/MergeApk.java"
HEAP="${JAVA_HEAP:-4g}"
java -Xmx"$HEAP" -cp "$LIB/ARSCLib.jar:$CLASSES" MergeApk "$DIR" "$OUT"
echo "wrote $OUT"
