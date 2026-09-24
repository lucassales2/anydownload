---
id: T-058
type: task
priority: P0
milestone: D4
tags: [task, engine, kmp, formats]
---

# T-058 — Format-spec selector and typed-options compiler

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

The port chooses formats the way yt-dlp does. A translated format-spec parser and format sorter pick from an `InfoDict`; the app's typed `DownloadOptions` compile to a spec. In D4 a merge is never requested. Covers E-05 and E-06.

## Dependencies

- [T-057](T-057-Extractor-core.md) — `MediaFormat` is the input.

## Context the next session needs

Upstream: `YoutubeDL.build_format_selector` (parser, `SINGLE`, `GROUP`, `PICKFIRST`, `MERGE`, filters), `utils/_utils.py` `FormatSorter`. `DownloadOptions` today carries `mediaType`, `videoProfile`, `videoCodec`, `quality`, `audioContainer`, `audioBitrate`. The domain never stores a spec string; the compiler is the only place that builds one. Package `com.anydownlod.core.format`.

## Work

- `FormatSpec` parser: tokens, `best`/`worst`/`bestvideo*`/`bestaudio`/`b`/`bv*`/`ba`, explicit format ids, `[key op value]` filters (`height<=720`, `ext=mp4`, `vcodec^=avc1`, `filesize<100M`, `!=`, `~=`), `/` fallback, `,` multiple, `()` groups, and `+` merge nodes. Translate from upstream with the notice.
- `FormatSorter`: fields `hasvid, hasaud, ie_pref, lang, quality, res, fps, hdr, vcodec, channels, acodec, size, br, asr, proto, ext, source, id`, the default order, `+`/`-` reversal and `:` limits, `res:720` style caps. Translate the field tables.
- `FormatSelector.select(infoDict, spec, sort): Selection` where `Selection` is `Single(format)`, `Merge(video, audio)`, or `None`. In D4 the engine rejects `Merge` with `UNSUPPORTED_FORMAT` and a message naming the media toolkit as not built.
- `OptionsToSpec.compile(options): CompiledSpec`:
  - `VIDEO`: `b[height<=N]` when `quality` is a resolution, `b` for best, `w` for worst; codec preference becomes a sort key (`+vcodec:avc1` etc.); `MP4`/`IOS_COMPATIBLE` add `[ext=mp4][vcodec^=avc1]` with fallback to `b`; never `bv*+ba`.
  - `AUDIO`: `ba[ext=m4a]` for `M4A`, `ba[acodec=opus]` for `OPUS`, `ba` when the container is null; `MP3`, `WAV`, `FLAC` compile to a typed `NeedsToolkit(container)` result, never to a spec.
  - Captions and thumbnail media types compile to `NotInPhase`.
- Fixtures: translate a subset of `test/test_YoutubeDL.py` format-selection cases (Unlicense) into Kotlin tests using synthetic format lists.

## Acceptance criteria

- [x] Parser and selector tests pass for `best`, `worst`, ids, filters, fallbacks, groups, and a `+` node that yields `Merge`.
- [x] Sorter tests reproduce upstream ordering on the translated cases.
- [x] Compiler tests: every `DownloadOptions` combination yields a single-file spec, `NeedsToolkit`, or `NotInPhase`; no compiled spec contains `+`.
- [x] Notice headers name `YoutubeDL.py` and `utils/_utils.py` at the pin; manifest updated.

## Evidence / notes

Done 2026-09-24.

- New `com.anydownlod.core.format` files, each with the Unlicense header and the pin (`yt-dlp` tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`): `FormatSpec.kt` (tokenizer, upstream grammar — SINGLE/GROUP/PICKFIRST/MERGE, `/` fallback, `,` lists, `+` merge, `()` groups, `[key op value]` filters, `parseFilesize`), `FormatSorter.kt` (the default order, `+`/`-` reversal, `:` caps and `~` closest, the ordered vcodec/acodec/hdr/proto/vext/aext tables, the upstream preference tuple, and `_fill_sorting_fields` derivations), `FormatSelector.kt` (`Selection.Single`/`Merge`/`None`, `best`/`worst` reversal, type/`*` filters, `.N`, incomplete-format fallback, DRM dropped, `/` fallback), and `OptionsToSpec.kt` (`CompiledSpec.SingleFile`/`NeedsToolkit`/`NotInPhase`).
- `MediaFormat` gained the upstream fields the sorter needs: `preference`, `audioChannels`, `dynamicRange`, `languagePreference`; the format model stays the T-057 shape otherwise.
- Compiler mapping: VIDEO `b`/`w`/`b[height<=N]`, `MP4`/`IOS_COMPATIBLE` add `[ext=mp4][vcodec^=avc1]` with a `b` fallback, codec preferences become sort keys (`vcodec:avc`/`h265`/`av01`/`vp9`); AUDIO `ba`, `ba[ext=m4a]`, `ba[acodec=opus]`; MP3/WAV/FLAC return `NeedsToolkit`; captions/thumbnails return `NotInPhase`; no compiled text contains `+`.
- Manifest: `YoutubeDL.py` added as partial (selector grammar and filter), `_utils.py` partial now includes the `FormatSorter` tables and `parseFilesize`; `./gradlew :tools:port-manifest:run` regenerated the coverage block; `NOTICE.md` records both upstream paths. `YoutubeDL.py` is not vendored.
- Tests: `FormatSpecTest` 9 (atoms, merge/fallback associativity, choices, groups, implicit `best`, numeric/string filters, units, malformed specs), `FormatSorterTest` 8 (defaults, res/fps, vcodec/acodec tables, `+`/`:`/`~`, `vp9.2` limit, id/ext, derived bitrates, missing values), `FormatSelectorTest` 11 (upstream `TestFormatSelection` cases: best/worst, ids, picks, audio/video-only, fallback, filters, string operators, merge, incomplete formats, DRM, comma), `OptionsToSpecTest` 7 (every profile/codec/quality combination, audio containers, toolkit/not-in-phase, selection on a synthetic info dict).
- Verification: `./gradlew :shared:core:jvmTest` → 204 tests, 0 failures; `:apps:desktop:test` and `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; `:shared:core:compileKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin`, `:shared:core:compileKotlinIosSimulatorArm64` green; `:tools:port-manifest:check` green (coverage block matches the manifest).
