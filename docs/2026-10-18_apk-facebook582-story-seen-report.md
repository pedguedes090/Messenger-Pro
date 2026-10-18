# Facebook 582 Story seen blocking — reverse engineering report

> Analysis date: 2026-10-18
> Flavor: null — authorized APK reverse engineering and source compatibility work.
> Scope and authorization: [scope summary](2026-10-18_apk-facebook582-story-seen-scope.md).
> Result status: target resolution/build are verified; MuMu runtime confirms direct-mutation and model/cache suppression for Story seen state. Shopee-linked Reels remained visible without a separate product card in sampled results; the exact product attachment from the user's screenshot was not found.

## Executive summary

Facebook 582 has several Story “seen” paths: a direct `DirectSeenMutation`, a seen-receipts mutation, a feed CSR callback, a local database writer, and a model/cache transaction. The previous receipt hook searched for the GraphQL model name `StorySeenReceiptsSeenTimeUpdateMutation`, which resolves to a constructor rather than the sender. Runtime diagnosis found that `X.1yO.A0A(String)` dispatches `setStoryAsSeen` and `updateSeenState`; the previous release let this model/cache transaction continue even when other seen hooks fired. The new release hooks this path by a narrow schema/signature fingerprint. On MuMu, the new hook logged suppression and the tested Story card remained in the feed with its unseen ring. Shopee-linked Reels sampled on MuMu kept playing and did not show a separate product card, though the exact attachment in the user's screenshot was not found. The clean module build, tests, lint, signature check, GitHub release workflow, and Hugging Face uploads succeeded.

## Target and source changes

