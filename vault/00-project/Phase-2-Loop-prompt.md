---
type: prompt
milestone: D2
tags: [project, engine, handoff]
---

# Phase 2 loop prompt

[Phase 2](Phase-2-Local-Kotlin-Engine.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session. It implements Phase D2 one task at a time until [T-044](../06-tasks/T-044-Phase-2-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D2, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-2-Local-Kotlin-Engine.md
- vault/03-decisions/ADR-006-Local-http-engine-phase.md
- vault/03-decisions/ADR-004-Local-kotlin-engine.md

ADR-004 is the end state. This phase does not port yt-dlp extractors and does not add a backend. First slice is a direct HTTP(S) file download on iOS, Android, web, and desktop. Web downloads only through a browser extension with host permissions; the Compose/Wasm page never fetches arbitrary origins. Android may add Chaquopy under apps/android only, after T-006 records those licenses. Desktop keeps the installed yt-dlp CLI for non-direct URLs. Common code has no ProcessBuilder and no Python. Options stay an allowlist; leave the custom yt-dlp JSON field disabled. Do not copy MeTube, NewPipe, or YtDlp-kt. Do not translate extractor source in this phase. Do not commit unless a task explicitly tells you to. Never write real cookies, credentials, or private media URLs into the vault or the repo.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D2 (T-003, T-006, T-038 through T-044). T-004 is closed by T-044, not picked up on its own.
2. If a D2 card is In progress, resume that task. Otherwise take the Ready D2 card. If none is Ready, take the first Backlog D2 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D2 card is eligible, or T-044 is already Done, stop. Summarize which D2 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D2 task.

Do not mark T-044 done while any earlier D2 task is not Done. Do not start T-007, T-008, T-009, T-010, T-037, or any M2/M3 extractor/parity card as part of this loop. Do not vendor yt-dlp or FFmpeg binaries on desktop. Do not fetch YouTube from the web page.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.
```
