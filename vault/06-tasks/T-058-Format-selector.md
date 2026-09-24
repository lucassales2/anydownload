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

- [ ] Parser and selector tests pass for `best`, `worst`, ids, filters, fallbacks, groups, and a `+` node that yields `Merge`.
- [ ] Sorter tests reproduce upstream ordering on the translated cases.
- [ ] Compiler tests: every `DownloadOptions` combination yields a single-file spec, `NeedsToolkit`, or `NotInPhase`; no compiled spec contains `+`.
- [ ] Notice headers name `YoutubeDL.py` and `utils/_utils.py` at the pin; manifest updated.

## Evidence / notes

Not started.
