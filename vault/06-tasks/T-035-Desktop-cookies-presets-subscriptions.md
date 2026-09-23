---
id: T-035
type: task
priority: P1
milestone: D1
tags: [task, desktop, cookies, subscriptions]
---

# T-035 — Cookies, presets, and subscription checks

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [User flows](../01-product/User-flows.md)

## Outcome

The desktop app can import a Netscape cookie file, apply named presets in a fixed order, and check subscriptions with yt-dlp while the window is open. Cookie contents stay out of the UI, the JSON store, and logs.

## Dependencies

- [T-033](T-033-Desktop-ytdlp-single-url.md).
- [T-030](T-030-Subscriptions-screen.md).
- [T-031](T-031-Settings-screen.md).
- [T-032](T-032-Desktop-json-store.md).
- [T-034](T-034-Desktop-ytdlp-formats-and-batches.md), so subscription downloads use the same argument mapper.

## Context the next session needs

### Cookies (F-18)

MeTube accepts a browser-exported Netscape cookie file, replaces it, deletes it, and shows whether one is active. D1 does the same locally.

- Import uses the T-031 `pickCookieFile` callback, implemented on desktop as a file chooser limited to a single file.
- Validate before accepting: file size capped (1 MiB is enough unless you document a different cap), text looks like a Netscape cookie file (header or tab-separated fields). Reject anything else with a short error. Do not partially import.
- Copy the accepted file into the state directory under a fixed name. Jobs store only "use the cookie file" as a boolean or a secret reference id, never the bytes and never the source path from the user's Downloads folder.
- Pass `--cookies` and that file path as two arguments when a download or subscription check is allowed to use cookies. The add form gets a "use cookies" checkbox, default off, shown only when status is Configured. Subscriptions capture that flag at subscribe time like the other options.
- Replace overwrites the stored file. Delete removes the file and sets status to not configured. In-flight processes keep the previous file until they exit; do not delete a file a live process still has open. New jobs see the deletion immediately.
- The screen still shows only Configured or Not configured.
- Do not log the file, the path at info level, or stderr that echoes cookie headers. Tests use a tiny fixture file with a fake cookie name, not a real account.
- Expired or rejected cookies surface as yt-dlp's login-required or unavailable error. The message tells the user they can replace the file. It does not suggest pasting cookies into an issue.

### Presets (F-19)

A preset is a named bundle of allowlisted options the add form can select. Layering, from lowest to highest priority:

1. Built-in defaults from the argument mapper.
2. Selected presets in the order shown in Settings. Later presets override earlier ones on the same key.
3. The explicit add-form fields for that submission. Those win over presets.
4. Nothing above can enable `--exec`, config loading, or a custom output path outside the root. A preset key outside the allowlist is dropped and the Settings row shows that it was ignored.

There is no global "yt-dlp JSON file" watcher in D1. Global behavior is the Settings screen plus presets. Document that difference from MeTube's `YTDL_OPTIONS` file in Evidence. It is an intentional gap, not a forgotten feature.

The custom per-download JSON box stays disabled.

### Subscriptions (F-21)

While the desktop window is open, a scheduler checks subscriptions whose next-check time has passed and that are not paused. Closing the app stops the scheduler. It does not catch up by downloading in the background after exit. On the next launch, overdue subscriptions become due and run once the UI is up, still one at a time or under the same concurrency cap.

A check:

- Runs yt-dlp in a flat listing mode (`--flat-playlist`, playlist end capped by settings, default scan 50) so it does not download every media file during the check.
- Parses ids and titles. Applies the title regex with a timeout or a length cap so a pathological pattern cannot hang the UI. Members-only items are skipped when the flag is set and the flat listing says so; if the flat listing does not say, skip only when a later metadata pass marks them, and document which one you implemented.
- Enqueues unseen ids using the subscription's captured `DownloadRequest`, with the item URL filled in. Seen ids are stored on the subscription in the JSON store, capped (MeTube's reviewed default is 50000). When the cap is exceeded, drop the oldest.
- First check policy: mark current items seen and download nothing, unless the row editor or subscribe dialog has an explicit "include existing items" choice. Default is future items only, so a subscribe does not pull an entire channel. Say which policy is active on the subscribe button or the new row.
- Check now, check all, and check selected call the same function as the timer.
- Errors on a check set the row error and do not wipe seen ids.
- Delete still does not delete downloaded files.

Do not add a second subscription implementation beside `SubscriptionRepository`. The desktop graph's repository performs real checks; the in-memory fake used by other hosts stays a fake.

## Verification

