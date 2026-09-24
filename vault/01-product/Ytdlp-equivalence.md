---
type: feature-matrix
status: proposed
tags: [product, parity, engine, research]
---

# yt-dlp equivalence matrix

[Home](../Home.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [MeTube parity](Feature-parity.md)

**Baseline:** yt-dlp tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`, inspected 2026-09-24. This note is the target for "the Kotlin port of yt-dlp" from [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). The [MeTube matrix](Feature-parity.md) covers the product workflows on top; this one covers the engine underneath.

Two halves, decided by the owner on 2026-09-24 ([ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)):

1. **Core engine** — the hand-written table below. A row is *Done* only when the owning task links tests on every host the row applies to.
2. **Extractor catalog** — 1,751 extractor classes named in upstream `_extractors.py` at the pin. Coverage is a generated table from `port/manifest.json` ([T-055](../06-tasks/T-055-Port-manifest-and-equivalence-matrix.md)). Nobody edits that table by hand.

Statuses: **Done** (tested on every applicable host), **Partial** (subset shipped; the note says which), **D4** (in the current phase), **Later** (planned, no phase), **Out** (deliberately not equivalent; the reason is in the row).

## Core engine rows

| ID | yt-dlp capability (upstream module) | Kotlin target and honest scope | Status | Tasks |
| --- | --- | --- | --- | --- |
| E-01 | URL matching and extractor dispatch (`extractor/common.py` `_VALID_URL`, `suitable`, `_match_id`; `extractor/_extractors.py`) | `ExtractorRegistry` picks the first suitable `InfoExtractor`; generic subset is last resort. | D4 | [T-057](../06-tasks/T-057-Extractor-core.md) |
| E-02 | `info_dict` and format model (`common.py` `_real_extract` contract, `formats`, `ext`/`vcodec`/`acodec`/`tbr`/`height`/`fps`/`protocol`/`filesize`) | `InfoDict` and `MediaFormat` with the upstream field names in Kotlin case. Fields the port does not fill stay null. | D4 | T-057 |
| E-03 | Extractor helper library (`_search_regex`, `_parse_json`, `traverse_obj`, `url_or_none`, `int_or_none`, `unescapeHTML`, `_download_json`/`_download_webpage`) | Subset translated as needed by each extractor; recorded in the manifest as `utils` partial. | D4 (partial) | T-057 |
| E-04 | Typed extraction errors (`ExtractorError`, `GeoRestrictedError`, login-required, age-limit, unavailable) | Sealed `ExtractionError` mapped to `JobErrorCode`. | D4 | T-057, [T-061](../06-tasks/T-061-Engine-extracts-selects-downloads.md) |
| E-05 | Format selection language (`YoutubeDL.build_format_selector`: `best`, `bv*+ba/b`, `[height<=720]`, `/` fallback, `,` multi) | Parser and evaluator ported. `+` merges parse but are rejected typed until the toolkit exists. Typed options compile to a spec. | D4 (partial: no merge) | [T-058](../06-tasks/T-058-Format-selector.md) |
| E-06 | Format sorting (`FormatSorter`, `-S`: `res,fps,codec,br,size,proto,ext,...`) | Sort fields ported; defaults match upstream. | D4 | T-058 |
| E-07 | HTTP downloader (`downloader/http.py`: ranges, `http_chunk_size`, resume, retries) | Ranged chunk download when the format declares a chunk size; retries bounded; resume later. | D4 (partial: no resume) | [T-056](../06-tasks/T-056-Http-request-port.md), T-061 |
| E-08 | Native HLS downloader (`downloader/hls.py`, `extractor/common._extract_m3u8_formats`) | Media playlist parse, fragment concat, AES-128, fMP4 init; no FFmpeg. Live playlists later. | D4 | [T-073](../06-tasks/T-073-Native-hls-dash-downloaders.md) |
| E-09 | Native DASH downloader (`downloader/dash.py`, `_parse_mpd_formats`) | `SegmentTemplate`/`SegmentList`/`SegmentBase` and fragment concat; no FFmpeg. | D4 | T-073 |
| E-10 | Fragment downloader shared logic (`downloader/fragment.py`: concurrency, skip unavailable, retries) | Sequential first; concurrency later. | D4 (partial) | T-073 |
| E-11 | JS challenge solving (`extractor/youtube/jsc`, yt-dlp-ejs, `n`/`sig`) | `JsRuntime` port; bundled EJS `0.8.0`; QuickJS on JVM/Android/iOS; page JS on web. | D4 | [T-069](../06-tasks/T-069-Ejs-bundle-and-jsruntime-port.md), [T-070](../06-tasks/T-070-Ejs-challenge-solving-web-client.md), [T-071](../06-tasks/T-071-Js-runtime-desktop-android-ios.md), [T-072](../06-tasks/T-072-Js-runtime-web-page.md) |
| E-12 | Legacy JS interpreter (`jsinterp.py`) | Not ported. EJS replaces it. | Out | — |
| E-13 | PO token providers (`extractor/youtube/pot`) | Not in D4. Formats that need a PO token are dropped as upstream does without a provider. | Later | — |
| E-14 | Playlists and entries (`playlist_result`, `--playlist-items`, `--flat-playlist`) | Child jobs through the queue work. | Later | [T-012](../06-tasks/T-012-Playlists-and-batches.md) |
| E-15 | Output template (`--output`, `prepare_filename`, sanitization) | Safe template subset after the artifact naming rules. | Later | [T-014](../06-tasks/T-014-Storage-and-delivery.md) |
| E-16 | Cookies (`--cookies`, `--cookies-from-browser`, `cookies.py`) | Netscape cookie file import, on-device, opt-in. Browser store reading is out of scope on mobile and web. | Later | [T-018](../06-tasks/T-018-Cookie-lifecycle.md) |
| E-17 | Postprocessors: merge (`FFmpegMergerPP`), extract audio, convert, embed thumbnail/metadata/subtitles | Media toolkit per ADR-007: FFmpeg on desktop, MediaMuxer on Android, AVFoundation on iOS, web gap. Not built. | Later | [T-013](../06-tasks/T-013-Media-formats.md), [T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md) |
| E-18 | Subtitles (`--write-subs`, `--sub-langs`, conversion) | Later | Later | T-015 |
| E-19 | Thumbnails (`--write-thumbnail`, conversion) | Later | Later | T-015 |
| E-20 | Chapters, SponsorBlock, `--download-sections` | Needs the toolkit. | Later | [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md) |
| E-21 | Live streams (`--live-from-start`, HLS live) | Later | Later | — |
| E-22 | Network: proxies, `--source-address`, rate limit, sleep, retries | Bounded retries in D4; proxies and rate limit later; per-job proxy overrides are out (Q-09). | Later | — |
| E-23 | Impersonation (`curl_cffi`, `--impersonate`) | Platform HTTP stacks do not impersonate. Recorded gap. | Out | — |
| E-24 | Options surface and config files (`--config-locations`, free-form options, plugins, exec hooks, external downloaders) | Typed allowlist only. Plugins, exec, config files, and external downloaders are out (Q-09, ADR-006). | Out | [T-017](../06-tasks/T-017-Options-and-presets.md) |
| E-25 | Download archive and history (`--download-archive`) | App history is the archive; upstream file format not implemented. | Later | T-014 |
| E-26 | Engine version and update (`--update`, `--version`) | Show the pinned upstream tag and the Kotlin build; no self-update. | Later | [T-023](../06-tasks/T-023-Release-readiness.md) |
| E-27 | Extractor test contract (`_TESTS`, `test/test_download.py` matchers: `md5:`, `int`, `str`, `re:`, `only_matching`) | Harness runs upstream-style cases from Kotlin fixtures; live opt-in. | D4 | [T-059](../06-tasks/T-059-Extractor-test-harness.md) |
| E-28 | Differential check against upstream | Opt-in `yt-dlp -J` oracle on desktop compares normalized fields. | D4 | [T-062](../06-tasks/T-062-Desktop-ytdlp-oracle.md) |

## Extractor coverage

Generated by `./gradlew :tools:port-manifest:run` from `port/manifest.json` ([T-055](../06-tasks/T-055-Port-manifest-and-equivalence-matrix.md)). Do not edit the block between the markers by hand.

Counting rule: an extractor is **Ported** when its `_real_extract` behavior for the URL forms listed in the manifest is translated and its harness cases pass; **Partial** when a named subset is translated (the manifest names it); **Planned** when a task exists; otherwise **Not started**. A count is never rounded up.

Known before the generator exists (2026-09-24): upstream `_extractors.py` names **1,751** extractor classes at the pin. Ported: 0. Partial: 1 (`GenericIE`, HTML5 media subset only, [T-045](../06-tasks/T-045-Generic-extractor-subset.md)). Planned: 1 (`YoutubeIE`, single video). Everything else: not started.

<!-- port-manifest:coverage:start -->
_Not generated yet. Run T-055._
<!-- port-manifest:coverage:end -->

## Named priorities after YouTube

Owner, 2026-09-24: **X / Twitter** (`extractor/twitter.py`, 1,785 lines at the pin) is the next named site. It is roadmap input for the phase after D4, not D4 scope. Other sites are chosen when a phase is planned.

## How to use this matrix

1. Keep E-IDs stable. Add rows when upstream gains a capability the port must answer; mark rows Out with a reason rather than deleting them.
2. A task that ports a module updates `port/manifest.json` in the same change and regenerates the coverage block.
3. Statuses change only with linked evidence in the owning task. "Compiles" is not evidence; a harness or host test is.
4. The pin moves only at a phase boundary and is recorded in the ADR for that phase, the manifest, and every notice header the change touches.
