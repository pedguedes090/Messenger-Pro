# Facebook 582 / Messenger 583 compatibility report

> Date: 2026-10-18
> Scope: the two APKs named in the user request and the local `F:\Messenger-Pro` repository. No external target was contacted.
> Flavor: null (authorized APK reverse engineering; no malware or vulnerability claim).

## Executive summary

The repository now accepts Messenger Orca build `346611418` and recommends `583.0.0.29.95`. Facebook 582 static ad patching now uses model/category and render-path fingerprints instead of the old 576 obfuscated descriptors. It filters paid/sponsored feed edges, commerce-product Reels, the sponsored Reels pool, VideoHome ad requests, and the separate Story Ads fetch path. The generated DEX patch test passes; IDA inspection of the emitted `classes2.dex` confirms the feed filter’s drop and fail-open branches. The clean Android build, unit tests, and lint pass.

MuMu Android 15 was available for launch smoke checks. Facebook 582 and Messenger 583 both reached their login surfaces with LSPosed loading the module; Messenger completed module unobfuscation without a crash, while Facebook remained alive after launching the patched APK. The emulator had no logged-in account, so feed/Reels ad-removal behavior and incoming-message dispatch could not be exercised. The Messenger 583 receive action is unresolved; the hook stays disabled by default for that build until `API_NOTIFICATION` is verified.

## Scope and artifacts

| Artifact | Identity |
|---|---|
| Facebook | `com.facebook.katana`, `582.0.0.50.54`, version code `475417168`; application `com.facebook.katana.app.FacebookApplication` |
| Messenger | `com.facebook.orca`, `583.0.0.29.95`, version code `346611418`; application `com.facebook.messenger.app.MessengerApplication`; launcher `com.facebook.messenger.neue.MainActivity` |

APK SHA-256: Facebook `e905b3aa93a9f8a42de0e7f38d4156c9e2121552d8217d511ce051ee97ad4f7b`; Messenger `0d45dbea76f5b425047575634c21b50297684dbf596e9fc7cf9690584abd20a1`.

## Evidence

### E-001 — Package and version identity

- `source_ref`: manifest and DEX tables extracted from the supplied APKs; IDA Pro DEX sessions.
- `repro_command`: inspect each APK manifest, then enumerate `classes*.dex` from the archive.
- `raw_excerpt`: package/version values in the table above; Facebook contains 21 DEX files and Messenger 15.
- `content_hash`: the SHA-256 values above.

### E-002 — Facebook feed insertion and category model

- `source_ref`: Facebook 582 `classes2.dex`, IDA Pro.
- `repro_command`: inspect `_1xD.addNewEdgeToCollection` at `0x71ee5c`, then follow its calls to `GraphQLFeedUnitEdge.B8S()` at `0x788888` and `A03()` at `0x78866c`.
- `raw_excerpt`: `addNewEdgeToCollection(ImmutableList.Builder, GraphQLFeedUnitEdge, LX/1aL;) -> boolean`; `B8S()` returns `GraphQLFeedStoryCategory`; `A03()` returns the cached feed-unit model. The enum initializer maps stable names including `ORGANIC`, `PROMOTION`, `SHOWCASE`, `SPONSORED`, `FIXED_POSITION`, and `HIGH_VALUE_PROMOTION`.

### E-003 — Product attachment path used by commerce/Reels rendering

- `source_ref`: Facebook 582 `classes2.dex` and `classes3.dex`, DEX instruction inspection and IDA xrefs.
- `repro_command`: inspect `FeedStoryGroupCommerceAttachmentPlugin.A00(GraphQLStory)` and follow its static call to `LX/3mA.A01(GraphQLStory) -> GraphQLStoryAttachment`; inspect the referenced `LX/2f4.A0E(GraphQLStoryAttachment) -> boolean` implementation.
- `raw_excerpt`: the render plugin requests a story attachment; `A01` returns the first attachment accepted by `A0E`; `A0E` checks the attachment’s tree type for `GroupCommerceProductItem`.
- `content_hash`: inherited from E-001 APK hashes.

### E-004 — Paid ads, Reels pool, and Story Ads fingerprints

