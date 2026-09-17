---
id: T-013
type: task
priority: P1
milestone: M2
tags: [task, downloads, formats]
---

# T-013 — Video profiles and audio extraction

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Tested video/codec/quality profiles and M4A/MP3/Opus/WAV/FLAC audio output for F-02/F-03.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [ ] Match reviewed Auto/MP4/iOS-compatible, codec preference and resolution/best/worst controls with explicit fallback behavior.
- [ ] Implement format-appropriate audio quality choices and FFmpeg extraction/conversion; distinguish source selection, muxing and transcoding.
- [ ] Validate actual codec/container/bitrate using output probes; do not promise native playback of every output on every target.
- [ ] Test missing formats/FFmpeg, unsupported combinations, unknown sizes, postprocessing failure and cancellation.

## Evidence / notes

Not started. Artwork/metadata/captions are expanded in T-015; library tagging is not implied.
