---
id: T-046
type: task
priority: P0
milestone: D3
tags: [task, engine, kmp]
---

# T-046 — Engine uses the generic extractor

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

An HTML page with exactly one resolvable media element downloads on every host that has the shared HTTP engine. Other HTML fails typed.

## Dependencies

- [T-045](T-045-Generic-extractor-subset.md).

## Work

- On `NEEDS_EXTRACTOR`, read a bounded HTML body (cap the bytes; do not hold a huge page), run `GenericExtractor`, then stream the chosen URL with the existing transfer and temp-file publish.
- The media response must classify as a direct file. If it is HTML again, fail typed. Do not recurse.
- Apply `UrlPolicy` to the media request and its redirects, same as a direct file.
- Cancel during the HTML read or the media stream discards the temp file.
- Progress for the media body stays as D2 defined it. Do not invent speed or ETA.
- `WebExtensionEngine` is wired in [T-050](T-050-Web-extension-generic.md), not here.
- Custom yt-dlp JSON stays ignored.

## Acceptance criteria

- [x] JVM tests: matching HTML fixture completes as a file; direct file still completes; zero-match and two-match HTML fail typed; media URL that redirects to a blocked address fails without saving; cancel mid-media leaves no completed file.
- [x] Tests use an in-memory dispatcher or local mock. No live host.
- [x] `./gradlew :shared:core:jvmTest` passes.

## Evidence / notes

Done 2026-09-24.

- `HttpDownloadEngine` HTML route (T-046): the `NEEDS_EXTRACTOR` branch of `downloadDirectFile` now reads a bounded page body (`readBoundedHtml`, cap `MAX_HTML_BYTES = 512 KiB`, chunked `decodeToString`, never holds a huge page), runs `GenericExtractor.extract(pageUrl, html)`, and for `Direct(url)` downloads the chosen URL through the same loop (policy on every hop, redirects, temp-file publish). `Fail`ed extraction maps to typed `EXTRACTION_FAILURE` with redacted messages per reason (no URL, no HTML dump).
- No recursion: the media hop runs with `extractHtml = false`; if it returns HTML again the job fails typed ("The media URL returned another page instead of a file."). `UrlPolicy` applies to the media URL and its redirects exactly like a direct file. Cancellation during the HTML read or media stream discards the temp file (existing `streamToFile` finally). Progress for the media body is unchanged D2 behavior; speed/ETA stay null. `WebExtensionEngine` untouched (T-050). Custom yt-dlp JSON still ignored.
- Tests added to `HttpDownloadEngineTest` (commonTest, fixture strings and mock transfer/file store only, no live host): `matchingHtmlFixtureDownloadsTheMediaUrl` (page + media fetched, artifact bytes match, published), `zeroMatchHtmlFailsTypedWithoutSaving`, `twoMatchHtmlFailsTypedWithoutSaving`, `mediaUrlRedirectingToBlockedAddressFailsWithoutSaving` (INVALID_URL_OPTIONS, no publish), `mediaHopReturningHtmlAgainFailsTypedWithoutRecursing` (exactly 2 fetches), `cancelDuringMediaStreamAfterExtractionLeavesNoFile` (CANCELLED, temp discarded, no publish), `oversizedPageIsBoundedAndFailsCleanly` (media beyond the 512 KiB cap is never found, 1 fetch). Existing direct-file, redirect, cancel, and progress tests still pass unchanged.
- Verification: `./gradlew :shared:core:jvmTest` — 124 tests, 0 failures; `:shared:ui:jvmTest` — 71 tests, 0 failures; `:shared:core:compileKotlinIosSimulatorArm64`, `compileKotlinWasmJs`, `iosSimulatorArm64Test`, `:apps:desktop:compileKotlin`, `:apps:android:compileDebugKotlin` all BUILD SUCCESSFUL.