- `source_ref`: Facebook 582 `classes2.dex`, `classes4.dex`, `classes7.dex`, and `classes13.dex`; IDA plus DEX string/signature scan.
- `repro_command`: run `pipeline/fb/test_ads_patch_582.ps1` against the extracted target DEX directory.
- `raw_excerpt`:
  - `LX/28m.A0I(LX/3uR;, LX/28m;, int) -> GraphQLFeedUnitEdge` contains `get_top_valid_ad` and its end marker.
  - `LX/5J5.A0N(LX/6aY;, LX/3d5;, List, int) -> LX/5j9;` is the pool method containing `fb_shorts_native_in_feed_unit`.
  - `LX/Uvf.AuM(LX/Vgr;) -> void` contains `VideoHomeDataFetcher.fetchAds`, `ad_ids`, and `request_type=TRIGGERED_BY_AD`.
  - `LX/B9W.A05(ImmutableList, Boolean, Boolean, int) -> void` contains `AdsPaginatingNetworkAdBucketFetcher`; IDA at `0x74db30` shows the story-ad fetcher is created and called from this method.

### E-005 — Patch output

- `source_ref`: output DEX files from `test_ads_patch_582.ps1`.
- `repro_command`:

  ```powershell
  & .\pipeline\fb\test_ads_patch_582.ps1 -DexRoot 'C:\Users\dun\AppData\Local\Temp\mpro-582-583-dex-analysis\katana'
  ```

- `raw_excerpt`: `FACEBOOK_582_ADS_PATCH_TEST=PASS`; resolved and patched counts: classes2 feed edge `1`, sponsored selector `1`; classes4 Reels pool `1`; classes7 Story Ads fetch `1`; classes13 VideoHome ad request `1`. The edge filter’s branch targets were validated before writing.

### E-006 — Messenger native dispatch evidence

- `source_ref`: Messenger 583 `classes2.dex` / `classes4.dex`, IDA session `classes2.dex`, and DEX callsite scan.
- `repro_command`: enumerate `MailboxCoreJNI` / `MailboxSDKJNI` `dispatchV*` signatures with `int` at argument 0, `long` at argument 2, and reference payload slots at 3, 6, and 8; inspect callers in IDA.
- `raw_excerpt`: matching native declarations remain in Core and SDK, but the static receive action is not identified. `LX/3v1.onCompletion` at `0x9f1618` dispatches action `24`; its caller is in `MsysMutationService.sendMessageInternal` (`LX/2BR.A06`, `0x853934`), so action 24 is not used as a receive-event substitute. The old action 29 was not verified for build 583.

### E-007 — Build and test results

- `source_ref`: Gradle task output and test/lint reports.
- `repro_command`:

  ```powershell
  $env:ANDROID_HOME='C:\Users\dun\AppData\Local\Android\Sdk'
  $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
  .\gradlew.bat clean assembleDebug testDebugUnitTest lint
  ```

- `raw_excerpt`: `BUILD SUCCESSFUL`; unit tests report 3 tests, 0 failures; lint XML reports 0 issues. Module version is `1.2.13` / version code `34`; debug APK: `app/build/outputs/apk/debug/mPro-v1.2.13-debug.apk`.

### E-008 — MuMu launch smoke checks

- `source_ref`: user-provided MuMu emulator `emulator-5554`, Android 15/API 35; ADB process/activity state and LSPosed bridge logs.
- `repro_command`: launch each package with LSPosed scope enabled, then inspect `adb logcat`, process IDs, and the resumed activity.
- `raw_excerpt`:
  - Facebook process `com.facebook.katana` stayed alive at `com.facebook.account.login.activity.SimpleLoginActivity`; LSPosed logged `Loading legacy module tn.amin.mpro2` and `Loading class tn.amin.mpro2.MainHook`. `FacebookHook` logged APK DEX scanning, but no completion diagnostic was observed during the smoke-check window.
  - Messenger process `com.facebook.orca` stayed alive in its login flow; LSPosed logged `MainHook` and `Successfully unobfuscated all components`. `MessageReceivedHook` logged that `API_NOTIFICATION` is unverified for build `346611418` and left the receive hook disabled.
  - No `FATAL EXCEPTION` was present in the captured app logs. The emulator was not authenticated, so it did not display feed/Reels content or permit an incoming-message test.
- `limitations`: launch-only checks; no account, feed, Reels, toggle, message receive, or ad-removal behavioral test.

### E-009 — Installable build artifacts

- `source_ref`: fresh Gradle outputs and the repacked Facebook APK produced by `pipeline/fb/build_582_apk.ps1`.
- `raw_excerpt`:
  - Module: `app/build/outputs/apk/debug/mPro-v1.2.13-debug.apk`, SHA-256 `2cb6dd79511a25d9d5bc2194b7d0d084248e00e52a0bf7cce66652801917e5b8`.
  - Facebook 582 patched APK: `app/build/outputs/fb/Facebook-582-patched.apk`, SHA-256 `d5506b6fc8a5ab77d1e9dc8150bf58b234a8ea74b48b20b26e4dbb574408f0ff`; 156,557,416 bytes; `apksigner verify` passed with APK Signature Scheme v3.
  - Both the module and patched Facebook APK installed successfully on MuMu (`adb install -r` / packaging script `-Install`).
