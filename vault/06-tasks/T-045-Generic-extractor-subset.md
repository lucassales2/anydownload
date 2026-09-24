---
id: T-045
type: task
priority: P0
milestone: D3
tags: [task, engine, kmp]
---

# T-045 — Generic extractor subset

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md) · [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md)

## Outcome

A shared function that reads a simple HTML page and returns one direct media URL, or a typed failure. No download. No process.

## Dependencies

- [T-054](T-054-Download-and-edit.md) — the first-screen cards finish before this extractor.
- [T-006](T-006-Review-security-licensing.md) — Unlicense yt-dlp may be translated with notices.
- [T-038](T-038-Shared-http-engine.md) — `UrlPolicy` exists and must be reused.

## Context the next session needs

`UrlClassifier` already marks `text/html` as `NEEDS_EXTRACTOR`. This task does not change `HttpDownloadEngine`. It only adds the parser the next task will call.

Package: `com.anydownlod.core.extract`. Do not create a Gradle module.

## Work

- Re-read yt-dlp `yt_dlp/extractor/generic.py` at the `2026.8.19` tag (the Android pin). Translate only the behavior below. Do not copy the file into the repo.
- Add a notice in-repo (a `NOTICE` or a header comment plus a short `shared/core` notice file) naming that revision, the Unlicense, and that this is a translation of a subset.
- Accept the page URL and the HTML string. Find `<video src>`, `<audio src>`, and `<source src>` nested in those elements. Ignore other tags.
- Resolve relative URLs against the page URL. Run each candidate through `UrlPolicy`. Drop candidates that fail policy.
- One surviving candidate returns that URL. Zero or more than one returns a typed, redacted failure (no HTML dump).
- Do not follow playlists, embeds, iframes, JSON-LD, meta refresh, HLS, or DASH.

## Acceptance criteria

- [x] Unit tests: one `<video src>`, one `<audio src>`, one nested `<source>`, a relative URL, zero matches, two matches, and a policy-rejected target (userinfo or non-HTTP).
- [x] Tests use fixture strings. No live host.
- [x] The notice names the upstream revision. `generic.py` is not vendored.
- [x] Common code still has no process or Python API.

## Evidence / notes

Done 2026-09-24.

- Re-read `yt_dlp/extractor/generic.py` at the Android pin `yt-dlp==2026.8.19` (upstream tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`, fetched from GitHub on this date). Translated only the HTML5-media subset: `<video src>`, `<audio src>`, and nested `<source src>` (case-insensitive), the upstream `urljoin`-style resolution, `orderedSet()` dedupe, and `unescapeHTML`/`\/` de-escaping. Playlists, embeds, iframes, JSON-LD, meta refresh, HLS, DASH stay out.
- New `com.anydownlod.core.extract.GenericExtractor` in `shared/core/src/commonMain/.../extract/GenericExtractor.kt` with an Unlicense header naming the revision; short notice also at `shared/core/NOTICE.md`. `generic.py` is not vendored. No ProcessBuilder, no Python, no new Gradle module.
- API: `GenericExtractor.extract(pageUrl, html)` → `GenericExtraction.Direct(url)` or `Failed(UnsupportedPageUrl | NoMedia | MultipleMedia)` — typed and redacted, never carrying HTML. Every candidate goes through the existing `UrlPolicy`; one survivor wins, zero or more than one fail closed.
- Tests: `GenericExtractorTest` (16 cases) in `shared/core/src/commonTest`, fixture strings on example.org only: video src, audio src, nested source, relative + `../` + scheme-relative resolution, query/fragment base stripping, uppercase tags, `&amp;`/`&#x2F;` entities, dedupe, zero matches, source-outside-element ignored, two matches fail closed, userinfo and ftp rejected to NoMedia, one-good-plus-one-bad wins with the good one, non-http page URL fails typed.
- Verification: `./gradlew :shared:core:jvmTest` — 117 tests, 0 failures; `:shared:core:compileKotlinMetadata`/`compileKotlinIosSimulatorArm64`/`compileKotlinWasmJs` and `:apps:android:compileDebugKotlin` all BUILD SUCCESSFUL; `:shared:ui:jvmTest` still green.