Tests: cookie file rejected when oversized or not Netscape-shaped; stored job JSON contains no cookie line; preset layering order; disallowed preset keys dropped; subscription check fixture (a saved flat-playlist JSON or text) enqueues only unseen, unfiltered titles; paused subscription does not run; first-check default enqueues nothing.

Desktop, using a fixture cookie file you create locally and do not commit: import, see Configured, delete, see Not configured. If you have a site you are allowed to access with cookies, a single authorized download is optional and must not be described with the cookie or the URL in Evidence. A subscription check against a public channel limited to a small scan is enough to show one new item queued or "none new" after the initial seen-mark.

## Out of scope

Reading Safari or Chrome cookie stores directly, syncing cookies between devices, the Q-09 free-form box, background checks after the window closes, Kotlin extractors.

## Acceptance criteria

- [x] Import, replace, and delete work for a Netscape cookie file. The UI never shows contents. State JSON does not contain them.
- [x] Downloads pass `--cookies` only when the job opted in and a file is configured.
- [x] Presets layer in the documented order under the add-form fields. Disallowed keys never become arguments.
- [x] The custom JSON field is still disabled.
- [x] An open desktop app checks due subscriptions, honors pause, filter, members-only, and the scan cap, and records seen ids.
- [x] The first check does not download the back catalog unless an explicit control says otherwise.
- [x] Evidence records the default first-check policy and the MeTube `YTDL_OPTIONS` file gap.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :apps:desktop:test` — BUILD SUCCESSFUL. New suites: `DesktopCookieStoreTest` 4, `PresetLayeringTest` 4, `DesktopSubscriptionRepositoryTest` 4, plus cookie/preset cases in the existing engine/args tests; `LiveYtDlpCheckTest` is now 8 (all opt-in).
- Full matrix `:shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :apps:desktop:test :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, **174 tests, 8 skipped (live-only), 0 failures**.
- Live subscription check with `-Danydownlod.live.subscriptionUrl=...` (public channel, scan cap 2): `seen=2 jobs=0 error=null` on the first check, and still 0 jobs on the second check (nothing new).
- `./gradlew :apps:desktop:run` — the window opened with the cookie store, desktop subscription repository, and scheduler; no exception/error lines; closed cleanly.

Cookies: `DesktopCookieStore` validates an existing regular file, empty/oversized files, and Netscape shape (header or a 7-field tab row), copies to `<state>/cookies.txt`, and persists only the frozen path; delete removes the file and clears the path. The Settings screen drives Import → Configured → Delete → Not configured through `CookieStore` and never shows contents; `DesktopStoreTest` already proves the settings JSON has no `value`/`contents` field. The engine adds `--cookies <path>` only when `useCookies` is true and a path exists, and the add form shows the checkbox only when status is Configured. Tests use a fake `fake_session`/`fake_value` cookie row, never a real account.

Presets: `PresetLayering` resolves the selected preset ids in Settings order, lets a later preset override an earlier one on the same key, drops any key outside the five-key allowlist, and keeps an explicit form switch on. The merged options feed the same argument mapper; `PresetLayeringTest` proves a preset can add `--sponsorblock-remove`. D1 presets can enable the five boolean switches only; they cannot lower an explicit form value. The disabled custom-JSON field is unchanged and never persisted.

Subscriptions: `DesktopSubscriptionRepository` keeps CRUD on the shared fake and performs real flat scans for `checkNow`/the scheduler; `--flat-playlist --skip-download --playlist-end 50 --print 'ENTRY|%(id)s|%(url)s|%(title)s|%(availability)s'` by default, with the same `--cookies` gate. Later checks filter by the title regex (pattern length cap 200, title cap 500), skip members-only when the flag is set and `%(availability)s` says so, enqueue only unseen ids through the captured request, then mark every scanned id seen (newest-first cap 50,000). Errors set `lastError` without wiping seen ids. `SubscriptionScheduler` runs while the window is open and stops when the scope is cancelled on close; overdue rows are due on the next launch. `checkAll` skips paused rows.

Default first-check policy: **future items only**. On the first check the current listing is marked seen and nothing is enqueued; there is no include-existing control in D1, so the back catalog never downloads on subscribe. `DesktopSubscriptionRepositoryTest.firstCheckMarksSeenAndEnqueuesNothing` and the live check cover this.

MeTube `YTDL_OPTIONS` gap: D1 has no global yt-dlp options file and no watcher. Global behavior is the Settings document plus ordered named presets; the per-download custom-JSON box stays disabled (Q-09). This is an intentional D1 difference and is recorded here.