- `limitations`: Facebook APK is signed with the local Android debug key, so it cannot update an official-signature install. The Messenger APK re-signed for the emulator is test-only and is not included as a release artifact.

### E-010 — Patched Facebook feed-filter bytecode

- `source_ref`: generated Facebook 582 `classes2.dex` at `app/build/outputs/fb/Facebook-582-patched.apk`; extracted DEX inspected with IDA session `a464e5a6`.
- `repro_command`: open the patched `classes2.dex` in IDA and analyze `_1xD.addNewEdgeToCollection@ZLLL` at `0x693888`.
- `raw_excerpt`:
  - IDA prototype: `boolean(X.1xD *this, ImmutableList$Builder *p0, GraphQLFeedUnitEdge *p1, X.1aL *p2)`.
  - The emitted prefix reads `GraphQLFeedUnitEdge.A03()` and `B8S()`. Null edge, feed-unit, or category branches jump to `0x693940`, the original method body.
  - `GraphQLFBMultiAdsFeedUnit` and enum names `SPONSORED`, `PROMOTION`, `SHOWCASE`, `FIXED_POSITION`, and `HIGH_VALUE_PROMOTION` branch to `0x69393C`, which returns `false` before the builder is mutated.
  - The `FB_SHORTS` branch requires `GraphQLStory`, calls `LX/3mA.A01(GraphQLStory)`, and returns `false` only when the commerce attachment result is non-null. Other Shorts and other categories continue through the original body.
- `limitations`: bytecode/control-flow verification is static. A logged-in feed/Reels session is still required to prove Facebook’s live server data reaches these model paths.

### E-011 — Messenger 583 notification model is not a verified JNI action

- `source_ref`: Messenger 583 `classes2.dex` and `classes4.dex`, IDA sessions `56552941` and `666cbf4d`.
- `repro_command`: enumerate `MailboxCoreJNI` / `MailboxSDKJNI` method descriptors and inspect `NewMessageNotification` and `onMessageReceived` methods in IDA.
- `raw_excerpt`:
  - `NewMessageNotification._init_` at classes4.dex `0x5ce33c` has prototype `void(NewMessageNotification, MessengerAccountType, Message, ThreadSummary, ..., flags...)`; it stores a `Message` and thread key as fields. This confirms the Android notification model, not a native dispatch action ID.
  - `_80P.onMessageReceived` at classes4.dex `0x559780` takes `com.facebook.rsys.transport.gen.SignalingMessage` and records a `MessageReceiver.onMessageReceived` metric. It is a Rsys signaling callback, not evidence for a Mailbox chat-message action.
  - The current DEX method catalogs contain many overloaded native `dispatchV*` declarations, but static references did not identify a receive action and payload layout that can safely configure `API_NOTIFICATION`.
- `conclusion`: keep `MessageReceivedHook` disabled for build `346611418` unless an authorized runtime trace or a stronger static call path identifies the action and argument layout.

## Findings

### F-001 — Facebook 582 has a structural feed/Reels/Story Ads patch path

- `severity`: n/a_re
- `category`: reverse_algo
- `status`: candidate (static patch verified; runtime behavior remains untested)
- `evidence_ids`: [E-002, E-003, E-004, E-005]
- `location`: `pipeline/fb/patch_dex/PatchAds.java`
- `confidence`: high for target resolution and emitted DEX structure; medium for behavior in a running Facebook process.
- `impact`: paid categories and `GraphQLFBMultiAdsFeedUnit` return `false` from the insertion seam; an `FB_SHORTS` `GraphQLStory` is dropped only when the commerce attachment helper returns a `GroupCommerceProductItem`. Reels pool and paid video/story fetch seams are separately disabled.
- `repro_steps`: run the command in E-005. Missing or ambiguous fingerprints resolve to no patch and are printed in the resolver diagnostics.
- `remediation`: none; this report documents the source changes.

The product-Reel condition does not scan captions. A Reel that only mentions a product remains. A product-tagged Reel is treated as commerce content even if its creator is not explicitly marked as an affiliate; this is the remaining classification boundary.

### F-002 — Previous runtime Facebook ad hooks were not safe blockers

