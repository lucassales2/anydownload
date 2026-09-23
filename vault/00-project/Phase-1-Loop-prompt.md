---
type: prompt
milestone: D1
tags: [project, desktop, handoff]
---

# Phase 1 loop prompt

[Phase 1](Phase-1-Desktop-MeTube.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session. It implements Phase D1 one task at a time until [T-036](../06-tasks/T-036-Phase-1-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D1, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-1-Desktop-MeTube.md
- vault/03-decisions/ADR-005-Desktop-metube-phase.md

ADR-004 is the later end state. Do not start it. Do not port yt-dlp to Kotlin. Do not add Android, iOS, or web download behavior. Do not copy MeTube or yt-dlp source into the repo. Do not vendor yt-dlp or ffmpeg. ProcessBuilder stays inside apps/desktop. Leave shared/network unused. Leave the custom yt-dlp JSON field disabled. Do not commit unless a task explicitly tells you to. Never write real cookies, credentials, or private media URLs into the vault or the repo.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D1 (T-026 through T-036).
2. If a D1 card is In progress, resume that task. Otherwise take the Ready D1 card. If none is Ready, take the first Backlog D1 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D1 card is eligible, or T-036 is already Done, stop. Summarize which D1 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the desktop click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D1 task.

Do not mark T-036 done while any earlier D1 task is not Done. Do not start T-003, T-004, T-006, T-007, T-008, or T-009 as part of this loop.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.
```
