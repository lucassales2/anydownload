---
type: prompt
milestone: D4
tags: [project, engine, handoff]
---

# Phase 4 loop prompt

[Phase 4](Phase-4-Extractor-Core-and-YouTube.md) · [Kanban](../Kanban.md)

Paste the block below as the first message in a new agent session. It implements Phase D4 one task at a time until [T-074](../06-tasks/T-074-Phase-4-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Implement AnyDownload Phase D4, one Kanban task at a time, until the phase is finished or you are blocked.

Start by reading these notes and follow them for the whole session:
- vault/00-project/Phase-4-Extractor-Core-and-YouTube.md
- vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md
- vault/01-product/Ytdlp-equivalence.md
- vault/03-decisions/ADR-007-Generic-extractor-phase.md
- vault/03-decisions/ADR-004-Local-kotlin-engine.md

ADR-004 is the end state. This phase does not add a backend. It builds the extractor core (InfoExtractor base, InfoDict/MediaFormat, URL registry, helpers, format-spec selector, test harness, port manifest) and translates YouTube for a single video on iOS, Android, web, and desktop: first the JS-less visionos client, then yt-dlp-ejs with the web client on an embedded JavaScript runtime. Translate from yt-dlp tag 2026.08.19 only, file by file, with the Unlicense notice, upstream path, and revision in the header and in port/manifest.json. Do not vendor common.py, utils, YoutubeDL.py, or any extractor file. Do not copy MeTube, NewPipe, or YtDlp-kt. The media toolkit is not built: download single-file formats only, never emit a merge, and fail typed or disable the choice when a selection needs merging or transcoding. Bundle yt-dlp-ejs 0.8.0 with its UNLICENSE and the upstream hashes; no runtime script download. Common code has no ProcessBuilder, no Python, no Chaquopy imports. Desktop keeps the installed yt-dlp CLI for URLs the registry does not match and uses it only as an opt-in oracle otherwise. Android keeps Chaquopy for unmatched URLs, under apps/android only. Web fetches only through the browser extension; the Compose/Wasm page never fetches an origin, and it runs the solver in its own JavaScript. Options stay an allowlist; free-form yt-dlp JSON stays disabled. Never write cookies, PO tokens, visitor data, signed googlevideo URLs, or private media URLs into fixtures, logs, the vault, or the repo; synthesize or redact them. Do not commit unless a task explicitly tells you to.

Loop:

1. Read vault/Kanban.md. Consider only cards tagged #D4 (T-055 through T-074).
2. If a D4 card is In progress, resume that task. Otherwise take the Ready D4 card. If none is Ready, take the first Backlog D4 card whose dependency tasks are all in Done. Dependencies are the links in that task's Dependencies section, not the visual order of the board.
3. If no D4 card is eligible, or T-074 is already Done, stop. Summarize which D4 tasks are Done and what remains.
4. Move that card to In progress before editing code.
5. Read the task note from start to finish. Implement only that task. Use the package names, defaults, and out-of-scope list in the note. The acceptance checklist is the definition of done.
6. Verify the way the task describes, including tests, the opt-in live or oracle run when the task asks for one, and the click-through when it asks for one. If a check fails, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop the loop.
7. When the acceptance criteria pass, check those boxes, write Evidence with the date, commands, and what you ran or clicked, update port/manifest.json if the task ported a module, and move the card to Done.
8. Go back to step 1 for the next eligible D4 task.

Do not mark T-068 done while any of T-055 through T-067 is not Done. Do not start T-069 through T-073 before T-068 is Done. Do not mark T-074 done while any earlier D4 task is not Done. Do not start T-007, T-008, T-009, T-010, T-037, or any M2/M3 card as part of this loop. Do not add FFmpeg, MediaMuxer, or AVFoundation postprocessing. Do not port YouTube playlists, channels, live, comments, subtitles, PO token providers, or cookies. Do not port X/Twitter or any other site. Do not vendor yt-dlp or FFmpeg binaries. Do not fetch YouTube from the web page. Do not bring back the clipboard permission dialog or an automatic clipboard read.

When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the phase is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why.
If more work is needed, call loop_control with status "next" describing what's left.
```
