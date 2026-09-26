---
type: prompt
milestone: D7
tags: [project, engine, handoff]
---

# Phase 7 loop prompt

[Phase 7](Phase-7-X-Twitter.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session after Phase D6 is verified. It implements Phase D7 one task at a time until [T-100](../06-tasks/T-100-Phase-7-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D7, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-7-X-Twitter.md
- vault/03-decisions/ADR-011-X-twitter-phase.md
- vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md
- vault/01-product/Ytdlp-equivalence.md

The phase is a public X/Twitter status video on all four hosts:
- T-094 — TwitterIE for x.com, twitter.com, mobile.x.com, and mobile.twitter.com /user/status/id. Public guest lookup from redacted fixtures. Videos grouped by a stable media id. Photo-only, protected, and deleted fail typed. No download.
- T-095 — Preview lists those videos and the user selects them. One video is preselected. Photos are not rows. The existing Edit panel still chooses quality.
- T-096 — Gate. DownloadRequest carries the selected media ids. Empty selection downloads nothing. Re-extract at download time. One file per selected video inside the download root. The job source URL is the status URL. Desktop fixture download, plus an opt-in live public status when configured. No CLI process for this URL.
- T-097 — Android registry, JVM-equivalent fixture test, assembleDebug if no emulator.
- T-098 — iOS registry, simulator fixture, no live X/Twitter.
- T-099 — Web: the extension carries the guest lookup and the media GET. The page does not fetch X or Twitter.
- T-100 — Four-host table, port manifest, equivalence row, Roadmap, Home, decision log. Accept ADR-011 when that table is recorded.

T-096 depends on T-094 and T-095. T-097, T-098, and T-099 depend on T-096. T-100 depends on all of them.

ADR-004 is the end state. Photos, threads, Spaces, broadcasts, profiles, t.co, and cards are out. A card that is only a YouTube link is not downloaded here. Public guest lookup only. A protected or login-only post fails typed. Do not send cookies. Do not vendor twitter.py. Do not shell out to yt-dlp for this URL. Common code has no ProcessBuilder. Never write guest tokens, cookies, signed media URLs, or private URLs into fixtures, logs, history, the vault, or the repo. Tests use redacted public JSON. A live status is opt-in on desktop only and stays out of default CI. Do not commit a media file. Do not commit unless a task explicitly tells you to. The pin stays 2026.08.19.

If T-093 is not Done, stop. D7 does not start before Phase D6 is verified.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D7 (T-094 through T-100).
2. If a D7 card is In progress, resume that task. Otherwise take the Ready D7 card. If none is Ready, take the first Backlog D7 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D7 card is eligible, or T-100 is already Done, stop. Summarize which D7 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, and move the card to Done.
8. Go back to step 1 for the next eligible D7 task.

Do not mark T-096 done while T-094 or T-095 is not Done. Do not start T-097 through T-099 before T-096 is Done. Do not mark T-100 done while any earlier D7 task is not Done. Do not start T-012, T-013, T-015, T-016, T-018, or any other M2/M3 card as part of this loop. Do not port another site. Do not add cookies, photos, threads, Spaces, or broadcasts.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
