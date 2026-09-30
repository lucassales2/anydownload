---
type: prompt
milestone: D17-D22
tags: [project, engine, handoff]
---

# Phase 17–22 loop prompt

[Plan](Phase-17-22-Plan.md) · [File lists](Catalog-D17-D21.md) · [Schedule](Full-engine-schedule.md) · [Kanban](../Kanban.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

Paste the block below as the first message in a new agent session. It implements D17 through D22 and stops when [T-023](../06-tasks/T-023-Release-readiness.md) is Done or a card is blocked. It does not start T-132.

Do not also paste [Phase-11-Loop-prompt.md](Phase-11-Loop-prompt.md) or [Phase-11-16-Loop-prompt.md](Phase-11-16-Loop-prompt.md). Do not arm a short `/loop` against this prompt. A second wake will edit the same files.

## Prompt

```text
Goal: Finish the AnyDownload Kotlin engine at pin 2026.08.19. Implement phases D17 through D22, one Kanban card at a time, until T-023 is Done or a card is blocked. Catalog cards translate the next file from vault/00-project/Catalog-D17-D21.md and leave the card In progress until that section is finished. D22 then removes the desktop yt-dlp CLI and Android Chaquopy fallbacks, audits equivalence, and writes the portfolio builds. Do not start T-132. Do not arm a second short /loop against this prompt. Do not run the D11 or D11–D16 loop prompts. D1–D16 are Done. The Kanban is the source of truth. The task note is the definition of done. The phase note and the catalog are the scope boundary. The plan is vault/00-project/Phase-17-22-Plan.md.

Order, and do not skip ahead:
- D17 T-126. Depends on T-125, which is Done. 23 large files, one file per wake.
- D18 T-127. Depends on T-126. First 110 medium files, one file per wake.
- D19 T-128. Depends on T-127. Remaining 110 medium files, one file per wake.
- D20 T-129. Depends on T-128. First 340 small files, four files per wake.
- D21 T-130. Depends on T-129. Remaining 339 small files, four files per wake.
- D22 T-131, then T-022, then T-023. T-131 depends on T-130. T-022 depends on T-131. T-023 depends on T-022. One card per wake. No new extractor files.

The ordered filenames, sizes, and exclusions are fixed in vault/00-project/Catalog-D17-D21.md. Largest first. Do not reorder a section after its card is In progress. Do not remeasure the upstream tree to invent a different split.

A catalog file is finished when every extractor class it declares has a port/manifest.json row, the translated classes are in productionExtractorRegistry, and the new harness passes on synthetic fixtures. Read the Python from pin 2026.08.19 and do not commit it. A public URL form that can be translated from page or API fixtures becomes partial, with harness cases. A login wall, DRM wall, or account API matches and fails typed, or is planned, and the manifest says that in one sentence. not-started is not a finished row. A matched URL on desktop and Android goes to Kotlin. Unmatched URLs keep the CLI and Chaquopy until T-131. Kotlin sources go under shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/<site>/. Follow the D16 extractor shape.

Fixtures use *.example hosts and fake values such as fake_session / fake_value. No cookie, bearer token, guest token, signed media URL, or private URL in the repo, the logs, or the vault.

If the file needs a helper D10 did not translate, add the smallest helper that file calls in the same wake and name it in Evidence. That spends the 80-day buffer. Do not add a helper that needs a secret, jsinterp, HTTP impersonation, a plugin, or an exec hook. Mark that URL form Partial with the reason.

Verification, every catalog wake: run the new or updated extractor test, then ./gradlew --no-parallel :tools:port-manifest:run and ./gradlew --no-parallel :tools:port-manifest:check. On every 10th file finished in the phase, and when the phase’s last file finishes, also run ./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks and the cross-target compiles (:shared:core wasm and iOS main+test, :shared:ui iOS main, :apps:web wasm, :apps:android:compileDebugKotlin). Fix a failure before leaving the wake. Write the date, the filenames, and the commands under Evidence as "File N of M — name".

On the first wake of a catalog card, write under Evidence the catalog heading, the count, and the first and last filename. That sentence is the file list. Do not paste the whole section into the task note. When the last file passes, check the boxes that are true, set that phase note to done with the commands, the coverage counts, and the limits, and move the card to Done.

D22 slices. Stay on the card until its slices pass.
- T-131: remove DesktopRoute.YTDLP_CLI so an unmatched desktop URL fails typed; remove AndroidRoute.CHAQUOPY so an unmatched Android URL fails typed; keep the opt-in desktop yt-dlp -J oracle off the download path; confirm common code has no ProcessBuilder; run the route tests and :shared:core:jvmTest.
- T-022: set every E-01–E-28 row to Done, Partial with the host note, or Out with the reason; record F-01–F-26 with fixture evidence or an approved difference; F-24 and F-25 stay withdrawn; :tools:port-manifest:check passes and the not-started count is 0 across all 1,751 classes. Do not port another site in this card. Do not move the pin.
- T-023: README build and run steps for Android, iOS, desktop, and Compose/Wasm from a clean checkout; OS and browser coverage, suspension, and browser-save limits; project license and notices for ported and bundled code; user notes for download, save, cookies, and retention. Store submission stays out.
Set Phase 22 to done only when T-131, T-022, and T-023 are Done.

Rules for every card:
- No backend, app login, or MeTube protocol client. No ProcessBuilder or Python in common code.
- Do not vendor yt-dlp. Do not copy MeTube, NewPipe, or YtDlp-kt. A translation carries the Unlicense notice and the pin 2026.08.19. The pin does not move in this loop.
- Cookies, signed media URLs, bearer tokens, and private URLs stay out of logs, history, fixtures, and this vault. Synthetic fixtures only.
- Impersonation, jsinterp, plugins, exec hooks, and free-form options stay out.
- Web merge, embed, and clip fail typed. iOS stays foreground-only. Mobile MP3, WAV, and FLAC stay disabled.
- Do not commit a media file. Do not commit unless the owner asks in this session.
- Check acceptance boxes only where the statement is true. Write the date and the commands under Evidence. Move the card when its checklist passes.

Loop:
1. Read vault/Kanban.md and vault/00-project/Catalog-D17-D21.md. Consider only T-126, T-127, T-128, T-129, T-130, T-131, T-022, and T-023.
2. If any of those cards is In progress, resume it. Otherwise take the first card in the order above whose dependencies are Done.
3. If T-023 is Done, stop. List which of these eight cards are Done, the coverage counts, and which phase notes are marked done.
4. If the card you would take is Blocked, stop. Do not start the next card. Do not skip a file.
5. Move the chosen card to In progress before editing.
6. On a catalog card, implement only the next unfinished wake (one file, or four files on T-129 and T-130). On a D22 card, implement only the next unfinished slice of that card.
7. Run the verification that wake names. On failure, fix it before leaving the wake. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop.
8. When the card’s criteria pass, check the boxes that are true, write Evidence, move the card to Done, and if it is the last card of its phase, set that phase note to done with the commands and the limits.
9. Go back to step 1. Do not start T-132 or any card outside this list.

Work toward the goal. When T-023 is Done, call loop_control with status "done" and explain why. If a card, file, or D22 slice remains, call loop_control with status "next" and name it.
```

## Notes for the next session

- D1–D16 are Done. The first eligible card is T-126, which is still Backlog. Its first file is `adobepass.py`.
- [Phase-11-16-Loop-prompt.md](Phase-11-16-Loop-prompt.md) stops at T-125. This file replaces it for a session that continues through D22. Do not run both.
- The file lists are already measured. The first wake records the catalog heading in T-126 Evidence and translates `adobepass.py` only.
