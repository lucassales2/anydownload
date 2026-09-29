---
id: T-131
type: task
priority: P0
milestone: D22
tags: [task, engine, release]
---

# T-131 — Remove the yt-dlp and Chaquopy fallbacks

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 22](../00-project/Phase-22-Remove-ytdlp-fallback.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

Desktop and Android no longer call yt-dlp for an unmatched URL. Estimate is inside the 8 days of D22, before the audit and the portfolio builds.

## Dependencies

- [T-130](T-130-Small-extractors-b.md).

## Acceptance criteria

- [ ] `DesktopRoute.YTDLP_CLI` is gone. An unmatched desktop URL fails typed.
- [ ] `AndroidRoute.CHAQUOPY` is gone. An unmatched Android URL fails typed.
- [ ] The opt-in desktop `yt-dlp -J` oracle test still exists and is not on the download path.
- [ ] Common code still has no `ProcessBuilder`.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). [T-022](T-022-Parity-audit.md) runs after this task.
