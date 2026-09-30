---
id: T-019
type: task
priority: P1
milestone: D15
tags: [task, subscriptions, scheduling]
---

# T-019 — Durable channel and playlist subscriptions

[Home](../Home.md) · [Kanban](../Kanban.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

F-21 on-device recurring scans and controls. Scans run while the app is open.

## Dependencies

- [T-011](T-011-Durable-queue.md).
- [T-012](T-012-Playlists-and-batches.md).
- [T-017](T-017-Options-and-presets.md).
- [T-018](T-018-Cookie-lifecycle.md).
- [T-124](T-124-Youtube-done.md). Channel scans need the tab extractor from D12. Phase: [Phase 15](../00-project/Phase-15-Subscriptions-sharing.md).

## Acceptance criteria

- [x] Persist source/name/options, interval, scan cap, seen-ID cap, initial backlog policy and failure/dedupe semantics.
- [x] Support rename, title filter, skip-members-only, pause/resume, check now/all/selected, bulk delete and last/next-check/errors.
- [x] Bound/time-limit filter evaluation and scan work; coalesce overlapping checks with backoff/jitter and downtime policy.
- [x] Test restart, unavailable items, changed cookies/options, overlapping subscriptions and failed-item retry without surprise redownloads.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D15 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). iOS and the browser will not keep scans running after the app is suspended. Say that in the UI. Done 2026-09-30.

**Iteration 1 — scanner core (2026-09-30).** `SubscriptionScanner` + `SubscriptionEntrySource` in common code implement the scan policy: the default first check marks current items seen and downloads nothing; `Subscription.downloadExisting = true` enqueues the current backlog once; later checks enqueue only unseen items; the title filter is bounded (≤200 chars, an invalid regex is ignored) and titles truncated (≤500); members-only items are skipped when configured; seen ids are capped oldest-first at 50,000; a failed scan keeps the previous seen ids and records `EXTRACTION_FAILURE` with a next-check time; paused rows are skipped; overlapping checks for one subscription coalesce. `SubscriptionRepository.recordCheck` persists one scan outcome (seen ids, error, next check) and the in-memory and desktop repositories delegate it.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest --rerun-tasks` — 723 tests, 0 failures.
- `:shared:core` wasm/iOS main+test compiles — clean.

Next: host entry sources and scanner wiring (extractor registry for Kotlin hosts, CLI scan for desktop), UI controls (bulk delete, check selected, last/next check and errors, suspension wording on iOS/web), scheduler backoff/jitter, restart tests, then final T-019 verification.

**Iteration 2 — registry source, scanning decorator, selected checks (2026-09-30).** `RegistrySubscriptionEntrySource` lists a tab source through the shared extractor registry; `enqueueSubscriptionEntry` submits one child job per unseen entry with the subscription's captured options and a stable `subscription:<id>:<entry>` idempotency key; `ScanningSubscriptionRepository` decorates any repository so `checkNow`/`checkAll`/`checkSelected` run the scanner in the host scope (nothing runs after that scope is cancelled). `SubscriptionRepository.checkSelected(ids)` was added and implemented by the in-memory, desktop, and scanning repositories.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :apps:desktop:test --rerun-tasks` — core 725, desktop 146 (15 live skipped), 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs` — clean.

Next: wire the scanning repository into the Android, iOS, and web graphs; UI controls (bulk delete, check selected, last/next check and errors, iOS/web suspension wording); scheduler backoff/jitter; restart tests; final verification.

**Iteration 3 — host wiring, suspension wording, scan cap (2026-09-30).** Android, iOS, and web graphs now provide `ScanningSubscriptionRepository` over the in-memory delegate, the extractor registry, and the graph's engine (lazy `Provider<DownloadEngine>`), so Check now/all/selected run the shared scanner while the app is open; desktop keeps its yt-dlp flat scan. `AppGraph.subscriptionsPauseOnSuspend` is true on iOS and web and drives a Subscriptions note (“This host stops scans when the app is suspended or closed. Open the app to scan again.”). The scanner gained the reviewed per-scan entry cap (50) before seen-id and dedupe handling.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 726, ui 106, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Still open on this card: scheduler backoff/jitter and downtime policy, restart/unavailable/changed-options/overlapping-subscription tests, and the final T-019 verification.

**Iteration 4 — backoff/jitter, downtime, bulk delete (2026-09-30).** Failed scans now double the wait (up to four intervals) plus a stable per-subscription jitter of up to a quarter interval; `dueIds`/`checkDue` give hosts a downtime policy where an app closed past due runs each overdue subscription once without a catch-up storm. The Subscriptions UI has bulk delete of the selected rows (UI test added). Scanner tests now cover backoff, due/pause, overlapping subscriptions, and restart with persisted seen ids.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 730, ui 107, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Limits recorded: scans run only while the app is open (iOS and web say so); a scan marks items seen at scan time, so a failed download is retried from the queue rather than re-enqueued by the scanner, which prevents surprise redownloads; desktop persists subscriptions in `subscriptions.json`, while the Android/iOS/web in-memory repositories still lose subscriptions on restart (the remaining persistence gap).

**Iteration 5 — subscription persistence and verification (2026-09-30).** `SubscriptionDocumentCodec` stores source, name, captured options, interval, seen ids, backlog policy, last/next check, and the last error; `PersistedSubscriptionRepository` writes after every mutation and restores from host storage (a corrupt document restores empty). Android writes `subscriptions.json` beside `jobs.json`, iOS writes it under Application Support, and web uses the `anydownload.subscriptions.document` localStorage key; the desktop already wrote `subscriptions.json`. Each graph now composes persisted + scanning repositories, so mobile and web keep subscriptions and scan results across restarts.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 733, ui 107, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.
