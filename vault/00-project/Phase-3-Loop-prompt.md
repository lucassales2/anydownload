---
type: prompt
milestone: D3
tags: [project, engine, handoff]
---

# Phase 3 loop prompt

[Phase 3](Phase-3-Generic-Extractor.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session. It implements Phase D3 one task at a time until [T-051](../06-tasks/T-051-Phase-3-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D3, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-3-Generic-Extractor.md
- vault/03-decisions/ADR-007-Generic-extractor-phase.md
- vault/03-decisions/ADR-006-Local-http-engine-phase.md
- vault/03-decisions/ADR-004-Local-kotlin-engine.md

ADR-004 is the end state. This phase does not add a backend. First finish the home screen: the idle screen shows only the paste-link field, the app does not read the clipboard on its own and does not show the clipboard permission dialog, a compatible HTTP(S) link opens the metadata preview, and that preview has Download plus a collapsible Edit for video or audio, quality, and format. Then translate one subset of yt-dlp's generic extractor. The subset reads a simple HTML page and returns a single video/audio/source URL; the existing HTTP engine downloads that file on iOS, Android, web, and desktop. Web fetches only through the browser extension; the Compose/Wasm page never fetches arbitrary origins. Desktop keeps the installed yt-dlp CLI for URLs this extractor does not resolve. Android keeps Chaquopy for those URLs, under apps/android only. Common code has no ProcessBuilder and no Python. Do not merge, transmux, or extract audio. Do not port YouTube or yt-dlp-ejs. Do not vendor generic.py. Translate only the subset, with an Unlicense notice and the upstream revision. Do not copy MeTube, NewPipe, or YtDlp-kt. Options stay an allowlist; leave the custom yt-dlp JSON field disabled. Do not commit unless a task explicitly tells you to. Never write real cookies, credentials, or private media URLs into the vault or the repo.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D3 (T-052, T-053, T-054, and T-045 through T-051).
2. If a D3 card is In progress, resume that task. Otherwise take the Ready D3 card. If none is Ready, take the first Backlog D3 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D3 card is eligible, or T-051 is already Done, stop. Summarize which D3 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D3 task.

Do not mark T-051 done while any earlier D3 task is not Done. Do not start T-007, T-008, T-009, T-010, T-037, or any M2/M3 card as part of this loop. Do not vendor yt-dlp or FFmpeg binaries. Do not fetch YouTube from the web page. Do not bring back the clipboard permission dialog or an automatic clipboard read.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
