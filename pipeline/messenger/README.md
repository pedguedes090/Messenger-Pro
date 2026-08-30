# Messenger 576 module injection (Phase 3)

Injects the MessengerPro module (`tn.amin.mpro2`) and ChatHeadEnabler
(`app.neonorbit.chatheadenabler`) into a stock Messenger 576 APK and re-signs
it with the fixed MRV key, so no-root users get a pre-patched Messenger.

## Tooling

**MRVPatchManager** (LSPatch fork, github.com/NeonOrbit/MRVPatchManager):

- injects an LSPosed module + re-signs with the fixed key
  `MRV_PUBLIC_SIGNATURE` (Messenger + Facebook share the key so login state is
  shared),
- options: `--fix-conf`, `--mask-pkg`, `--fallback`, `--modules`,
  `--key-args`,
- handles `.apks` bundles via `ApkBundles.kt` / `UniversalInstaller.kt`; the
  file picker filters apk/zip/octet-stream, so rename `.apks` to `.zip`.

## Command (planned)

```
java -jar mrvpatch-cli.jar \
  --modules tn.amin.mpro2 app.neonorbit.chatheadenabler \
  --fix-conf --mask-pkg --fallback --key-args MRV_PUBLIC_SIGNATURE \
  -o messenger-patched.apk messenger-576.apk
```

## Status

- **Not yet automated.** Needs a host-compiled MRVPatchManager CLI (the
  Android app is UI-driven; the CLI must be built from the LSPatch backend).
- The module APK is built with `./gradlew assembleDebug`.
- Messenger target: `com.facebook.orca` 576.0.0.47.92 / versionCode 345212666
  (arm64 base, no split needed).
