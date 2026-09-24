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

- [x] Engine tests above pass; D2/D3 engine tests unchanged.
- [x] No compiled spec with `+` reaches the downloader (assertion in tests).
- [x] Job rows show title and thumbnail from extraction; error messages are redacted and typed.
- [x] `shared/core` still has no process, Python, or Chaquopy references.

## Evidence / notes

Done 2026-09-24.

- `HttpDownloadEngine` takes an optional `registry: ExtractorRegistry?`. `runJob` branches: a matched URL goes through `extractAndDownload`, an unmatched URL keeps the D2/D3 probe path unchanged.
- `extractAndDownload`: `RESOLVING` → `extractor.extract` → the job row gains `title`, `thumbnailUrl` (largest thumbnail), `sourceHost`, and `formatsNeedingJs` → `resolveFormat` compiles the typed options and selects exactly one format → `DOWNLOADING` → `downloadDirectFile` with the format's `httpHeaders`, `httpChunkSize`, declared size, and title/ext for naming. `ExtractionError` maps per the note: `LoginRequired`→`LOGIN_REQUIRED`, `AgeRestricted`/`Unavailable`/`GeoRestricted`→`UNAVAILABLE_OR_PRIVATE`, `NoFormats`→`UNSUPPORTED_FORMAT`, `Malformed`→`EXTRACTION_FAILURE`, `UnsupportedUrl`→`UNSUPPORTED_SOURCE`.
- `resolveFormat`/`resolveSelection` are internal and directly tested. `NeedsToolkit`, `NotInPhase`, `Selection.Merge`, and an empty selection all fail `UNSUPPORTED_FORMAT`: the toolkit message suggests M4A/Opus or a single-file video, and an empty selection mentions how many formats need the JavaScript runtime when there are any.
- Ranged chunks: when a format declares `http_chunk_size`, the first request is `Range: bytes=0-(chunk-1)` and `streamChunkedToFile` appends sequential ranges into the same temp file; the total comes from the first chunk's `Content-Range` or the declared `filesize`. A non-2xx chunk status maps through the shared HTTP table, so a mid-stream 403 is `UNAVAILABLE_OR_PRIVATE`, not `NETWORK_FAILURE`. Cancel and failures discard the temp file at any point, including between chunks.
- `ArtifactName.build(title, ext, options)` sanitizes the extracted title and appends the container; the collision policy in the file store is unchanged. `DownloadJob` gained `formatsNeedingJs`.
- Tests: `EngineExtractionTest` 8 (matched extract/select/download with title+ext naming and job metadata; all seven typed error mappings; MP3 `NeedsToolkit`; Merge and empty-selection messages; every compiled spec checked for `+`; a format without a URL; the unmatched probe path), `JavaNetChunkedDownloadTest` 3 (9000 bytes in 2048-byte ranges assembled byte-exact over five local-server requests; a mid-chunk 403 → `UNAVAILABLE_OR_PRIVATE` with no file; cancel between chunks leaves no file). The existing `HttpDownloadEngineTest` (27) and all D2/D3 tests are unchanged and green.
- Verification: `./gradlew :shared:core:jvmTest` → 249 tests, 0 failures; `:apps:desktop:test` and `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; `:shared:core:compileTestKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin` green; `:tools:port-manifest:check` green. Grep confirms `shared/core` has no `ProcessBuilder`/Chaquopy imports (one KDoc comment names the Chaquopy pip pin).
- No manifest change: this task wires the engine to the T-055–T-060 modules rather than translating an upstream file.
