---
id: ADR-009
type: adr
status: accepted
created: 2026-09-25
tags: [architecture, decisions, engine, platforms, toolkit]
---

# ADR-009 — Media toolkit: merge and audio extract, web stays a gap

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md) · [ADR-007](ADR-007-Generic-extractor-phase.md) · [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state. Does not supersede [ADR-007](ADR-007-Generic-extractor-phase.md) or [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md). Direct files, the generic subset, and YouTube single video stay on the shared engine. This record builds the toolkit ADR-007 named and left unbuilt.

## Context

Phase D4 is done (verified 2026-09-24, [T-074](../06-tasks/T-074-Phase-4-verification.md)). The shared engine extracts a YouTube single video and downloads one single-file format on every host family. A selection that needs a merge or a transcode fails typed (`UNSUPPORTED_FORMAT`). MP3, WAV, and FLAC stay disabled in Edit. Best-quality YouTube video is usually a separate video stream plus a separate audio stream, so that picture is unreachable until a toolkit exists.

Owner, 2026-09-25: Phase D5 is the media toolkit. Later the same day, [ADR-010](ADR-010-Spotdl-parity-phase.md) placed spotDL parity after this phase. X/Twitter follows that.

Upstream pin checked the same day: the latest yt-dlp release is still `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`). The pin does not move.

ADR-007 already chose the tools:

- Desktop: `ffmpeg` and `ffprobe` already on `PATH`. Do not vendor them.
- Android: platform `MediaMuxer` / `MediaExtractor`. No bundled FFmpeg.
- iOS: `AVFoundation`. No subprocess.
- Web: no merge and no audio extract. Documented gap.

## Proposed decision

- **Phase D5** is the next implementation work. Tasks are [T-075](../06-tasks/T-075-Media-toolkit-contract.md) through [T-083](../06-tasks/T-083-Phase-5-verification.md), sequenced in the [phase note](../00-project/Phase-5-Media-Toolkit.md). [T-079](../06-tasks/T-079-Gate-desktop-merge.md) is a mid-phase gate: a desktop merge of a local split-stream fixture, and web still refusing that choice, before the Android and iOS adapters.
- **Shared contract.** `com.anydownlod.core.postprocess.MediaToolkit` lives in common code: `merge` of one video file and one audio file, `extractAudio` to a named container, and `capabilities()` (can merge, which audio containers). A missing toolkit is a typed `UNSUPPORTED_FORMAT`. Common code has no `ProcessBuilder`.
- **Merge copies streams.** It does not re-encode. If the destination container cannot hold the codecs, the job fails typed. The engine downloads both sides to temp files, calls the toolkit, and publishes one artifact. Cancel deletes the temps.
- **Video selection.** When `capabilities().canMerge` is true, typed video options compile to `bv*+ba/b` (best separate streams, falling back to the best single file). When it is false, they keep today's single-file `b`. The compiler still emits at most one `+`.
- **Audio.** A stream that is already M4A or Opus is copied. Desktop FFmpeg may transcode to MP3, WAV, and FLAC, and `ffprobe` checks the output. Android and iOS do not gain an MP3 or FLAC encoder in this phase; those choices stay disabled there. WAV is enabled on a mobile host only if that host's task proves a platform PCM export.
- **Web** keeps every merge and transcode choice disabled. The disabled reason names the host gap. The page does not run FFmpeg.
- **Out of D5:** captions, thumbnails, chapter split, SponsorBlock, embed metadata, clips, playlists, cookies, spotDL parity ([ADR-010](ADR-010-Spotdl-parity-phase.md)), X/Twitter, bundling FFmpeg, and retiring the desktop CLI or Chaquopy. [T-013](../06-tasks/T-013-Media-formats.md) stays open because F-02 and F-03 are not fully met on web or for every mobile codec.

## Alternatives

- Bundle FFmpeg on Android and iOS so every host transcodes the same set. Rejected: ADR-007 already chose platform APIs, and a bundled binary pulls LGPL/GPL and patent questions ([security and licensing](../04-delivery/Security-and-licensing.md)) into a portfolio build.
- Wasm FFmpeg in the browser. Rejected for this phase: the web gap is recorded, and a wasm build is its own size and licensing decision.
- Leave merge on the desktop CLI and Chaquopy only. Rejected: matched YouTube URLs already stay off those fallbacks, so best-quality video would never run on the Kotlin path.
- Translate X/Twitter in this phase. Rejected by the owner on 2026-09-25: the toolkit unblocks the YouTube quality D4 cannot save.

## Consequences

D5 ends with one playable file from a split video+audio pair on desktop, Android, and iOS, and an honest disabled state on web. Desktop can also produce MP3, WAV, and FLAC. A missing `ffmpeg` fails typed instead of silently saving one stream. The format compiler grows a single merge. Postprocessing progress is a phase name, not an invented percent.

Risks: platform muxers reject codec pairs FFmpeg would remux, so the same URL can succeed on desktop and fail typed on a phone; that difference is the capability query, not a silent downgrade. `ffmpeg` on `PATH` is an untrusted binary the desktop app already probes. Temp files holding media must be deleted on cancel and on failure.

## Validation / approval

Accepted 2026-09-25 from the owner's choice of the media toolkit as Phase 5. D5 is complete: [T-083](../06-tasks/T-083-Phase-5-verification.md) recorded the four-host capability matrix, and [Phase 5](../00-project/Phase-5-Media-Toolkit.md) is marked done. The upstream pin did not move.
