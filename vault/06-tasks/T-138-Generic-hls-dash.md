---
id: T-138
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, extractors]
---

# T-138 — Generic HLS and DASH discovery

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Finding exactly one `.m3u8` or `.mpd` URL in a page hands it to the existing `M3u8` / `Mpd` parsers and the fragment path. Live and DRM still fail typed. Zero or several manifest candidates still fall through to the desktop CLI or Android Chaquopy.

## Dependencies

- [T-137](T-137-Generic-html-discovery.md) — Done 2026-09-29.

## Context the next session needs

- `HttpDownloadEngine.streamManifestToTemp` already parses HLS/DASH, picks a variant, and fails typed for live playlists, unsupported key methods, dynamic MPD, and DRM. T-045 left HLS/DASH out of the generic subset; this task routes a discovered manifest into that same path.
- `UrlClassifier` marks `application/vnd.apple.mpegurl`, `application/x-mpegurl`, `audio/mpegurl`, and `application/dash+xml` as `MANIFEST`; a `.m3u8`/`.mpd` tail is a hint only.
- The generic extractor returns candidates to `GenericIE`, which builds the `InfoDict` the engine consumes.

Files: `GenericExtractor.kt`, `GenericIE.kt`, and the engine's manifest path in `HttpDownloadEngine.kt`.

## Work

- Discovery: find `.m3u8` and `.mpd` URLs in page attributes (`src`, `data-*`) and raw text, resolve them against the page URL, and put each through `UrlPolicy`.
- A page that yields exactly one policy-safe manifest URL must reach the existing parsers. Zero or several keep the typed fall-through.
- Run the same exactly-one rule over the combined candidate set (direct media plus manifests): a single manifest is parsed, a single direct file downloads as today.
- Keep the live and DRM errors as the typed failures the parsers already produce; do not soften them.
- Tests: reuse the existing local `HttpServer` fixtures for one m3u8 and one mpd, plus live-HLS, DRM-MPD, and zero/several fall-through cases.
- Update the `GenericIE` manifest scope for HLS/DASH discovery.

## Acceptance criteria

- [x] A page with one `.m3u8` downloads through `M3u8`/fragments; a page with one `.mpd` goes through `Mpd`; the tests assert the concatenated bytes.
- [x] Live HLS and DRM MPD still fail with the existing typed errors.
- [x] Zero or several manifest candidates still fall through instead of being guessed.
- [x] The manifest `GenericIE` scope names HLS/DASH discovery, and `:tools:port-manifest:check` is green.
- [x] `:shared:core:jvmTest` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Discovery:** `GenericExtractor` scans `src` and `data-*` attribute values plus raw page text for `.m3u8`/`.mpd` URLs, resolves them against the page URL, requires the path tail to be a manifest (`isManifestUrl`), and puts every survivor through the same `UrlPolicy` and exactly-one rule as the direct media candidates. The raw-text scan matches the extension and expands to token boundaries in one linear pass.
- **Routing:** a discovered URL is nothing new to the engine — `downloadDirectFile` classifies `/media.m3u8` (`application/vnd.apple.mpegurl`) and `/dash/manifest.mpd` (`application/dash+xml`) as `MANIFEST` and reuses `streamManifestToTemp`, so the existing `M3u8`/`Mpd` parsers, fragment downloader, live/DRM typed failures, and the T-135 skip policy all apply unchanged.
- **Tests:** `GenericExtractorTest` adds raw-text discovery, `data-*` discovery, a query-stamped manifest, zero manifests, two manifests (fail closed), a direct-file-plus-manifest mix (fail closed), and a policy-rejected manifest. `ManifestEngineDownloadTest` adds a discovered HLS page that assembles the three TS fragments byte-exactly (`media.ts`), a discovered DASH page that assembles `INITSEG-1SEG-2` (`manifest.mp4`), a two-manifest fall-through, a live HLS typed failure, and a DRM MPD typed failure.
- **Perf note:** the first raw-text regex backtracked catastrophically on the 512 KB oversized-page test (the JVM suite went from ~10s to ~11 min). It is now a linear extension scan; the full suite is back to seconds.
- **Manifest:** the `GenericIE` scope names the HLS/DASH discovery and the remaining limits; `:tools:port-manifest:run`/`check` are green.
- **Commands:** `./gradlew --console=plain :tools:port-manifest:run :tools:port-manifest:check :shared:core:jvmTest :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL; JVM 577 tests / 0 failures, iOS 522 tests / 0 failures.
- No commit.
