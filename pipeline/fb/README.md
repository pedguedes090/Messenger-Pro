# Facebook 576 patch (seen + ads)

End-to-end single-APK builder. See `../PIPELINE.md` for the full pipeline.

## Inputs

- `base.apk` (arm64) and `split_config.xxhdpi.apk` from the Facebook 576
  `.apks` bundle (versionCode 474227017).
- the 18 decompressed DEX (`classes.dex` .. `classes18.dex`) from the
  superpack blob (`store-0.dex.spo`), extracted on-device or via the Phase-4
  decompressor.

## Run

```
export FB_BASE=/path/base.apk
export FB_SPLIT=/path/split_config.xxhdpi.apk
export FB_DEXDIR=/path/18-dex
export BUILD_TOOLS=$ANDROID_HOME/build-tools/36.0.0
bash run.sh
```

Output: `out/Facebook-576-patched.apk` (single APK, story-seen + feed ads
blocked).

## What it patches

- `LX/BII;.A00` (story-seen, `secondary-5.dex`) - no-op.
- `LX/1lJ;.addNewEdgeToCollection(...)->Z` (feed ads, `secondary-1.dex`) -
  returns false for SPONSORED / PROMOTION / FRIENDLY_FEED_PROMOTION /
  HIGH_VALUE_PROMOTION edges.
