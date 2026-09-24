---
id: T-061
type: task
priority: P0
milestone: D4
tags: [task, engine, kmp]
---

# T-061 — Engine extracts, selects one format, downloads it

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

`HttpDownloadEngine` runs the registry before the network probe. A matched URL is extracted, one format is selected from the compiled spec, and that format's URL is downloaded through the existing direct-file path, with ranged chunks when the format asks for them. Direct files and the generic HTML route behave as before. Covers E-04 and E-07 (partial).

## Dependencies

- [T-058](T-058-Format-selector.md), [T-060](T-060-Youtube-jsless-extractor.md), [T-056](T-056-Http-request-port.md).

## Work

- In `runJob`: `registry.suitableFor(url)` → if matched, state `RESOLVING`, call `extract`, map `ExtractionError` to `JobErrorCode` (`LoginRequired`→`LOGIN_REQUIRED`, `AgeRestricted`/`Unavailable`→`UNAVAILABLE_OR_PRIVATE`, `GeoRestricted`→`UNAVAILABLE_OR_PRIVATE`, `NoFormats`→`UNSUPPORTED_FORMAT`, `Malformed`→`EXTRACTION_FAILURE`, `UnsupportedUrl`→`UNSUPPORTED_SOURCE`). Unmatched URLs keep the D2/D3 probe path unchanged.
- Selection: `OptionsToSpec.compile(options)`; `NeedsToolkit` and `Merge` fail with `UNSUPPORTED_FORMAT` and a message that names the media toolkit as not built and suggests M4A/Opus or a single-file video; `NotInPhase` fails `UNSUPPORTED_FORMAT`. Record on the job the number of formats hidden for lack of a JavaScript runtime.
- Job row gains `title`, `thumbnailUrl`, and `sourceHost` from the `InfoDict` before the download starts.
- Download: the selected `MediaFormat.url` goes through `downloadDirectFile(..., extractHtml = false)` with the format's `httpHeaders`. When `downloaderOptions.httpChunkSize` is set, read in `Range` chunks of that size sequentially into the same temp file; progress uses `filesize` or `Content-Length` of the first chunk's `Content-Range` total. A 403 mid-stream on a chunk fails `UNAVAILABLE_OR_PRIVATE`, not `NETWORK_FAILURE`.
- Artifact name: `ArtifactName` takes the `InfoDict.title` (sanitized) and `MediaFormat.ext` instead of the URL tail; collision policy unchanged.
- Cancel discards the temp file at any point, including between chunks.
- Tests with a fake registry/extractor and a local `HttpServer` on JVM: matched URL downloads the selected format; ranged chunks assemble byte-exact; each typed error mapping; `NeedsToolkit` for MP3; cancel between chunks leaves no file; unmatched direct file and generic page paths unchanged (existing tests).

## Acceptance criteria

- [ ] Engine tests above pass; D2/D3 engine tests unchanged.
- [ ] No compiled spec with `+` reaches the downloader (assertion in tests).
- [ ] Job rows show title and thumbnail from extraction; error messages are redacted and typed.
- [ ] `shared/core` still has no process, Python, or Chaquopy references.

## Evidence / notes

Not started.