| Item | Value |
|---|---|
| APK | `com.facebook.katana_582.0.0.50.54-475417168_minAPI30(arm64-v8a)(560,640dpi)_apkmirror.com.apk` |
| Package / version | `com.facebook.katana` / `582.0.0.50.54` / code `475417168` |
| APK SHA-256 | `e905b3aa93a9f8a42de0e7f38d4156c9e2121552d8217d511ce051ee97ad4f7b` |
| Module output | `mPro-v1.2.20-debug.apk`, code `37`, name `1.2.20` |
| Code commit / release | `5cd78980c451be3daa4615f9e2c5176a0b3ab57f` / [`v1.2.20`](https://github.com/pedguedes090/Messenger-Pro/releases/tag/v1.2.20) |

Changed files: `FacebookHook.java`, `KatanaUnobfuscator.java`, `FacebookStorySeenFingerprint.java`, and its unit tests. The same release also contains the already-authorized Messenger 583 dispatch fix and its regression tests.

## Static evidence

### E-001 — Facebook APK identity

- `source_ref`: supplied APK under `apkdebug/`.
- `repro_command`:

  ```powershell
  $sdk = 'C:\Users\dun\AppData\Local\Android\Sdk'
  $apk = 'F:\Messenger-Pro\apkdebug\com.facebook.katana_582.0.0.50.54-475417168_minAPI30(arm64-v8a)(560,640dpi)_apkmirror.com.apk'
  $aapt = Get-ChildItem "$sdk\build-tools\*\aapt.exe" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
  & $aapt dump badging $apk | Select-Object -First 1
  Get-FileHash -Algorithm SHA256 -LiteralPath $apk
  ```

- `content_hash`: SHA-256 shown in the target table.
- `raw_excerpt`: `package: name='com.facebook.katana' versionCode='475417168' versionName='582.0.0.50.54'`.

### E-002 — Direct Story seen mutation

- `source_ref`: Facebook 582 `classes7.dex`, IDA Pro database opened from the supplied APK.
- `repro_command`: IDA MCP `analyze_function(database=<classes7.dex session>, addr="0x7818ac")`, then analyze `0x781940` and inspect the call xref from `0x7818ec`.
- `raw_excerpt`:
  - `BFm.A00@VLLLLLLLZ`: `void(X.BFm *this, X.cd6 *p0, FbUserSession *p1, String *p2, String *p3, String *p4, Map *p5, Set *p6, boolean p7)`.
  - It invokes `BFm.getRequest@LLLLLZLL`.
  - The builder references `story_ids_list`, `is_story_peek_view`, `bucket_to_story_card_id_filters`, `type=mutation`, and `surface=story_viewer`.
- `content_hash`: inherited from E-001.

### E-003 — Seen-receipts sender versus model constructor

- `source_ref`: Facebook 582 `classes7.dex`, IDA Pro.
- `repro_command`: IDA MCP `analyze_function` on `0x7c3528` and `0xab1414`, then inspect xrefs to `BQU.A0I`.
- `raw_excerpt`:
  - `BQU.A0I@VLLLI` is `static void(X.LnL, X.BQU, String, int)` and references `actor_id`, `time`, `story_id`, and `seen_receipts_unseen_count`; its call sites include `0x7c5cd0`, `0x7c6eee`, and `0x7c80c8`.
  - `CqX._init_@V` at `0xab1414` is the constructor containing `StorySeenReceiptsSeenTimeUpdateMutation`. It is a model constructor, not the sender entry point the old generic resolver tried to hook.
- `content_hash`: inherited from E-001.

### E-004 — APK resolver results and method mapping

- `source_ref`: Dexplore resolver probe against the full supplied APK, plus IDA and targeted JADX inspection.
- `repro_command`: load the APK with `DexFactory.load(apkPath)` and invoke the package-private production resolver helpers in `KatanaUnobfuscator`; query the CSR owner/method by `FeedCSRStorageHandlerImpl` and `setStorySeen`, `void`, one `String` parameter.
- `raw_excerpt`:
  - direct mutation: `X.BFm.A00(X.cd6,FbUserSession,String,String,String,Map,Set,boolean):void`
  - receipt sender: `X.BQU.A0I(X.LnL,X.BQU,String,int):void`
  - local writer: `X.6ku.A03(X.6ku,FbUserSession,X.1AX,String,int,long,long):int`
  - FeedCSR callback: `X.24A.ExO(String):void`
- `content_hash`: inherited from E-001.

### E-005 — Build and tests

- `source_ref`: local Gradle output and Android SDK package/signature inspection.
- `repro_command`:

  ```powershell
  $env:ANDROID_HOME='C:\Users\dun\AppData\Local\Android\Sdk'
  .\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
  ```

- `raw_excerpt`: `BUILD SUCCESSFUL`; unit tests `15`, failures/errors `0`; `lintDebug` passed. `aapt dump badging` reports module version `1.2.20` / code `37`; `apksigner verify --verbose` reports v2 verification true and exit code `0`.
- `content_hash`: module APK SHA-256 `2ef2caacea12bcf3c2490572c8523cafd2beafc401020b4b13c2a055bccd57b8`.

### E-006 — Published artifacts

- `source_ref`: GitHub Actions run `39115851043`, GitHub Release `v1.2.20`, and Hugging Face bucket `meetea/data/patched/`.
- `repro_command`: `gh run view 39115851043 --json status,conclusion,jobs`; `gh release view v1.2.20 --json assets`; `hf buckets list meetea/data --recursive --format json`.
- `raw_excerpt`: all workflow steps succeeded. Release assets contain Facebook 582, Messenger 583, and the local-signed module APK. Bucket entries report the new sizes: Facebook `158536311`, Messenger `168461379`, module `10041718` bytes.
- `limitations`: bucket metadata and CLI upload completion were checked; no download-back digest verification was run for the three new bucket objects.

### E-007 — MuMu Story runtime dispatch

- `source_ref`: MuMu Android 15 (`emulator-5554`), Facebook 582, MPro 1.2.19, and filtered `LSPosed-Bridge` log output. Device properties and MuMu host processes confirmed this was MuMu; no real phone was used.
- `repro_command`:

  ```powershell
  $adb='C:\Users\dun\AppData\Local\Android\Sdk\platform-tools\adb.exe'
  & $adb -s emulator-5554 shell input tap 410 430
  & $adb -s emulator-5554 shell input keyevent 4
  & $adb -s emulator-5554 logcat -d -v time -s LSPosed-Bridge | Select-String 'FacebookHook: suppressed Story seen'
  ```

- `raw_excerpt`:
  - `14:57:59.925 ... FacebookHook: suppressed Story seen via direct mutation`
  - `14:58:41.880`, `.884`, `14:58:42.044`, `.045 ... FacebookHook: suppressed Story seen via local state`
  - Startup log: `FacebookHook: installed 4/4 targeted Story seen hooks`.
- `limitations`: one Story was exercised in this earlier `1.2.19` run. The log did not show `receipt mutation` or `FeedCSR callback`, and no second account was used to inspect the sender-side viewer list. Facebook temporarily foregrounded `LoginActivity`; a later launch returned the MuMu session to the feed.

### E-008 — Model/cache seen transaction and MuMu suppression

- `source_ref`: Facebook 582 `classes2.dex`, IDA Pro at `_1yO.A0A@VL` (`0x731ea0`), plus MuMu log output from MPro 1.2.20.
- `repro_command`: inspect IDA function `0x731ea0` and its xrefs; then open and close a fresh Story under MPro 1.2.20 and filter `LSPosed-Bridge` for `Story model seen-state`.
- `raw_excerpt`:
  - IDA prototype: `void(X.1yO *this, java.lang.String *p0)`; the body references `_updateSeenState`, `primaryKey`, `setStoryAsSeen`, `setStorySeen`, and `updateSeenState`.
  - MuMu installed `X.1yO#A0A(java.lang.String)->void` as the fifth Story hook (`5/5`). At `16:31:45.085` the direct mutation was suppressed; at `16:31:46.000`, `.003`, and `.010` the model/cache seen-state transaction was suppressed.
  - After closing the tested Story, its card remained in the tray with a blue unseen ring.
- `content_hash`: Facebook APK hash inherited from E-001; tested module hash is E-005.
- `limitations`: one MuMu Story path confirmed; the Facebook server's separate viewer list and the `BQU.A0I` receipt path were not observed.

### E-009 — MuMu Reels commerce-overlay sample

- `source_ref`: MuMu Android 15, Facebook 582 patched APK from release `v1.2.20`, Reels search results and immersive viewer screenshots.
- `repro_command`: open Reels search, search `Shopee`, open a result whose caption has a `Mua ngay` Shopee link, and inspect the immersive viewer without tapping the link or shop controls.
- `raw_excerpt`: two Shopee-linked Reel results opened in the viewer. The video and caption link remained visible; no separate product attachment card appeared over the video. Thirteen ordinary Reels also remained viewable during feed sampling.
- `limitations`: the sampled Shopee-linked Reel was not proven to carry the same structured `GroupCommerceProductItem` attachment as the user's screenshot. Static attachment-path evidence remains the basis for that specific product-card filter; this runtime sample only confirms the Reel and its caption were not removed.

## Findings

### F-001 — The old receipt hook resolved a model constructor, not a sender

- `severity`: n/a_re
- `category`: design
- `status`: validated (static)
- `evidence_ids`: [E-003, E-004]
- `location`: `app/src/main/java/tn/amin/mpro2/hook/unobfuscation/KatanaUnobfuscator.java`
- `impact`: the generic anchor `StorySeenReceiptsSeenTimeUpdateMutation` points at `CqX`'s constructor; it does not suppress the receipt mutation sent by `BQU.A0I`.
- `confidence`: high for the static mapping; runtime behavior remains unverified.
- `remediation`: resolve and hook `BQU.A0I` using the four stable input fields and its void/four-parameter signature.

### F-002 — The previous release missed a model/cache seen-state event

- `severity`: n/a_re
- `category`: design
- `status`: validated (static call flow and MuMu observer log)
- `evidence_ids`: [E-008]
- `location`: Facebook 582 `X.1yO.A0A(String)` at classes2.dex `0x731ea0`
- `impact`: the method dispatches `setStoryAsSeen`, `setStorySeen`, and `updateSeenState`. The previous four hooks blocked the direct mutation and some local persistence paths, but did not stop this model/cache transaction from changing the Story tray state.
- `confidence`: high that the transaction was called in a MuMu Story run; the exact user-reported “seen” visual state was not reproduced with the previous release in the isolated test.
- `remediation`: add a narrow fifth hook using all stable event strings and the `void(String)` signature.

### F-003 — Story seen suppression now has five narrow, fail-open targets

- `severity`: n/a_re
- `category`: reverse_algo
- `status`: validated on MuMu for direct-mutation and model/cache suppression; receipt-sender behavior not observed
- `evidence_ids`: [E-002, E-003, E-004, E-005, E-007, E-008]
- `location`: `app/src/main/java/tn/amin/mpro2/FacebookHook.java` and `KatanaUnobfuscator.java`
- `impact`: the hook set targets the direct mutation, receipt sender, CSR state callback, local database writer, and model/cache event `X.1yO.A0A`. The former hook that returned null from every declared method of a helper class was removed to avoid damaging unrelated Story viewer behavior.
- `confidence`: high that direct and model/cache suppression fired in the MuMu run; server-side receipt-list outcome and alternate receipt sender behavior remain unverified.
- `remediation`: on resolver mismatch the hook is skipped, a bounded diagnostic is logged, and Facebook remains fail-open.

## Call path

### P-001 — Facebook Story seen write

- `path_type`: callflow
- `start`: user opens a Facebook Story.
- `goal`: prevent the Story seen receipt and local seen state from being committed.
- `steps`:
  1. Story viewer prepares `DirectSeenMutation` through `BFm.A00` → `BFm.getRequest` — E-002, F-002.
  2. Receipt update is assembled and submitted through `BQU.A0I` — E-003, F-001, F-002.
  3. Feed CSR callback `24A.ExO(String)` and local writer `6ku.A03(...):int` are separately resolved to prevent secondary local state updates — E-004, F-002.
  4. Model/cache handler `1yO.A0A(String)` emits `setStoryAsSeen` and `updateSeenState`; the new fingerprint resolves only the void/String event transaction — E-008, F-003.
  5. On MuMu, opening/closing one Story logged suppression through the direct mutation and model/cache hooks; the Story card retained its blue unseen ring — E-008, F-003.
  6. `FacebookHook` installs only methods that pass the schema and signature checks; missing targets are skipped with diagnostics — E-005, F-003.
- `residual_risks`: `BQU.A0I` and `24A.ExO` were installed but did not log invocation in the observed Story run; the server-side viewer list was not observed. A future Facebook schema may change the field set or sender method.

## Timeline and runtime limits

- 2026-10-18: inspected repository Story hooks, the supplied Facebook 582 APK, and prior MuMu diagnostics.
- 2026-10-18: used IDA xrefs/signatures and DEX resolver probes to locate `BFm.A00`, `BQU.A0I`, `6ku.A03`, and `24A.ExO`.
- 2026-10-18: implemented scoped selectors, removed the all-method helper hook, added bounded diagnostics and fingerprint tests; clean Gradle build, tests, and lint passed.
- 2026-10-18: pushed commit `859024d`, created release `v1.2.19`, and uploaded the patched APKs and module to Hugging Face.
- 2026-10-18: installed MPro 1.2.19 on MuMu, confirmed `4/4` Story hooks, opened one Story and observed direct-mutation plus local-state suppression logs. Facebook returned to `LoginActivity` after closing; Reels runtime verification is pending feed access.
- 2026-10-18: IDA and a pass-through MuMu observer traced `X.1yO.A0A(String)` to `setStoryAsSeen` / `updateSeenState`; the observer saw this event after Story view while the previous release let it continue.
- 2026-10-18: added the fifth schema/signature hook, bumped to MPro 1.2.20, and verified on MuMu that the direct mutation and model/cache event were suppressed while the tested Story card remained blue. Release `v1.2.20` and the new module/APKs were published.
- 2026-10-18: sampled Shopee-linked Reels on MuMu; the Reel and its caption stayed visible and no separate product attachment card appeared in those samples.

MuMu runtime confirms direct-mutation and model/cache suppression for tested Story views. This does not establish the sender-side viewer-list outcome or every Story path. The exact product-tagged Reel from the user's screenshot was not found in the sampled feed, so its commerce card is supported by static attachment-path verification rather than a matching runtime reproduction. No message body, credentials, or account data were logged.
