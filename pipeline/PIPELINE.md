# MessengerPro pipeline

Automates building and patching **Messenger 576** and **Facebook 576** so a
release ships three pre-patched APKs:

| # | APK | Contents | Who needs it |
|---|-----|----------|--------------|
| 1 | **Messenger patched** | `com.facebook.orca` 576 with `tn.amin.mpro2` (MessengerPro) + `app.neonorbit.chatheadenabler` (ChatHeadEnabler) injected and re-signed with the fixed MRV key | no-root users |
| 2 | **MessengerPro module** | the LSPosed module APK (`tn.amin.mpro2`) | rooted (LSPosed) users |
| 3 | **Facebook patched** | `com.facebook.katana` 576 single APK with story-seen block + feed-ads block | no-root users |

Rooted (LSPosed) users install only the module (#2); no-root users install all
three. Messenger and Facebook are signed with the same key so account login
state is shared.

---

## Targets (versions to download)

The CI cannot download these from APKMirror (Cloudflare blocks the runner), so
they must be downloaded manually and hosted on your own file host. The workflow
pulls them at build time from `FB_BASE_URL` / `FB_SPLIT_URL` /
`MESSENGER_URL` repository secrets.

| App | Package | Version | versionCode |
|-----|---------|---------|-------------|
| Messenger | `com.facebook.orca` | 576.0.0.47.92 | **345212666** |
| Facebook | `com.facebook.katana` | 576.0.0.42.73 | **474227017** |

For Facebook, use the **arm64** `base.apk` + `split_config.xxhdpi.apk` (or the
`.apks` bundle and unpack it with the steps below).

---

## Directory layout

```
pipeline/
  PIPELINE.md          # this file
  fb/
    run.sh             # end-to-end Facebook patch driver
    patch_dex/
      PatchDex.java    # no-op LX/BII;.A00  (block story-seen)
      PatchAds.java    # verified 576 feed/async/video/Reels ad seams
      patch_dex.sh     # compile + run the dexlib2 patchers
    superpack/
      desuper.py       # strip .spo superpack, inject 18 secondary-N.dex
      patch_manifest.py# clear requiredSplitTypes in binary manifest
      integrate_ads.py # swap ads-patched secondary-1.dex into merged APK
    merge/
      MergeApk.java    # ARSCLib binary resources.arsc merge
      merge.sh         # fetch ARSCLib + run the merge
  messenger/
    README.md          # MRV / LSPatch module-injection (Phase 3)
```

---

## Facebook patch (fully automated on a host)

The Facebook `.apks` is a **superpack** bundle: the real DEX are compressed in
`assets/secondary-program-dex-jars/store-0.dex.spo` (and `assets/lib/libs.spo`),
decompressed at runtime by `libsuperpack-jni.so` into
`/data/user/0/com.facebook.katana/dex/z-*.zip` (18 DEX:
`classes.dex` .. `classes18.dex`).

The pipeline sidesteps superpack with a **de-superpack fallback**: the bootstrap
`classes.dex` (3.5 MB, the multidex loader) reads
`assets/secondary-program-dex-jars/metadata.txt`. Removing the
`.superpack_files 1` / `.superpack_extension spo` pragmas makes it read plain
`secondary-N.dex` assets directly.

```
export FB_BASE=/path/base.apk
export FB_SPLIT=/path/split_config.xxhdpi.apk
export FB_DEXDIR=/path/18-dex
export BUILD_TOOLS=$ANDROID_HOME/build-tools/36.0.0
bash pipeline/fb/run.sh
```

Steps performed by `run.sh`:

1. **Patch story-seen** - no-op `LX/BII;.A00` (void, 8 params) in
   `secondary-5.dex` (the method Facebook calls to persist story-seen).
2. **Patch feed + async-feed ads** - patch `secondary-1.dex` (`classes.dex`):
   `LX/1lJ;.addNewEdgeToCollection(...)->Z` drops `A0K` (SPONSORED), `A0I`
   (PROMOTION), `A0C` (FRIENDLY_FEED_PROMOTION), and `A0D`
   (HIGH_VALUE_PROMOTION); `LX/3Le;.Di7(...)->ImmutableList` returns an empty
   list; `LX/3Le;.A0D(...)->LX/6mV;` returns null; `LX/1mb;.A09(...)->V`
   returns immediately; `GraphQLFBMultiAdsFeedUnit.A00()` returns null;
   `FeedSponsoredStoryHolder.A0R()` returns null; and the FBShorts/VideoHome
   sponsored-pool/fetch paths return before vending or requesting an ad.
3. **Patch async runnable** - patch the class carrying the verified
   `__redex_internal_original_name` anchor
   `MainFeedCSRDataLoaderImpl$maybeDoAsyncAdsTailLoad$1` in `secondary-3.dex`.
4. **Patch video/Reels ads** - patch `LX/OF5;.A05(...)->V` and
   `LX/OJB;.A03(...)->V` in `secondary-10.dex` so video-ad-break and
   banner/video ad fetches return before requesting ads.
5. **De-superpack** - drop `store-0.dex.spo`, rewrite `metadata.txt`, inject
   the 18 `secondary-N.dex` assets (secondary-1/3/5/10/16 contain the patched
   variants). Mapping:
   `secondary-1 <- classes.dex`, `secondary-N <- classesN.dex` (N = 2..18).
6. **Clear split requirement** - empty the `requiredSplitTypes=base__density`
   string in the binary `AndroidManifest.xml` so the single APK installs
   without `INSTALL_FAILED_MISSING_SPLIT`.
7. **Merge splits** - binary `resources.arsc` merge (base + xxhdpi) via
   REAndroid ARSCLib. apktool/aapt2 cannot recompile Facebook obfuscated
   resources, so ARSCLib merges the compiled tables directly.
8. **MRV patch + sign** - `java -jar MRVPatcher-5.8.1.jar merged.apk -p -o out -f`
   (VERIFIED: injects the LSPatch loader with empty `exModules` and re-signs
   with the fixed MRV key, so Facebook shares Messenger's signature). Output
   matches the hand-patched Facebook in release v1.2.9 byte-for-byte in
   config + cert.

Dependencies (fetched automatically): dexlib2 2.5.2, util 2.5.2,
guava 27.1-android, jsr305 3.0.2 (Maven Central); ARSCLib V1.4.0 (JitPack).

### Patch anchors (verified on 576)

- **Seen**: `LX/BII;.A00` - `secondary-5.dex` (`classes5.dex`, 10,057,776 B).
- **Ads**: the verified `LX/1lJ;`, `LX/3Le;`, `LX/1mb;`, `LX/OF5;`,
  `LX/OJB;`, `LX/21f;`, `LX/4r3;`, `LX/6F5;`, `LX/1kU;`, and `LX/iR6;`
  seams from `ADS_BLOCK_576_REPORT.md` plus the follow-up DEX map. They are
  patched in `secondary-1.dex`, `secondary-3.dex`, `secondary-10.dex`, and
  `secondary-16.dex`, and the
  `metadata.txt` SHA-1 is regenerated for every replacement.
- **Register layout**: for instance methods with `regs > insSize`, parameters
  live in the HIGH register group (`v[regs-insSize] .. v[regs-1]`); e.g.
  `addNewEdgeToCollection` regs=30/insSize=4 => v26=this, v27=builder,
  v28=edge, v29=enum. `v28` cannot be encoded by `35c` (max reg 15), so the
  prefix uses `invoke-virtual/range` (`3rc`).

---

## Messenger patch (module injection - VERIFIED)

Injecting the module into Messenger uses **MRVPatcher** (an LSPatch fork) as a
runnable JAR. Its `MRVPatcher.patch(args)` == `LSPatch.main(args)`, and it
embeds the fixed MRV private keystore (`assets/mrvkey.jks`, pass `123456`).

```
java -jar MRVPatcher-5.8.1.jar Messenger-576.apk -o out -f --modules tn.amin.mpro2
```

Verified against the hand-patched Messenger (release v1.2.9): same MRV cert
(SHA-256 `217d4345ad36c9dce82da6ad5494b7c68a84e1077893fe0eb9f14c428d3e259c`)
and same config.json (`exModules:["tn.amin.mpro2"]`).

See `pipeline/messenger/README.md` for the full CLI reference. The module APK
is built with `./gradlew assembleDebug`.

---

## Known limitations / deferred work

- **Superpack decompressor (Phase 4)** - `run.sh` currently expects the 18
  decompressed DEX to be provided (`FB_DEXDIR`). A native decompressor for
  `store-0.dex.spo` (`libsuperpack-jni.so`) would make the pipeline fully
  end-to-end.
- **Reel video download was abandoned** - the reels 3-dot menu is a
  server-driven Bloks actionsheet with no native resource-id builder; no
  reliable native anchor exists for a download button.
- **x86 emulators** crash Facebook JNI (arm64 libs under translation); smoke
  test on a real arm64 device.
