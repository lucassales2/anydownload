---
type: phase
status: planned
milestone: D10-D22
tags: [project, engine, schedule]
---

# Full Kotlin engine schedule

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md) · [Phase 8](Phase-8-On-device-core.md)

**Start here for everything after Metro.** D1–D8 are done. [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) is Phase D9 (tasks T-113–T-122). This note is D10–D22: the work that makes the Kotlin engine match yt-dlp `2026.08.19` without calling yt-dlp or Chaquopy. Decision: [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md), accepted 2026-09-29 as the plan of record.

## Estimate

**614 engineer-days. One person, five days a week: 123 weeks, about 28 months.**

| Milestone | Days | Calendar |
| --- | ---: | --- |
| D10–D13. YouTube finished, cookies, subs, clips. CLI only for other sites | 39 | 8 weeks |
| D15. MeTube workflows on that engine | 53 | 11 weeks |
| D16. Priority sites | 90 | 18 weeks |
| D22 raw sum | 534 | |
| D22 plus 15% rework | 614 | 28 months |
| Same work at the 2026-09-24/25 burst rate | ~307 | ~14 months |
| Two catalog streams after D15, sustained rate | ~340 | ~16 months |
| Staying current after the pin | 2–5 per month | no end date |

The 2–5 days a month are not inside the 614.

## How the days were counted

Inspected 2026-09-29, tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`. `yt_dlp/extractor/` is 976 files, 8.05 MB. YouTube is 36 files, 541 KB, and only a single-video slice is ported. `twitter.py` is 76 KB, and only the public status video is ported. The other catalog is 935 files, 6.9 MB:

- Small, under 8 KB: 679 files
- Medium, 8–25 KB: 220 files
- Large, 25–80 KB: 32 files
- Huge, at or above 80 KB: 4 files (Bilibili 111 KB, Vimeo 98 KB, PeerTube 82 KB, BBC 80 KB)

Calibration from this repo. D4 (2026-09-24) landed the extractor core, a YouTube single-video partial, EJS, and HLS/DASH. D7 (2026-09-25) landed one class of `twitter.py` on four hosts. D5, D6, and D7 on the same day count as a burst, about two sustained days. A sustained day includes the fixture harness, `port/manifest.json`, and the four-host test bar.

Once D10’s helpers exist:

- Small: 4 files a day → 170 days
- Medium: 1 day → 220 days
- Large: 2 days
- Huge: 4 days
- Finish Twitter: 3 days
- Finish YouTube at the pin: 15 days

Raw phase sum **534**. Rework buffer **80** (15%), spent in D17–D21. Total **614**.

## Rules for every D10–D22 task

- No backend, app login, or MeTube protocol client. No `ProcessBuilder` or Python in common code.
- Do not vendor yt-dlp. Do not copy MeTube, NewPipe, or YtDlp-kt. A translation carries the Unlicense notice and the pin `2026.08.19`.
- The pin moves only at a phase boundary, recorded in that phase’s note.
- Cookies, signed media URLs, bearer tokens, and private URLs stay out of logs, history, fixtures, and this vault.
- A class is Ported only when its `_real_extract` URL forms pass the harness. A login wall or DRM wall is Partial, with that sentence in the manifest, and it still counts as finished for the schedule.
- Desktop CLI and Android Chaquopy stay for URLs the registry does not match, until D22.
- Web merge, embed, and clip fail typed. iOS stays foreground-only. Mobile MP3/WAV/FLAC stay disabled.
- Impersonation, `jsinterp`, plugins, exec hooks, and free-form options stay out (E-12, E-23, E-24, Q-09).
- Do not commit a media file. Do not commit unless a task explicitly tells you to.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card.

## Phase order

Do them in this order. D10 does not start before [T-120](../06-tasks/T-120-Metro-verification.md) is Done.

| Phase | Days | Task | Delivers |
| --- | ---: | --- | --- |
| [D10](Phase-10-Shared-downloader.md) | 8 | [T-123](../06-tasks/T-123-Shared-downloader.md) | Format lists, HTTP resume, fragment concurrency, generic extractor, helper remainder |
| [D11](Phase-11-Cookies.md) | 6 | [T-018](../06-tasks/T-018-Cookie-lifecycle.md) | On-device Netscape cookie jar on extractor requests |
| [D12](Phase-12-Youtube-done.md) | 15 | [T-124](../06-tasks/T-124-Youtube-done.md) | The 21 `Youtube*` classes at the pin |
| [D13](Phase-13-Postprocessors.md) | 10 | [T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md), [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md) | Subs, thumbnails, embeds, clips, chapters, SponsorBlock |
| [D14](Phase-14-Options-archive.md) | 8 | [T-017](../06-tasks/T-017-Options-and-presets.md) | Presets, output templates, download archive, proxy and rate limit |
| [D15](Phase-15-Subscriptions-sharing.md) | 6 | [T-019](../06-tasks/T-019-Subscriptions.md), [T-020](../06-tasks/T-020-Sharing-and-UX.md) | Subscriptions and share entry points |
| [D16](Phase-16-Priority-sites.md) | 37 | [T-125](../06-tasks/T-125-Priority-sites.md) | Twitter finished, plus 13 high-traffic files |
| [D17](Phase-17-Remaining-large-sites.md) | 46 | [T-126](../06-tasks/T-126-Remaining-large-sites.md) | The other 23 large files |
| [D18](Phase-18-Medium-extractors-a.md) | 110 | [T-127](../06-tasks/T-127-Medium-extractors-a.md) | First 110 medium files |
| [D19](Phase-19-Medium-extractors-b.md) | 110 | [T-128](../06-tasks/T-128-Medium-extractors-b.md) | Remaining 110 medium files |
| [D20](Phase-20-Small-extractors-a.md) | 85 | [T-129](../06-tasks/T-129-Small-extractors-a.md) | 340 small files |
| [D21](Phase-21-Small-extractors-b.md) | 85 | [T-130](../06-tasks/T-130-Small-extractors-b.md) | Remaining 339 small files |
| [D22](Phase-22-Remove-ytdlp-fallback.md) | 8 | [T-131](../06-tasks/T-131-Remove-ytdlp-fallback.md), [T-022](../06-tasks/T-022-Parity-audit.md), [T-023](../06-tasks/T-023-Release-readiness.md) | Delete the CLI and Chaquopy fallbacks, audit, portfolio builds |

Cumulative raw days: D13 = 39, D15 = 53, D16 = 90, D22 = 534. Plus 80 = 614.

## What already runs without yt-dlp

Matched URLs already go to the Kotlin engine before any probe. That path stays: direct HTTP files, generic HTML5 media, YouTube single video (visionos and web, with yt-dlp-ejs 0.8.0), YouTube `/playlist?list=` first page capped at 50, public X status videos, non-live HLS, static DASH, format sorting, one `bv*+ba/b` merge on desktop, Android, and iOS, and the spotDL workflow. D10–D22 extend that path. They do not replace it.
