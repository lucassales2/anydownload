---
id: T-057
type: task
priority: P0
milestone: D4
tags: [task, engine, kmp]
---

# T-057 — Extractor core: InfoExtractor base, InfoDict, registry, helpers

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

The shared structure every later extractor translation plugs into, mirroring upstream `extractor/common.py` closely enough that a translation is file-by-file. The D3 generic subset moves onto it with no behavior change. Covers E-01 to E-04.

## Dependencies

- [T-055](T-055-Port-manifest-and-equivalence-matrix.md) — the manifest records the modules this task ports.
- [T-056](T-056-Http-request-port.md) — extractors fetch through the new request port.

## Context the next session needs

Upstream `common.py` is 4,189 lines at the pin. Translate only what this phase's extractors call, and name each translated function in the manifest `scope`. Package `com.anydownlod.core.extract`. No new Gradle module. Add `kotlinx-serialization-json` to `shared/core` for JSON.

## Work

- `InfoExtractor` abstract class: `ieKey`, `validUrl: Regex`, `suitable(url)`, `matchId(url)`, `extract(url): InfoDict`, and an injected `ExtractorHttp` that wraps `HttpTransfer` with `downloadWebpage(url, headers, maxBytes)` and `downloadJson(url, method, headers, body)`. Page reads stay bounded; the cap is a per-call argument, default 512 KiB, because YouTube's player JS is larger and is read by a later task with its own cap.
- `InfoDict`: `id`, `title`, `ext`, `url`, `formats: List<MediaFormat>`, `thumbnails`, `duration`, `uploader`, `channel`, `channelId`, `uploadDate`, `viewCount`, `description`, `webpageUrl`, `extractor`, `extractorKey`, `ageLimit`, `isLive`, `availability`. Names follow upstream in Kotlin case. Fields not filled stay null. Playlist results are a sealed sibling, not populated in D4.
- `MediaFormat`: `formatId`, `url`, `ext`, `protocol`, `vcodec`, `acodec`, `width`, `height`, `fps`, `tbr`, `abr`, `vbr`, `asr`, `filesize`, `filesizeApprox`, `container`, `language`, `quality`, `sourcePreference`, `formatNote`, `httpHeaders`, `downloaderOptions` (`httpChunkSize`), `hasDrm`, `manifestUrl`, `fragmentBaseUrl`. Audio-only and video-only are derived properties from `vcodec`/`acodec` `none`.
- `ExtractionError` sealed: `UnsupportedUrl`, `Unavailable`, `LoginRequired`, `AgeRestricted`, `GeoRestricted(countries)`, `NoFormats`, `Malformed(reason)`. Messages are redacted and short.
- `ExtractorRegistry`: ordered list; `suitableFor(url)` returns the first match; `GenericExtractor` is last and only for `text/html` responses as today.
- Helpers in `ExtractorUtils`: `searchRegex`, `htmlSearchMeta`, `parseJson` (lenient), `traverse` (path with `..` wildcards and type filters, the subset used), `urlOrNone`, `intOrNone`, `floatOrNone`, `strOrNone`, `unescapeHtml`, `parseCodecs(mimeType)`, `mimetype2ext`, `parseIso8601`, `unifiedStrdate`. Each helper carries the upstream function name in its KDoc.
- Move `GenericExtractor` onto `InfoExtractor` (`GenericIE`), returning an `InfoDict` with one `MediaFormat`. `HttpDownloadEngine` keeps calling it; its 16 tests stay green.
- Update `port/manifest.json`: `common.py` partial, `utils/_utils.py` partial, `generic.py` partial (unchanged scope).

## Acceptance criteria

- [ ] Unit tests for every helper with upstream-derived fixture strings (translated from `test/test_utils.py` cases where applicable, with the notice).
- [ ] `ExtractorRegistry` picks the first suitable extractor; an unmatched URL yields `UnsupportedUrl`.
- [ ] `GenericExtractorTest` passes unchanged in behavior; the engine HTML route tests pass.
- [ ] Every new file has the Unlicense header with the upstream path and revision; the manifest lists the modules and scopes.
- [ ] `:shared:core` compiles for JVM, Android, iOS simulator, and Wasm.

## Evidence / notes

Not started.