- `severity`: n/a_re
- `category`: design
- `status`: validated from source
- `evidence_ids`: [E-004]
- `location`: `app/src/main/java/tn/amin/mpro2/FacebookHook.java`, `pipeline/fb/patch_dex/PatchStoryAds.java`, and `PatchGameAds.java`
- `confidence`: high
- `impact`: Story Ads hooks only logged and counted as installed; a Quicksilver `contains("game")` test could suppress unrelated messages and logged message payloads; the Audience Network constructor hook threw an exception. The broad standalone story patcher also changed arbitrary `get`/`fetch`/`load` methods.
- `remediation`: removed those runtime ad hooks, made the old story patcher delegate to `PatchAds`, and disabled the unsafe game-ad patcher. The runtime Story Ads hook is not reported as an active blocker.

### F-003 — Messenger 583 receive action remains unresolved

- `severity`: n/a_re
- `category`: other
- `status`: candidate
- `evidence_ids`: [E-006, E-007]
- `location`: `app/src/main/java/tn/amin/mpro2/hook/all/MessageReceivedHook.java`
- `confidence`: high that action 24 is a send-completion route; low for the unknown receive action.
- `impact`: `MAX_ORCA_VERSION` and recommended version are updated. The new dispatch signature filter only hooks compatible payload shapes. When `API_NOTIFICATION` is unset on build 583, the receive hook stays disabled and logs the unresolved action instead of guessing or capturing unrelated native events. Legacy action 29 remains limited to builds through `345212666`.
- `remediation`: configure `API_NOTIFICATION` only after confirming the receive action from an authorized runtime trace.

## Path

### P-001 — Facebook sponsored and commerce Reel filtering

- `path_type`: callflow
- `start`: Facebook feed/Reels DEX model objects.
- `goal`: prevent structurally identified sponsored and product-commerce content from being inserted or fetched.
- `steps`:
  1. `GraphQLFeedUnitEdge.A03()` yields the cached feed unit and `B8S()` yields its category — E-002, F-001.
  2. Sponsored/promotion categories and `GraphQLFBMultiAdsFeedUnit` return `false` before the feed builder mutates — E-002, E-005, F-001.
  3. For `FB_SHORTS`, only `GraphQLStory` models are tested; the render helper’s `GroupCommerceProductItem` attachment causes a drop — E-003, F-001.
  4. Dedicated sponsored selector, Reels pool, VideoHome ad request, and Story Ads fetch methods are patched by stable strings plus return/signature shape — E-004, E-005, F-001.
- `residual_risks`: no runtime feed/Reels or Story Ads session was available; product-tagged organic commerce Reels may also be filtered.

### P-002 — Messenger receive dispatch guard

- `path_type`: callflow
- `start`: Orca 583 package load and `API_NOTIFICATION` preference.
- `goal`: avoid hooking unrelated native mailbox events.
- `steps`:
  1. Filter candidate native dispatch methods by return/parameter shape — E-006, E-007.
  2. Use the legacy action only on builds whose action was verified; on 583, require an explicit configured action — E-006, F-003.
  3. Omit message content, sender, and IDs from diagnostic logs — source change in `MessageReceivedHook.java`.
- `residual_risks`: the correct receive action and runtime payload layout are not verified.

## Timeline summary

- 2026-10-18: repository and current hooks inspected; initial Git status was clean.
- 2026-10-18: supplied APK manifests and DEX tables analyzed; Facebook feed, commerce, Reels, and Story Ads paths traced; Messenger native dispatch candidates inspected with IDA.
- 2026-10-18: source and DEX patch profile updated; DEX test, clean Android build, unit tests, and lint passed.
- 2026-10-18: MuMu `emulator-5554` became available. Facebook 582 and Messenger 583 were launched with the LSPosed module enabled; both remained alive at login surfaces. Messenger completed module unobfuscation; no authenticated feed/Reels or receive-event test was available.
- 2026-10-18: generated Facebook 582 `classes2.dex` reopened in IDA; feed-filter drop and null/model fail-open control flow verified in the emitted bytecode.
- 2026-10-18: Messenger 583 notification model and Rsys `onMessageReceived` callback inspected in IDA; neither provides a verified Mailbox receive action, so `API_NOTIFICATION` remains unset.

## Runtime limits

No on-device feed, Reels, Story Ads, Messenger receive, toggle, or content-level crash test was completed. The MuMu smoke check proves module loading and app launch only. The installable Facebook output is repacked and signed with the local Android debug key; it cannot update an official-signature Facebook install, and replacing the app may require uninstalling the existing copy. The Messenger APK re-signed for side-by-side emulator testing is test-only and is not a project release artifact. The existing end-to-end `pipeline/fb/run.sh` remains the legacy 576/Superpack pipeline; use `pipeline/fb/build_582_apk.ps1` for the target Facebook 582 APK. REA’s Android JADX provider returned `capability_unavailable` because `REA_JADX_MCP_JAR` was not supplied; no tool configuration was changed. IDA Pro and local DEX inspection supplied the static evidence in this report.
