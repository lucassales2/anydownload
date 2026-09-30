---
type: plan
status: ready
milestone: D17-D22
tags: [project, engine, handoff]
---

# Plan — Phases D17 through D22

[Schedule](Full-engine-schedule.md) · [Loop prompt](Phase-17-22-Loop-prompt.md) · [File lists](Catalog-D17-D21.md) · [Kanban](../Kanban.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Ready 2026-09-30.** D1–D16 are Done. This plan is the session scope for the rest of the Kotlin engine at pin `2026.08.19`. The pasteable instructions are [Phase 17–22 loop](Phase-17-22-Loop-prompt.md). Paste that prompt in a new session. Do not also paste the D11 or D11–D16 prompts, and do not arm a short `/loop` against it.

## Goal

Account for the rest of the yt-dlp extractor catalog at the pin, then delete the desktop CLI and Android Chaquopy fallbacks, audit the equivalence rows, and write the four-target portfolio builds. When [T-023](../06-tasks/T-023-Release-readiness.md) is Done, D22 is done and the 614-day schedule is closed. Staying current after the pin is a separate 2–5 days a month and is not part of this loop.

## What is already done

D16 closed Twitter’s remainder and 13 high-traffic files. The coverage count after that phase is 0 ported, 35 partial, 80 planned, 1,636 not started, of 1,751 classes. Matched URLs already go to Kotlin. Unmatched URLs still use `DesktopRoute.YTDLP_CLI` or `AndroidRoute.CHAQUOPY` until T-131.

## File lists

The ordered lists are [Catalog D17–D21](Catalog-D17-D21.md). They were measured on 2026-09-30 from commit `3a08beaf031ab68f966401ead017ac81fe8486cf`. Bands use decimal kilobytes (8,000 / 25,000 / 80,000), which is the cut that reproduces the schedule’s 679 / 220 / 32 / 4 counts. Excluded because earlier phases already account for them: the YouTube package, `twitter.py`, `generic.py`, D16’s nine large files, and the four huge files (Bilibili, Vimeo, PeerTube, BBC). Largest file first inside each band. Do not reorder a list after its card is In progress.

| Phase | Card | Files this loop translates | Wake size |
| --- | --- | --- | --- |
| D17 | [T-126](../06-tasks/T-126-Remaining-large-sites.md) | 23 large | 1 file |
| D18 | [T-127](../06-tasks/T-127-Medium-extractors-a.md) | 110 medium | 1 file |
| D19 | [T-128](../06-tasks/T-128-Medium-extractors-b.md) | 110 medium | 1 file |
| D20 | [T-129](../06-tasks/T-129-Small-extractors-a.md) | 340 small | 4 files |
| D21 | [T-130](../06-tasks/T-130-Small-extractors-b.md) | 339 small | 4 files |
| D22 | [T-131](../06-tasks/T-131-Remove-ytdlp-fallback.md), then [T-022](../06-tasks/T-022-Parity-audit.md), then [T-023](../06-tasks/T-023-Release-readiness.md) | no new extractors | 1 card |

D17, largest first: `adobepass.py`, `dplay.py`, `nbc.py`, `cbc.py`, `niconico.py`, `brightcove.py`, `pbs.py`, `nhk.py`, `zdf.py`, `rai.py`, `nrk.py`, `weverse.py`, `pornhub.py`, `ard.py`, `zattoo.py`, `openrec.py`, `xhamster.py`, `neteasemusic.py`, `panopto.py`, `tvp.py`, `vrt.py`, `kaltura.py`, `gamejolt.py`.

## One catalog wake

1. Read the Kanban. Resume the In progress card. If none of T-126–T-130 is In progress, take the first of those whose dependency is Done.
2. On the first wake of a card, move it to In progress and write under Evidence: the catalog heading, the file count, and the first and last filename. That sentence is the file list. Do not paste the whole list into the task note.
3. Translate only the next unfinished file, or the next four when the card is T-129 or T-130. A file is unfinished when Evidence has no line that names it as finished.
4. Leave the card In progress until every file in its catalog section has that line.

A file is finished when all of the following are true:

- The Python was read from the pin and was not committed. The translation carries the Unlicense notice and the pin `2026.08.19`.
- Every extractor class declared in that file has a `port/manifest.json` row. A class whose public URL form is translated is `partial` and has harness cases on synthetic fixtures. A class that is only a login wall, a DRM wall, or an account API is `partial` when the URL matches and fails typed with that reason, or `planned` with that one-sentence reason. `not started` is not a finished row.
- The translated classes are in `productionExtractorRegistry`, so a matched URL on desktop and Android goes to Kotlin. Unmatched URLs still fall through until T-131.
- Fixtures use `*.example` hosts and fake values. No cookie, bearer token, guest token, signed media URL, or private URL is stored in the repo, the logs, or the vault.
- The new test class passes, and `:tools:port-manifest:check` passes.

Kotlin files live under `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/<site>/`, with the test beside the other extractor tests. Follow the D16 shape: one site package, registry entry, manifest rows, synthetic harness. Shared bases that later files in the same band will subclass land with the earliest file that needs them.

If the file needs a helper that D10 did not translate, add the smallest helper that file calls, in the same wake, and name it in Evidence. That is the 80-day rework buffer. Do not add a helper that requires a secret, `jsinterp`, HTTP impersonation, a plugin, or an exec hook. Mark that URL form Partial with the reason instead.

If a file cannot be finished, move the card to Blocked, write the blocker and the next action under Evidence, and stop. Do not skip the file and do not start the next card.

## Verification cadence

Every catalog wake:

- The new or updated extractor test.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check`.

On every 10th file finished in that phase, and again when the phase’s last file finishes, also run:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks`
- Cross-target compiles: `:shared:core` wasm and iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`.

Record the date, the file names, and the commands under Evidence. A failing check is fixed before the wake ends.

When the last file of a phase passes, check that card’s boxes only where the statement is true, set the phase note `status` to `done` with the commands, the coverage counts, and the limits, and move the card to Done.

## D22, after T-130

No new extractor files. Three cards, in order, one card per wake. Slices inside a card stay on that card until the card is Done.

**T-131 — remove the fallbacks.**

1. Delete the `DesktopRoute.YTDLP_CLI` download path. An unmatched desktop URL fails typed.
2. Delete the `AndroidRoute.CHAQUOPY` download path. An unmatched Android URL fails typed.
3. Keep the opt-in desktop `yt-dlp -J` oracle behind its test flag. It is not on the download path.
4. Confirm common code still has no `ProcessBuilder`.
5. Run the desktop and Android route tests plus `:shared:core:jvmTest`.

**T-022 — parity audit.** Depends on T-131. This card records; it does not port another site.

1. Every E-01–E-28 row is Done, Partial with the host note, or Out with the reason.
2. Every F-01–F-26 row has fixture evidence or an approved difference. F-24 and F-25 stay withdrawn with T-021.
3. `:tools:port-manifest:check` passes and the not-started count is 0. All 1,751 classes are ported, partial, or planned.
4. The pin stays `2026.08.19`. Drift past the pin is recorded, not absorbed.

**T-023 — portfolio builds.** Depends on T-022.

1. README says how to build and run Android, iOS, desktop, and Compose/Wasm from a clean checkout.
2. Tested OS and browser coverage, suspension, and browser-save limits are written down.
3. The project license and the notices for ported and bundled code are in the repo.
4. User notes cover download, save, cookies, and retention. Store submission stays out.

Set Phase 22 to done only when T-131, T-022, and T-023 are Done.

## Stays out of this loop

Legacy `jsinterp`, HTTP impersonation, plugins, exec hooks, and free-form options. Web merge, embed, and clip stay a typed failure. iOS stays foreground-only. Mobile MP3, WAV, and FLAC stay disabled. No backend, app login, or MeTube protocol client. No `ProcessBuilder` or Python in common code. Do not vendor yt-dlp. Do not copy MeTube, NewPipe, or YtDlp-kt. Do not commit a media file. Do not commit unless the owner asks. Do not start [T-132](../06-tasks/T-132-Localization.md).

## Stop

Stop when T-023 is Done, or when the card you would resume is Blocked. Report which of T-126, T-127, T-128, T-129, T-130, T-131, T-022, and T-023 are Done, the coverage counts, and the phase notes marked done.
