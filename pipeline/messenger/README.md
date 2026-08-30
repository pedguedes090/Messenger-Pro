# Messenger 576 module injection (VERIFIED)

Injects the MessengerPro module (`tn.amin.mpro2`) into a stock Messenger 576 APK
and re-signs with the fixed MRV key. **Verified**: the output matches the
hand-patched Messenger in release v1.2.9 (same MRV cert + same config.json).

## Tooling

**MRVPatcher** (LSPatch fork, github.com/NeonOrbit/MRVPatcher) is a runnable JAR
(`Main-Class: org.lsposed.patch.LSPatch`). It embeds:

- the fixed MRV private keystore `assets/mrvkey.jks` (pass `123456`),
- the MRV public signature (`CN=Facebook, O=MRV, ST=CA, C=US`,
  SHA-256 `217d4345ad36c9dce82da6ad5494b7c68a84e1077893fe0eb9f14c428d3e259c`).

`MRVPatcher.patch(args)` == `LSPatch.main(args)`, so `java -jar` uses the exact
same code path as the MRVPatchManager Android app.

## Command (verified)

```
export MESSENGER_APK=/path/Messenger-576.apk
export MODULES="tn.amin.mpro2"
bash patch_messenger.sh
```

Output: `out/Messenger-576-*-mrv.apk`, signed with the MRV key.

### Verified output config.json

```json
{"component":"com.facebook.common.appcomponentfactory.m4a.M4aAppComponentFactory",
 "confFixed":false,"exModules":["tn.amin.mpro2"],"fallback":false,
 "loadOnAll":false,"pkgMasked":false,"prefetches":{}}
```

### CLI flags (from MRVPatcher --help)

- `-o/--output <dir>`, `-f/--force`
- `--modules <pkg...>`  third-party module package names (NOT apk paths)
- `--fix-conf`, `--mask-pkg`, `--fallback`, `-p/--patch`, `--sign-only`
- hidden/internal: `--internal-patch --temp-dir --out-file --key-args`

Notes:
- `--modules` takes PACKAGE names; the patched app loads them from installed
  apps at runtime (so no-root users also install the module APK).
- Default patchable target is `com.facebook.orca` (Messenger); other FB apps
  are sign-only unless `-p/--patch` is passed.
- The hand-patched Messenger uses only `tn.amin.mpro2` (no ChatHeadEnabler).
