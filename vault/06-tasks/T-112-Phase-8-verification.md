---
id: T-112
type: task
priority: P0
milestone: D8
tags: [task, verification]
---

# T-112 — Phase 8 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

M2's exit gate is written down for all four hosts, the milestone cards that this phase finished are Done, and ADR-012 is accepted.

## Dependencies

- [T-108](T-108-Youtube-playlist-subset.md), [T-109](T-109-Batch-export.md), [T-110](T-110-History-names-folders.md), and [T-111](T-111-Profile-honesty.md).

## Context the next session needs

M2's exit gate, from the roadmap: the queue survives restart, retries do not duplicate finished files, and files are not loaded wholly into memory. This is the usable MVP, not full parity. T-011, T-012, and T-014 are the cards to close. T-015 and the rest of M3 stay open.

## Work

- Run the shared, desktop, Android, iOS, and web tests this phase added, plus `:tools:port-manifest:check`. Record commands and counts in Evidence.
- Fill the four-host table in the phase note: restore after restart, failed URL, playlist fixture expansion, remove versus delete. Use the same honesty as T-100 (JVM-equivalent, simulator, wasm, no browser click-through if that is still true).
- Mark E-14 partial (YouTube playlist subset, cap 50, no channels) and E-15 partial (safe title, collision suffix, no full yt-dlp template language) in the equivalence note, pointing at T-107, T-108, and T-110.
- Check the acceptance boxes on T-011, T-012, and T-014 that this phase met. The countdown bullet on T-011 is the `SCHEDULED` rule, not a timer. Move those three cards to Done.
- Set the phase note to done, the roadmap and Home to D8 verified, and ADR-012 to accepted with today's date. Leave M3 and M4 cards in Backlog.
- Do not commit unless you are asked.

## Acceptance criteria

- [x] The phase note has a four-host table and status done.
- [x] The queue-restart, no-duplicate-retry, and streaming-write claims each cite a test.
- [x] T-011, T-012, and T-014 are Done. T-015 is still not Done.
- [x] The manifest check is green and E-14 and E-15 say partial with the limits above.
- [x] ADR-012 is accepted. Home and the decision log name D8 verified.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Tests run (final):** `:shared:core:jvmTest` 519, `:shared:core:wasmJsBrowserTest` 465, `:shared:core:iosSimulatorArm64Test` 476, `:shared:ui:jvmTest` 99, `:shared:ui:iosSimulatorArm64Test` 8, `:apps:desktop:test` 131, `:apps:android-engine-tests:test` 26, `:apps:web:wasmJsBrowserTest` 6 — all 0 failures; `:apps:android:assembleDebug` BUILD SUCCESSFUL; `:tools:port-manifest:validatePortManifest` green. Command: `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:web:wasmJsBrowserTest :apps:android:assembleDebug :tools:port-manifest:validatePortManifest`, then `:apps:desktop:cleanTest :apps:desktop:test` alone (the FFmpeg tests are load-sensitive when run in parallel).
- **Phase note:** `status: done`; a “Verification (2026-09-29)” section carries the four-host M2 table (restore, failed URL, retry-no-duplicate, streaming writes, playlist expansion, remove/delete/cancel) with the host limits: Android JVM-equivalent (no emulator), iOS simulator-only, web wasm/Brave (no real browser click-through).
- **Equivalence:** E-14 and E-15 are Partial. E-14 points at T-107/T-108 (cap 50, limit 0 means 50, flat YouTube playlist subset, no channels/mixes/continuations/cookies/live); E-15 points at T-110 (safe title naming, ` (2)`/` (3)` collisions, path-escape rejection, no full template language).
- **Milestone cards:** T-011, T-012, and T-014 moved to Done with only the criteria this phase met checked; each note records the M3 gaps (retry-limit policy/partial resume, restart-mid-expansion test, long-filename truncation, symlink/per-platform save-limit tests). T-013 was closed by T-111. T-015 stays in Backlog.
- **Decision:** ADR-012 `status: accepted`, validation section points at the four-host table; the Decision log marks D8 done 2026-09-29 (verified by T-112); Home and Roadmap say D8 verified.
- No commit was made.