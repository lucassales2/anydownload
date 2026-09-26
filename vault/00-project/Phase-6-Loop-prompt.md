---
type: prompt
milestone: D6
tags: [project, engine, handoff]
---

# Phase 6 loop prompt

[Phase 6](Phase-6-SpotDL-Parity.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session after Phase D5 is verified. It implements Phase D6 one task at a time until [T-093](../06-tasks/T-093-Phase-6-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D6, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-6-SpotDL-Parity.md
- vault/03-decisions/ADR-010-Spotdl-parity-phase.md
- vault/03-decisions/ADR-009-Media-toolkit-phase.md
- vault/01-product/Feature-parity.md
- vault/06-tasks/T-037-Spotify-youtube-match.md

ADR-004 is the end state. This phase does not add a backend or an AnyDownload account. It reimplements spotDL v4.5.2 (commit cd4a4203f5b12bd6dbbdf22d7674807858d35e05): Spotify metadata, a match on YouTube Music then YouTube then the fallback providers, a download through the existing engine, and embedded tags through the D5 MediaToolkit. Do not vendor spotDL, do not copy its Python, and do not spawn spotdl. Do not download Spotify audio. Do not add spotDL's web server. Common code has no ProcessBuilder. Tests use public fixture URLs only. Never write client secrets, OAuth tokens, cookie files, signed media URLs, or private media URLs into fixtures, logs, the vault, or the repo. Do not commit unless a task explicitly tells you to.

If T-083 is not Done, stop. D6 does not start before Phase D5 is verified.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D6 (T-084 through T-093).
2. If a D6 card is In progress, resume that task. Otherwise take the Ready D6 card. If none is Ready, take the first Backlog D6 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D6 card is eligible, or T-093 is already Done, stop. Summarize which D6 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D6 task.

Do not mark T-086 done while T-084 or T-085 is not Done. Do not start T-087 through T-092 before T-086 is Done. Do not mark T-093 done while any earlier D6 task is not Done. When T-086 is Done, also check T-037 and move that card to Done. Do not start T-013, T-015, T-016, T-018, or any other M2/M3 card as part of this loop. Do not port X/Twitter. Do not add a spotDL web server, Docker, or a vendored FFmpeg.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
