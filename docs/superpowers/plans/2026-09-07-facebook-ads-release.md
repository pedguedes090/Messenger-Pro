# Facebook 576 Ads Patch and Release Implementation Plan

> **For agentic workers:** Execute the tasks inline with verification checkpoints.

**Goal:** Extend the Facebook 576 static patch pipeline so feed, async-feed, story-tail, video-ad-break, and Reels ad-fetch seams are patched in their actual DEX files, then publish freshly built APKs through GitHub Actions and a release tag.

**Architecture:** A single dexlib2 patcher will match only the verified 576 class/method descriptors from the local DEX map. The shell pipeline will patch the corresponding `classes*.dex` files before de-superpack integration, while preserving the clean pre-MRV APK and the MRV-signed APK outputs.

**Tech Stack:** Bash, Python 3, Java 17, dexlib2 2.5.2, Gradle/Android, GitHub Actions.

**Spec:** `work/ADS_BLOCK_576_REPORT.md` and `pipeline/PIPELINE.md`.

## Global Constraints

- Target Facebook package: `com.facebook.katana`, build 576 / versionCode `474227017`.
- Drop GraphQL feed categories `A0K`, `A0I`, `A0C`, and `A0D` at `LX/1lJ;->addNewEdgeToCollection`.
- Patch the verified route-A/B methods in `classes.dex`, the redex runnable in `classes3.dex`, and route-D/E methods in `classes10.dex`.
- Keep output names `Facebook-576-clean.apk` and `Facebook-v576.0.0.42.73.apk` compatible with the existing release workflow.

---

### Task 1: Add verified multi-route dex patcher

**Files:**
- Modify: `pipeline/fb/patch_dex/PatchAds.java`
- Modify: `pipeline/fb/patch_dex/patch_dex.sh`

- [ ] Add narrow class/method matching for routes A-E and return-type-correct replacements.
- [ ] Preserve the existing category-prefix patch for `LX/1lJ;` and all four promotion-family enum fields.
- [ ] Compile and run the patcher against all 18 local DEX files, asserting expected hit counts.

### Task 2: Wire patched DEX files into the single-APK pipeline

**Files:**
- Modify: `pipeline/fb/run.sh`
- Modify: `pipeline/fb/superpack/desuper.py`
- Modify: `pipeline/fb/superpack/integrate_ads.py`
- Modify: `pipeline/PIPELINE.md`

- [ ] Patch `classes.dex`, `classes3.dex`, `classes5.dex`, and `classes10.dex` from the corresponding input files.
- [ ] Inject each patched DEX into its matching `secondary-N.dex` asset and refresh metadata hashes.
- [ ] Keep the clean APK and MRV-signed APK output stages intact.
- [ ] Run a local static pipeline smoke test against the checked-in Facebook 576 DEX set and verify patched methods in output DEX.

### Task 3: Build, commit, push, and release

**Files:**
- Modify: Git history on `main`.

- [ ] Run baseline and modified Gradle tests/builds.
- [ ] Commit the pipeline changes and push `main` to `origin`.
- [ ] Create and push a new release tag, then monitor both GitHub Actions workflows.
- [ ] Verify the release assets include the module APK, clean Facebook APK, MRV Facebook APK, patched Messenger APK, and ChatHeadEnabler APK.

