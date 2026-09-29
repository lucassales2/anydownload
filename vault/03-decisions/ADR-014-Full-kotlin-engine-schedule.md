---
id: ADR-014
type: adr
status: accepted
created: 2026-09-29
tags: [architecture, decisions, engine, schedule]
---

# ADR-014 — Full Kotlin engine schedule

[Home](../Home.md) · [Decision log](Decision-log.md) · [Full engine schedule](../00-project/Full-engine-schedule.md) · [ADR-004](ADR-004-Local-kotlin-engine.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state. Does not supersede [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md) through [ADR-012](ADR-012-On-device-core-phase.md). Does not supersede [ADR-013](ADR-013-Metro-graphs-and-viewmodels.md): Metro stays Phase D9 and keeps tasks T-113–T-122. This schedule starts at D10, after [T-120](../06-tasks/T-120-Metro-verification.md) is Done.

## Context

D1–D8 are done. The Kotlin engine downloads a tested slice: direct files, a generic HTML5 subset, YouTube single video plus a playlist first page, a public X status video, non-live HLS and static DASH, one merge where a toolkit exists, and the spotDL workflow. Desktop still sends every unmatched URL to the installed `yt-dlp`. Android still sends those URLs to Chaquopy. iOS and web fail them.

The [equivalence matrix](../01-product/Ytdlp-equivalence.md) at pin `2026.08.19` counts **0 ported, 5 partial, 1,746 not started** of 1,751 extractor classes. The open M3 cards (T-015–T-020, T-022, T-023) describe MeTube workflows. They do not name the rest of the catalog.

Upstream `yt_dlp/extractor/` at tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), inspected 2026-09-29 from the Git tree, is 976 files and 8.05 MB. The YouTube package is 36 files and 541 KB. `twitter.py` is 76 KB. The other catalog is 935 files and 6.9 MB: 679 under 8 KB, 220 from 8–25 KB, 32 from 25–80 KB, and 4 at or above 80 KB (Bilibili, Vimeo, PeerTube, BBC).

The owner asked on 2026-09-29 for the remaining work to be split into phases and for the time to be calculated, then written into this vault.

## Decision

- **The plan of record is [Full engine schedule](../00-project/Full-engine-schedule.md).** Phases D10–D22, tasks T-123–T-131 plus the retargeted M3 and M4 cards. Nothing in D10 starts until T-120 is Done.
- **Estimate: 614 engineer-days** for one person at the sustained rate defined in the schedule note. That is 123 five-day weeks, about 28 months. The raw phase sum is 534 days. The other 80 days (15%) are rework for helpers the long tail exposes. A YouTube-complete engine is 39 days (D10–D13). Priority sites are 90 days (through D16).
- **The pin stays `2026.08.19`** until a phase boundary moves it. The 614 days match that snapshot. After D22, staying current is a separate 2–5 days a month. That retainer is not inside the 614.
- **Desktop `YTDLP_CLI` and Android `CHAQUOPY` stay until D22.** A URL the registry matches never falls through, which is already the rule. Unmatched sites keep the fallback so the app still downloads them while the catalog is ported. D22 deletes both branches. The desktop `yt-dlp -J` oracle stays an opt-in test.
- **Rates, once D10’s helpers exist:** 4 small files a day, 1 medium file a day, 2 days for a large file, 4 days for a huge file, 3 days to finish Twitter, 15 days to finish YouTube at the pin. A sustained day is about half of the 2026-09-24 / 2026-09-25 burst (D4, and D5–D7 on one day). Each day includes the fixture harness, the manifest, and the four-host test bar.
- **Out of the 614, on purpose:** legacy `jsinterp`, HTTP impersonation, plugins, exec hooks, free-form options (Q-09), and a web merge toolkit. Web merge, embed, and clip stay a typed failure. iOS stays foreground-only. Mobile MP3/WAV/FLAC stay disabled until a platform encoder is proven.
- **Two catalog streams after D15** can cut the catalog roughly in half and bring the calendar near 16 months. D10–D15 stay serial.

## Alternatives

- **Keep the M3 cards as the whole remaining plan.** Rejected: they never mention the 935 site files, so "full engine" would be declared done with 1,746 classes unstarted.
- **Number this work D9.** Rejected: D9 and T-113–T-122 and ADR-013 are the Metro migration.
- **One Kanban card per extractor class.** Rejected: 1,746 cards. Catalog tasks are file batches. Each batch still updates `port/manifest.json` per class.
- **Delete the CLI and Chaquopy at D10.** Rejected: every unported site would fail on desktop and Android for the rest of the schedule.
- **Treat the September burst as the planning rate.** Rejected: three phases on 2026-09-25 is not a rate to promise. The schedule uses the slower sustained day and records the burst as a lower bound of about 14 months.

## Consequences

M3 and M4 cards stay, with a phase id. New cards T-123–T-131 cover the engine work those cards do not name. The roadmap’s current engine priority, after Metro, is D10. T-022 cannot pass while the catalog count is unfinished; it closes D22.

Risks: the file-size rates assume D10 finishes the helpers those files call; the 80-day buffer is where that assumption is paid. YouTube at the pin can still break in production before D22. The simulator may still be unable to reach YouTube; fixture evidence remains the iOS bar, as in T-074. A login wall or DRM wall is Partial with that sentence in the manifest, and it still counts as finished for the schedule.

## Validation / approval

Accepted 2026-09-29 as the plan of record. The owner asked for the remaining Kotlin engine to be phased, timed, written into the vault, and committed. This acceptance is the schedule and the estimate. It does not mark any D10–D22 task Done. Each phase is Done only when its task evidence is filled and its Kanban card moves.
