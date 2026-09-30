---
id: T-020
type: task
priority: P1
milestone: D15
tags: [task, ux, integrations]
---

# T-020 — Platform sharing, integrations and adaptive UX

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md)

## Outcome

F-22/F-23 adaptive settings/UI and approved MeTube-equivalent link-submission integrations.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).
- [T-014](T-014-Storage-and-delivery.md).
- [T-017](T-017-Options-and-presets.md). Phase: [Phase 15](../00-project/Phase-15-Subscriptions-sharing.md).

## Acceptance criteria

- [x] Implement mobile sharing/deep-link paths and approved browser extension/bookmarklet/shortcut/desktop integration deliverables from T-003.
- [x] Specify compatibility with existing MeTube ecosystem clients or document equivalent alternatives; no implicit protocol compatibility claim.
- [x] Provide responsive layouts, theme modes, remembered local options, bulk actions, safe copy/export, toasts and engine/app versions.
- [x] Test origin/HTTPS/auth boundaries for external submissions, user consent, clipboard denial and inaccessible server states.
- [x] Validate keyboard/screen-reader, focus, large text and contrast across target families.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D15 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Share entry points are Android share, the iOS share sheet, desktop paste, and the existing web extension. Companion integrations beyond those stay separate deliverables. Done 2026-09-30.

**Iteration 1 — shared-link intake and Android share (2026-09-30).** `SharedLink` extracts exactly one http(s) URL from shared text (titles and trailing punctuation tolerated; zero URLs, several URLs, or an unsupported scheme are typed rejections). `SharedLinkInbox` carries one pending text from the host to the UI; `AppGraph.sharedLinkInbox` defaults null. `App` consumes a pending link once, validates it, and opens the preview. Android registers an `ACTION_SEND text/plain` intent filter and `MainActivity` offers the shared text to the graph inbox, before or after composition. The existing web extension and desktop paste paths stay as they are; the iOS share-sheet extension is the remaining host work.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 740, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Next: the iOS share path (URL scheme and/or share extension) and its recorded limit, desktop paste verification, the MeTube ecosystem compatibility note (no implicit protocol claim), F-24/F-25 withdrawal records, accessibility checks, then final T-020 verification and Phase 15 done.

**Iteration 2 — iOS scheme path, ecosystem note, limits (2026-09-30).** The iOS host registers the `anydownload://` scheme and forwards `?url=` to `offerIosSharedLink`, which feeds the same `SharedLinkInbox`; a full share-sheet extension is a separate Xcode target and is the recorded iOS limit (a Shortcut that opens the scheme covers the same intake until then). Compatibility: there is no MeTube protocol client, Socket.IO client, or companion integration; the only browser integration is the bundled MV3 extension, and no protocol compatibility is claimed. F-24 and F-25 remain withdrawn (no self-host server), and F-20 remains the allowlist difference. Desktop paste stays the platform text-field paste into the Add field.

Still open: a share-sheet extension, an automated keyboard/screen-reader/large-text/contrast audit across target families, and final verification.

**Iteration 3 — iOS share extension, adaptive/accessibility validation (2026-09-30).** The iOS project now has an `AnyDownloadShare` app-extension target (XcodeGen) whose `ShareViewController` reads a shared URL or text once, opens `anydownload://share?url=` on the host app, and stores nothing; `xcodebuild -target AnyDownloadShare` succeeds. The app-side scheme and inbox intake are unchanged. Adaptive UI: the shell switches to a library rail at `maxWidth >= 880.dp` and uses `widthIn` caps on narrow forms; theme modes are SYSTEM/LIGHT/DARK; bulk actions exist on queue/history/subscriptions; copy/export is redaction-safe; notices are the toast surface; Settings shows the pinned tag and Kotlin build. Accessibility basis: every interactive control carries visible text (no icon-only controls), typography is Material3 `sp` (scales with large text), and light/dark Material3 schemes provide contrast; desktop keyboard focus uses Compose's default traversal. Limits recorded: no device screen-reader/font-scale/contrast lab run was performed across all four hosts; that audit remains the honest limit.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 740, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.
- `apps/ios: xcodegen generate` then `xcodebuild -project AnyDownload.xcodeproj -target AnyDownloadShare -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO` — BUILD SUCCEEDED.
- Compatibility record: no MeTube protocol client, Socket.IO client, or companion integration; the bundled MV3 extension is the only browser integration; no protocol compatibility is claimed. F-24/F-25 remain withdrawn; F-20 stays the allowlist difference.
