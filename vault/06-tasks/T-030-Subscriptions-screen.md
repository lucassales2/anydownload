---
id: T-030
type: task
priority: P1
milestone: D1
tags: [task, ui, subscriptions]
---

# T-030 — Subscriptions screen

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [User flows](../01-product/User-flows.md#5-subscribe-to-new-uploads)

## Outcome

The Subscriptions tab lists channel and playlist subscriptions and can pause, edit, delete, and request a check. Rows come from `SubscriptionRepository`. Checks do not call yt-dlp yet.

## Dependencies

- [T-027](T-027-Desktop-app-shell.md).
- [T-028](T-028-Add-form.md), because Subscribe on the add form is how a row is created. If the form's Subscribe button already calls `add`, this task only has to render the result.

## Context the next session needs

MeTube's reviewed subscription behavior (wiki and F-21):

- The person sets download options on the add form, then clicks Subscribe. Those options are copied onto the subscription and reused for every later video. They are not editable on the row. To change quality or format, delete the subscription and subscribe again. The UI should say that on the edit dialog so it is not a surprise.
- Editable after creation: display name, check interval in minutes, title-filter regex, skip-members-only.
- A paused subscription keeps its seen ids and stops checking.
- Delete stops future checks. It does not remove downloaded files or history. The confirm says that.
- Check now, Check all, and Check selected are explicit. The fake records `lastCheck` and leaves the queue unchanged.
- The default interval is the settings value (60 minutes if untouched). The add form does not need its own interval field if Subscribe uses the settings default and the row editor can change it immediately after.
- Title filter is a regex. Empty means all titles. Show the pattern as plain text. Do not evaluate a huge pattern in the UI. Validation is "compiles as a regex" only.
- Members-only: a checkbox, off by default, described as skipping items yt-dlp marks as members-only.

Also from the user flows: say on the empty state or the tab intro that checks run while this app is open. D1 is desktop, so do not write iOS/browser suspension copy into this screen. One sentence is enough.

Seen ids are not listed in the table. They are internal.

## Work

Build `com.anydownlod.ui.subscriptions`.

Table columns: name, source host plus full URL as secondary text, interval, title filter, members-only (yes/no), last check, next check, last error, paused.

Row actions: Check now, Pause or Resume, Edit, Delete.

Toolbar: Check all, Check selected (enabled when the selection is non-empty). Reuse the selection checkbox behavior from T-029 if it already exists; otherwise a simple per-row checkbox plus select-all is enough. Do not build a second visual language.

Edit dialog fields: name, interval (positive integer minutes), title filter, skip members-only. Save calls `update`. Invalid regex or interval blocks save.

Delete confirm: "Delete this subscription? Downloads already in the queue or history stay. Future checks stop."

Empty state tells the user to paste a channel or playlist URL in the add form and choose Subscribe.

If the repository add from T-028 did not set the name, default the name to the URL's host until a later check learns a channel title. Do not block the screen on metadata.

Presenter tests: pause hides further automatic checks in the fake (next check cleared or ignored), edit does not replace the stored `DownloadRequest` options, delete removes only the subscription, a batch URL never created a row (covered in T-028, do not regress it), invalid regex rejected.

## Verification

Desktop: subscribe to `https://example.com/channel/fixture`, see the row, edit the filter, pause, check now, delete. Confirm the Downloading list did not gain a fake video. Run presenter tests.

## Out of scope

yt-dlp flat-playlist scans, seen-id persistence, real timers. T-035 adds those behind the same repository methods.

## Acceptance criteria

- [x] The tab lists subscriptions with the columns and row actions above.
- [x] Edit changes name, interval, filter, and members-only, and leaves captured download options untouched. The dialog says quality and format stay as they were at subscribe time.
- [x] Pause, resume, check now, check all, and check selected call the repository. The fake check does not enqueue jobs.
- [x] Delete confirms and does not remove history or files.
- [x] Empty state and the "checks run while the app is open" sentence are present.
- [x] Presenter tests and the desktop click-through are in Evidence.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, 96 tests total (core 47, shared/ui 49), 0 failures. New suites: `SubscriptionsPresenterTest` 7 and `SubscriptionsUiTest` 3.
- `./gradlew :apps:desktop:run` — the seeded window opened, no exception/error lines in the log, and it closed cleanly. (`InMemoryAppGraph.seeded()` seeds jobs only; the subscription table itself was exercised headlessly.)

Click-through: macOS Accessibility/Screen Recording permission is still not granted, so the clicks ran through the Compose test runtime in `SubscriptionsUiTest`. Performed: opened the Subscriptions tab and saw the subscription row; Check now (recorded a last-check timestamp and left `engine.jobs` empty); Pause then Resume from the same row button (next check cleared and restored); Edit with an invalid `[` filter (dialog stayed open with the regex error and the stored filter was unchanged); Edit with `fixture` (saved); Delete with the confirm copy "Downloads already in the queue or history stay. Future checks stop." (the only subscription was removed and no jobs were touched); Check selected with one of two rows selected (only that row's timestamp moved); empty state shows the sentence that checks run while the app is open. Presenter tests add the column mapping, blank-name/zero-interval/invalid-regex rejection, that `update` cannot replace captured `DownloadOptions`, and delete-only-this-subscription.

What landed:

- `com.anydownlod.ui.subscriptions`: `SubscriptionRow` + mapper (name, host, full URL, interval, filter, members-only, last/next check, error, paused), `SubscriptionsPresenter` (check now/all/selected, pause/resume, delete, validated `update`, regex compile check, row mapper), and `SubscriptionsScreen` (header + table rows, tri-state select-all, toolbar check actions, edit dialog with the quality/format note, delete confirm, and the empty state).
- `SubscriptionsEmptyState.kt` is gone; `AppShell` renders the real screen. `AppShellTest`'s empty-state assertion now matches the new copy.
- `formatUtcMinute` was added next to `formatDueTime` for dense table cells.
