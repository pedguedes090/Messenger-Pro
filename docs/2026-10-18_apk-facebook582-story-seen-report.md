# Facebook 582 Story seen blocking — reverse engineering report

> Analysis date: 2026-10-18
> Flavor: null — authorized APK reverse engineering and source compatibility work.
> Scope and authorization: [scope summary](2026-10-18_apk-facebook582-story-seen-scope.md).
> Result status: target resolution/build are verified; one MuMu Story runtime exercised the direct mutation and local-state blockers. The receipt-sender callback and Reels product overlay still need runtime observation.

## Executive summary

Facebook 582 has more than one Story “seen” path: a direct `DirectSeenMutation`, a seen-receipts mutation, a feed CSR callback, and a local state writer. The previous receipt hook searched for the GraphQL model name `StorySeenReceiptsSeenTimeUpdateMutation`, which resolves to a constructor and is not loadable as a reflective `Method`; the actual sender is `X.BQU.A0I`. The implementation now resolves the sender by its stable input-field set and validates the method signature, while also tightening the direct mutation and callback selectors. On MuMu, LSPosed installed all four hooks; opening and closing one Story produced suppression logs for the direct mutation and local state writer. The clean module build, tests, lint, signature check, GitHub release workflow, and Hugging Face uploads also succeeded. The receipt-sender callback and Reels product overlay still need runtime observation.

## Target and source changes

| Item | Value |
|---|---|
| APK | `com.facebook.katana_582.0.0.50.54-475417168_minAPI30(arm64-v8a)(560,640dpi)_apkmirror.com.apk` |
| Package / version | `com.facebook.katana` / `582.0.0.50.54` / code `475417168` |
| APK SHA-256 | `e905b3aa93a9f8a42de0e7f38d4156c9e2121552d8217d511ce051ee97ad4f7b` |
| Module output | `mPro-v1.2.19-debug.apk`, code `36`, name `1.2.19` |
| Main commit / release | `859024d742098cda8db4e34b0b576225d3fe56ea` / [`v1.2.19`](https://github.com/pedguedes090/Messenger-Pro/releases/tag/v1.2.19) |

Changed files: `FacebookHook.java`, `KatanaUnobfuscator.java`, `FacebookStorySeenFingerprint.java`, and its unit test. The same release also contains the already-authorized Messenger 583 dispatch fix and its regression tests.

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

- `raw_excerpt`: `BUILD SUCCESSFUL`; unit tests `15`, failures/errors `0`; `lintDebug` passed. `aapt dump badging` reports module version `1.2.19` / code `36`; `apksigner verify --verbose` reports v2 verification true and exit code `0`.
- `content_hash`: module APK SHA-256 `feafba0eb5e7f732ffc03135c15bd9aa4588947d0cd6aa4e21a61425e3b96b8c`.

### E-006 — Published artifacts

- `source_ref`: GitHub Actions run `39108787650`, GitHub Release `v1.2.19`, and Hugging Face bucket `meetea/data/patched/`.
- `repro_command`: `gh run view 39108787650 --json status,conclusion,jobs`; `gh release view v1.2.19 --json assets`; `hf buckets list meetea/data --recursive --format json`.
- `raw_excerpt`: all workflow steps succeeded. Release assets contain Facebook 582, Messenger 583, and the local-signed module APK. Bucket entries report the new sizes: Facebook `158536311`, Messenger `168461379`, module `10040746` bytes.
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
- `limitations`: one Story was exercised. The log did not show `receipt mutation` or `FeedCSR callback`, and no second account was used to inspect the sender-side viewer list. After closing the Story the foreground activity became `LoginActivity`, which currently blocks the Reels test until the feed is restored.

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

### F-002 — Story seen suppression now has four narrow, fail-open targets

- `severity`: n/a_re
- `category`: reverse_algo
- `status`: validated on MuMu for one Story's direct mutation and local-state paths; receipt-sender behavior not observed
- `evidence_ids`: [E-002, E-003, E-004, E-005, E-007]
- `location`: `app/src/main/java/tn/amin/mpro2/FacebookHook.java` and `KatanaUnobfuscator.java`
- `impact`: hooks suppress the direct mutation, receipt sender, CSR state callback, and local seen-state writer. The former hook that returned null from every declared method of a helper class was removed to avoid damaging unrelated Story viewer behavior.
- `confidence`: high that the direct mutation and local-state hooks fired in the MuMu run; server-side receipt-list outcome and alternate receipt sender behavior remain unverified.
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
  4. On MuMu, opening/closing one Story logged suppression through the direct mutation and local state hooks — E-007, F-002.
  5. `FacebookHook` installs only methods that pass the schema and signature checks; missing targets are skipped with diagnostics — E-005, F-002.
- `residual_risks`: `BQU.A0I` and `24A.ExO` were installed but did not log invocation in this single Story run; the server-side viewer list was not observed. A future Facebook schema may change the field set or sender method.

## Timeline and runtime limits

- 2026-10-18: inspected repository Story hooks, the supplied Facebook 582 APK, and prior MuMu diagnostics.
- 2026-10-18: used IDA xrefs/signatures and DEX resolver probes to locate `BFm.A00`, `BQU.A0I`, `6ku.A03`, and `24A.ExO`.
- 2026-10-18: implemented scoped selectors, removed the all-method helper hook, added bounded diagnostics and fingerprint tests; clean Gradle build, tests, and lint passed.
- 2026-10-18: pushed commit `859024d`, created release `v1.2.19`, and uploaded the patched APKs and module to Hugging Face.
- 2026-10-18: installed MPro 1.2.19 on MuMu, confirmed `4/4` Story hooks, opened one Story and observed direct-mutation plus local-state suppression logs. Facebook returned to `LoginActivity` after closing; Reels runtime verification is pending feed access.

One MuMu Story exercised the direct mutation and local-state blockers. This does not establish the sender-side viewer-list outcome or every Story path. Affiliate/product Reels were not tested because Facebook is currently foregrounded in `LoginActivity`; restoring the logged-in feed is required. No message body, credentials, or account data were logged.
