---
type: prompt
milestone: D5
tags: [project, engine, handoff]
---

# Phase 5 loop prompt

[Phase 5](Phase-5-Media-Toolkit.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session. It implements Phase D5 one task at a time until [T-083](../06-tasks/T-083-Phase-5-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D5, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-5-Media-Toolkit.md
- vault/03-decisions/ADR-009-Media-toolkit-phase.md
- vault/03-decisions/ADR-007-Generic-extractor-phase.md
- vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md
- vault/01-product/Ytdlp-equivalence.md

ADR-004 is the end state. This phase does not add a backend. It adds the media toolkit: merge one video stream with one audio stream, and copy or convert audio, on desktop (FFmpeg and ffprobe already on PATH), Android (MediaMuxer / MediaExtractor), and iOS (AVFoundation). Web stays a documented gap: those choices stay disabled. Do not vendor FFmpeg. Do not copy MeTube, NewPipe, or YtDlp-kt. Common code has no ProcessBuilder, no Python, and no Chaquopy imports. A merge copies streams and does not re-encode; an incompatible container fails typed. The typed-options compiler may emit one merge and never a free-form spec string. Direct-file, generic-page, and single-file YouTube downloads stay as they are after D4. Tests use local synthetic fixtures. A live YouTube merge is opt-in on desktop only, against the public URL already allowed in the oracle test. Never write cookies, signed media URLs, raw tool stderr, or private media URLs into fixtures, logs, the vault, or the repo. Do not commit unless a task explicitly tells you to.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D5 (T-075 through T-083).
2. If a D5 card is In progress, resume that task. Otherwise take the Ready D5 card. If none is Ready, take the first Backlog D5 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D5 card is eligible, or T-083 is already Done, stop. Summarize which D5 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D5 task.

Do not mark T-079 done while any of T-075 through T-078 is not Done. Do not start T-080 through T-082 before T-079 is Done. Do not mark T-083 done while any earlier D5 task is not Done. Do not start T-013, T-015, T-016, T-037, or any M2/M3 card as part of this loop. Do not start Phase D6 (T-084 through T-093). Do not port X/Twitter or any other site. Do not vendor FFmpeg. Do not add a web merge or a wasm FFmpeg. Do not add captions, thumbnails, chapters, or SponsorBlock.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
