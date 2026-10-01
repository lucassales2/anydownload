---
id: T-137
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, extractors]
---

# T-137 — Generic HTML discovery

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

`GenericExtractor.kt` also finds media in embeds, iframes, JSON-LD, and meta refresh, not only `<video>`, `<audio>`, and `<source>`. A page that resolves to exactly one policy-safe media URL stays on the Kotlin path; zero or several still fall through to the desktop CLI or Android Chaquopy.

## Dependencies

- [T-136](T-136-Generic-helpers.md) — Done 2026-09-29.

## Context the next session needs

- `GenericExtractor.extract(pageUrl, html, candidateCheck)` returns `Direct` only for exactly one policy-safe candidate; otherwise it returns the typed `NoMedia`/`MultipleMedia` failures. `GenericIE` maps `Direct` to one `MediaFormat`; `HttpDownloadEngine.extractMediaFromPage` downloads it with `extractHtml = false`, so a media hop cannot extract again.
- `UrlPolicy` stays on every candidate. The engine is the only caller; the fall-through to the desktop CLI / Android Chaquopy depends on the typed failures, not on an exception.
- Upstream `generic.py` reference methods: `_extract_embeds` (embed/iframe/video.js), `_search_json_ld` (JSON-LD, including `@graph`), and the meta-refresh handling around the `http-equiv="refresh"` regex.

Files: `GenericExtractor.kt` and `GenericIE.kt` in `anydownload`.

## Work

- Discover candidates from:
  - `<embed src>` and `<iframe src>`, resolved against the page URL. D10 does not crawl a non-media iframe; record that limit.
  - JSON-LD: parse `<script type="application/ld+json">` blocks (including `@graph`) with the T-136 helpers and take `contentUrl` / `embedUrl`, plus `url` only when it resolves to media.
  - Meta refresh: `<meta http-equiv="refresh" content="...url=...">`. Follow one hop inside `GenericIE` (bounded, same policy) when it points at another page, or accept the target as a candidate when it is media.
- Keep first-seen dedupe and the exactly-one rule; every candidate still passes `UrlPolicy`. A non-media URL is not a candidate.
- Tests: fixture HTML for each source, relative resolution, zero and several still failing typed, a policy-rejected candidate not winning, and a meta-refresh chain that stops after one hop.
- Update the `GenericIE` scope in `port/manifest.json` with what is now in and what stays out, then rerun `:tools:port-manifest:run` and `:tools:port-manifest:check`.

## Acceptance criteria

- [x] Fixture tests cover embed, iframe, JSON-LD (`@graph` included), and meta refresh, each resolving to one direct media URL.
- [x] Zero or several candidates still return the typed failure the CLI/Chaquopy fall-through depends on.
- [x] A meta refresh is followed at most once and only through `UrlPolicy`.
- [x] The manifest `GenericIE` scope names the new sources and the remaining limits (no recursive crawl, no playlist).
- [x] `:shared:core:jvmTest` is green; `:tools:port-manifest:check` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Sources:** `GenericExtractor` now also scans `<embed src>`, `<iframe src>` (only when the resolved target is a direct media file; a non-media iframe is never crawled), JSON-LD entries via `JsonLd.entries` (`contentUrl` unconditionally, `embedUrl`/`url` only when they resolve to a direct media file), and it parses the meta-refresh target (`metaRefreshTarget`, upstream `REDIRECT_REGEX` shape, flexible attribute order/casing). Every candidate still resolves through `resolveAgainst` and passes the caller's `UrlPolicy`; first-seen dedupe and the exactly-one rule are unchanged. The engine's `extractMediaFromPage` and the web extension path call `GenericExtractor.extract`, so they pick up the new media sources; the one-hop refresh lives in `GenericIE` as the task specified.
- **Meta refresh:** `GenericIE` follows one hop only when the page has no media of its own: a direct-media target is returned without a second fetch, a page target is fetched once (same `UrlPolicy`) and scanned once — a second refresh on that page is not followed.
- **Tests:** `GenericExtractorTest` adds embed/iframe media and non-media fixtures, JSON-LD `contentUrl`/`@graph`/`embedUrl`/`url` fixtures, cross-source multiple candidates, a policy-rejected embed, and `metaRefreshTarget` shapes. `GenericIETest` adds the one-hop refresh (page and direct-media targets), the stop-after-one-hop chain, and the rejected-target case, asserting the exact request list each time.
- **Manifest:** the `GenericIE` scope names embed/iframe, JSON-LD, and the one-hop meta refresh, and records the remaining limits (no iframe crawl, no `Refresh` header, no playlist, no HLS/DASH discovery, no JSON-LD metadata map). `:tools:port-manifest:run`/`check` are green.
- **Commands:** `./gradlew --console=plain :tools:port-manifest:run :tools:port-manifest:check :shared:core:jvmTest :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL; JVM 565 tests / 0 failures, iOS 515 tests / 0 failures.
- No commit.
