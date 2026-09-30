---
type: prompt
milestone: D10
tags: [project, engine, shared-downloader, handoff]
---

# Phase 10 loop prompt

[Phase 10](Phase-10-Shared-downloader.md) · [Kanban](../Kanban.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

Paste the block below as the first message in a new agent session after the Metro gate is done. It implements Phase D10 one task at a time until [T-139](../06-tasks/T-139-Phase-10-verification.md) is Done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```text
Goal: Wait until the Metro loop is finished, then implement AnyDownload Phase D10 (shared downloader), one #D10 Kanban task at a time, until T-139 is Done or a task is blocked. Do not run this in the Metro session. Do not arm a second short /loop against this prompt. Gate, every wake: 1. Read vault/Kanban.md. 2. Metro is finished only when every card tagged #metro (T-113 through T-122) is in Done, including T-120, and none is In progress, Ready, Backlog, Blocked, or Review. 3. If Metro is not finished, do not edit any file and do not move any card. Do not implement Metro tasks and do not start D10. If a 5-minute sleeper for this gate is already running, do not start another. Otherwise arm one one-shot wake, then stop this turn: sleep 300 echo 'AGENT_LOOP_WAKE_d10 {"prompt":"Metro gate: re-read vault/Kanban.md. If every #metro card is Done, stop this sleeper and start the D10 loop. If not, wait another 5 minutes."}' Notify on output matching ^AGENT_LOOP_WAKE_d10. Do not call loop_control with status "next" for this wait. The next check is in 5 minutes, not immediately. 4. When the gate passes, do not arm another 5-minute sleeper. If one is still running, kill it and do not schedule a replacement. Then continue below. D10 is the shared downloader. No new site. Pin stays 2026.08.19. T-123 stays the phase card. T-139 checks its boxes and moves it to Done. T-132 is not part of this loop. First D10 wake only, if vault/06-tasks/T-133-Format-lists.md does not exist: write the task notes T-133 through T-139 from the list below, write vault/00-project/Phase-10-Loop-prompt.md containing this prompt, point vault/00-project/Phase-10-Shared-downloader.md and the D10 row of vault/00-project/Full-engine-schedule.md at T-133–T-139, and add the seven #D10 cards to the Kanban Backlog after the Metro cards and before T-123. Do not implement T-133 in that same turn. Move no Metro card.

- T-133 — Format lists and multi-stream (E-05). Extend FormatSelector.kt and FormatSpec.kt for comma lists, all, and mergeall, and for more than one simultaneous stream. Tests only. Update the YoutubeDL.py manifest scope. Depends on T-120.
- T-134 — Resume inside one attempt (E-07). A ranged download that dies mid-body continues from the bytes already in the temp file when the server accepts ranges. A relaunch still marks the job FAILED / ENGINE_UNAVAILABLE and does not resume. Depends on T-133.
- T-135 — Fragment concurrency (E-10). FragmentDownloader.kt fetches a bounded number of fragments at once and can skip an unavailable fragment. Cancellation still discards the file. Existing AES-128 tests stay green. Depends on T-134.
- T-136 — Helpers the generic slices call (E-03). Translate only the common.py / _utils.py functions T-137 and T-138 call. Name each one in port/manifest.json. Do not translate the rest of common.py. Depends on T-135.
- T-137 — Generic HTML discovery. GenericExtractor.kt also finds embeds, iframes, JSON-LD, and meta refresh. A page that resolves to exactly one policy-safe media URL stays on the Kotlin path. Zero or several still fall through to the desktop CLI or Android Chaquopy. Depends on T-136.
- T-138 — Generic HLS and DASH discovery. Find one m3u8 or mpd URL in the page and hand it to the existing parsers. Live and DRM still fail typed. Depends on T-137.
- T-139 — Phase 10 verification. Run :shared:core:jvmTest, the wasm browser tests, the iOS simulator tests, and :tools:port-manifest:check. Update E-03, E-05, E-07, and E-10 in vault/01-product/Ytdlp-equivalence.md to the scope that actually landed. Check T-123's boxes and move T-123 to Done. Depends on T-138.

T-133 depends on T-120. T-134 depends on T-133. T-135 depends on T-134. T-136 depends on T-135. T-137 depends on T-136. T-138 depends on T-137. T-139 depends on T-138.

Rules:
- No new site, no cookies, no captions, no clips, and no removal of the desktop CLI or Android Chaquopy.
- Common code has no ProcessBuilder. Do not vendor yt-dlp. Do not commit a media file. Do not commit unless a task explicitly tells you to.
- A page with zero or several media URLs still falls through to the CLI or Chaquopy.
- The pin stays 2026.08.19.

Loop, only after the gate has passed and the T-133 note exists:
1. Read vault/Kanban.md. Consider only cards tagged #D10 (T-133 through T-139).
2. If a #D10 card is In progress, resume it. Otherwise take the Ready #D10 card. If none is Ready, take the first Backlog #D10 card whose dependencies are Done.
3. If none is eligible, or T-139 is Done, stop. Summarize which #D10 tasks are Done.
4. Move that card to In progress before editing.
5. Read the task note. Implement only that task. The acceptance checklist is the definition of done.
6. Run the verification that task names. On failure, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop.
7. When the criteria pass, check the boxes, write Evidence with the date and the commands you ran, and move the card to Done. T-139 also moves T-123 to Done.
8. Go back to step 1. Do not start T-018, T-124, or any later engine card. Do not start T-133 while T-120 is not Done. Do not insert a 5-minute wait between D10 tasks.

Work toward the goal. When T-139 is Done, call loop_control with status "done" and explain why. If Metro is still unfinished, arm the 5-minute gate wake and stop. If a D10 task remains after the gate has passed, call loop_control with status "next" and name what is left.
```

## Notes for the next session

- The first D10 wake wrote this file plus the task notes [T-133](../06-tasks/T-133-Format-lists.md) through [T-139](../06-tasks/T-139-Phase-10-verification.md), pointed [Phase 10](Phase-10-Shared-downloader.md) and the D10 row of the [schedule](Full-engine-schedule.md) at them, and added the seven `#D10` cards to the Backlog before [T-123](../06-tasks/T-123-Shared-downloader.md). No code changed.
- The board cards are tagged `#D10`. T-133 is first: [T-120](../06-tasks/T-120-Metro-verification.md) is Done, so T-133 is eligible.
- [T-123](../06-tasks/T-123-Shared-downloader.md) is the phase card; [T-139](../06-tasks/T-139-Phase-10-verification.md) checks its boxes and moves it to Done.
