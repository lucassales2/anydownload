---
type: prompt
milestone: D8
tags: [project, queue, handoff]
---

# Phase 8 loop prompt

[Phase 8](Phase-8-On-device-core.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session after Phase D7 is verified. It implements Phase D8 one task at a time until [T-112](../06-tasks/T-112-Phase-8-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D8, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-8-On-device-core.md
- vault/03-decisions/ADR-012-On-device-core-phase.md
- vault/02-architecture/Download-lifecycle.md
- vault/01-product/Feature-parity.md

The phase closes the remaining M1 and M2 work. D1 through D7 stay. Do not rebuild the engine, the extractors, or the Compose screens.

- T-101 — Write the on-device contract from the code that already exists. Close T-007.
- T-102 — Shared job document: codec, interrupt-on-load, stale revision, clear-completed. Desktop still uses its state directory.
- T-103 — Android, iOS, and web load and save that document. The state file is not the download root. Web uses localStorage for metadata only.
- T-104 — Concurrency, cancel-versus-complete, retry does not duplicate a finished file. Not-yet-available becomes SCHEDULED. No countdown and no live stream.
- T-105 — Gate. Desktop: one fixture URL, one failed URL, restart restores the row and does not finish a second copy of a completed file.
- T-106 — Gate. The same journey on Android, iOS, and web. Close T-008, T-009, and T-010.
- T-107 — Playlist entries become child jobs. Cap 50. Limit 0 means 50. Cancel keeps children already created. An unavailable entry is a failed child.
- T-108 — YoutubeTabIE partial: /playlist?list= from a redacted fixture. Flat video id and title. No channel, mix, cookie, or live. Child downloads use the existing YouTube video path.
- T-109 — Copy or export the URLs of a batch or playlist on an explicit action.
- T-110 — Safe file names, collisions, path escape rejected. Remove history does not delete the file. Delete file does not drop the row. Cancel does not delete a finished file. Disk failure is typed.
- T-111 — Video and audio profile controls follow the D5 host capabilities. No new encoder. Close T-013.
- T-112 — Four-host M2 table, manifest, equivalence rows, Roadmap, Home, decision log. Close T-011, T-012, and T-014. Accept ADR-012.

T-103 depends on T-102. T-104 depends on T-102. T-105 depends on T-103 and T-104. T-106 depends on T-105. T-107 depends on T-106. T-108 depends on T-107. T-109 depends on T-107. T-110 depends on T-106. T-111 depends on T-106. T-112 depends on T-108, T-109, T-110, and T-111.

ADR-004 is the end state. ADR-012 is the plan the owner directed on 2026-09-29. Active work restored from disk becomes FAILED, ENGINE_UNAVAILABLE, retryable. Do not auto-resume. Do not buffer a whole media file. Playlist expansion hard-stops at 50. Common code has no ProcessBuilder. Do not shell out to yt-dlp for a URL the registry matches. Do not vendor a YouTube file. Never write cookies, signed media URLs, or private URLs into fixtures, logs, history, the vault, or the repo. The pin stays 2026.08.19. Do not commit a media file. Do not commit unless a task explicitly tells you to.

If T-100 is not Done, stop. D8 does not start before Phase D7 is verified.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D8 (T-101 through T-112).
2. If a D8 card is In progress, resume that task. Otherwise take the Ready D8 card. If none is Ready, take the first Backlog D8 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D8 card is eligible, or T-112 is already Done, stop. Summarize which D8 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done. If the task says to close an older milestone card, move that card to Done in the same step and check only the boxes the task says are met.
8. Go back to step 1 for the next eligible D8 task.

Do not start T-107 or any later D8 task while T-106 is not Done. Do not mark T-112 done while any earlier D8 task is not Done. Do not start T-015, T-016, T-017, T-018, T-019, T-020, T-022, or T-023 as part of this loop. Do not port another site. Do not add cookies, captions, clips, or live streams.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
