---
id: T-015
type: task
priority: P1
milestone: M3
tags: [task, media, parity]
---

# T-015 — Captions, thumbnails and metadata

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Standalone captions/artwork and format-appropriate embedding/sidecars for F-14/F-15.

## Dependencies

- [T-013](T-013-Media-formats.md).
- [T-014](T-014-Storage-and-delivery.md).

## Acceptance criteria

- [ ] Support caption language and manual/auto/fallback preferences, SRT/TXT/VTT/TTML with truthful conversion/fallback reporting.
- [ ] Support JPG thumbnails, appropriate audio artwork/metadata, optional subtitle embedding and media/feed info/thumbnail sidecars.
- [ ] Register/deliver every output artifact and document WAV/container and missing-caption/artwork limitations.
- [ ] Test Unicode languages/metadata, unavailable tracks, playlist/channel sidecars, postprocessing failure and playable/probe-verified outputs.

## Evidence / notes

Not started. This task does not look up Spotify or any other music catalog. Embedding title, artists, album, and artwork for a Spotify match is [T-037](T-037-Spotify-youtube-match.md), which depends on this task.
