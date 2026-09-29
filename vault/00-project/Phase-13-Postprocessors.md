---
type: phase
status: planned
milestone: D13
tags: [project, engine, media]
---

# Phase 13 — Postprocessors

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 10 engineer-days.** Depends on D12. Upstream `postprocessor/` is 101 KB at the pin, mostly `ffmpeg.py` (48 KB) and `modify_chapters.py` (17 KB).

## Done looks like

Captions can be written as SRT, VTT, TTML, and TXT. JPG thumbnails can be saved as sidecars. Thumbnail, metadata, and subtitle embedding run where the host toolkit can do them. Desktop keeps FFmpeg on `PATH`. Android MediaMuxer and iOS AVFoundation embed only what those APIs can do. MP3, WAV, and FLAC stay disabled on mobile. Clip ranges, chapter split, and opt-in SponsorBlock work on hosts that can cut. Web fails typed when a merge, embed, or clip is required. E-17, E-18, E-19, and E-20 record that host table.

## Tasks

[T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md) and [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md).
