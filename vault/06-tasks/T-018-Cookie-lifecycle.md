---
id: T-018
type: task
priority: P0
milestone: D11
tags: [task, credentials, security]
---

# T-018 — Local cookie lifecycle

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-18 local cookie import, replace, status, and delete. Cookies stay on this device.

## Dependencies

- [T-123](T-123-Shared-downloader.md). Phase: [Phase 11](../00-project/Phase-11-Cookies.md). Estimate 6 engineer-days. Pulled ahead of the other M3 cards because later extractors attach the jar.

## Acceptance criteria

- [x] Ask for consent, validate cookie format/size, and store the file only on this device.
- [x] Support replacement, deletion, and expiry. Jobs hold a reference, not the cookie text.
- [x] Show configured/error state without secret contents. Redact logs.
- [x] Test expired cookies, deletion during active work, and restart using synthetic fixtures.
- [x] Document platform import limits and that cookies do not guarantee access or authorize unpermitted downloads.

## Evidence / notes

Done 2026-09-30. Scheduled 2026-09-29 as D11 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). The jar attaches in the shared HTTP helper. Never use personal cookie files as committed fixtures or public issue evidence. Browser-store reading stays out on mobile and web.

Owner, 2026-09-30: `cookies.txt` stays app-private with no Keystore, Keychain, or desktop cipher. Exclude it from Android Auto Backup and iOS backup where the platform allows, and say that a manual copy of app data includes it. Desktop may copy Chrome, Firefox, or Safari into that file after a separate consent screen. The copy is a snapshot, not a live re-read. Prompt: [Phase 11 loop](../00-project/Phase-11-Loop-prompt.md).

**Slice 1 — parser and jar (2026-09-30).** Files: `anydownload` and `CookieJar.kt`. The parser enforces the owner rules (1 MiB cap, leading `# Netscape HTTP Cookie File` / `# HTTP Cookie File` or a non-comment tab row with 7+ fields, `#HttpOnly_` rows, RFC 6265 name/value legality, no partial import). The jar matches host (dot domain or includeSubdomains), path, secure, and expiry, skips expired rows, builds a deterministic header, and reports `READY` / `ALL_EXPIRED` / `EMPTY`. No file I/O in commonMain; `toString` on a cookie omits name and value.

Commands: `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.cookies.*"` — 31 tests, 0 failures (17 `CookieJarTest`, 14 `NetscapeCookieFileTest`).

**Slice 2 — attach (2026-09-30).** `HttpRequest` gained the trusted `cookie` field, applied after `HttpHeaders.sanitize`; a declared `Cookie` header is still refused by name only, and the trusted value never enters `droppedHeaders` or any diagnostic. `ExtractorHttp`, `downloadDirectFile`, `fetchManifestText`, `resumeFromOffset`, and `FragmentDownloader` read the job's `ActiveCookieJar` snapshot (coroutine context) and rebuild the header per request URL and per redirect hop. The engine resolves one `CookieJarSource` snapshot per `useCookies` job; a missing, empty, or fully expired jar fails typed (`INVALID_URL_OPTIONS`) before any request, with a message that names no cookie data. Jobs with `useCookies = false` send no cookie. The desktop CLI `--cookies` argument path is untouched.

Commands:
- `./gradlew :shared:core:jvmTest` — 623 tests, 0 failures (includes `EngineCookieTest`, `CookieAttachmentTest`, and the new `HttpRequestTest` cases).
- `./gradlew :shared:core:compileTestKotlinWasmJs :shared:core:compileTestKotlinIosSimulatorArm64` — compiles, 0 errors.
- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.YtDlpArgumentsTest"` — passes (`--cookies` keeps the stored path as two arguments).

Next slice: Android and iOS `CookieStore` plus `pickCookieFile`, same validation as `DesktopCookieStore`, app-private storage, and backup exclusions; the web gap is recorded without putting bytes on the page.

**Slice 3 — Android/iOS stores and pickers (2026-09-30).** Android `AndroidCookieStore` (+ `AndroidCookieJarSource`) writes app-private `filesDir/cookies.txt`, validates with the shared `NetscapeCookieFile`, replaces atomically, deletes, and removes the picked cache copy; `AndroidCookiePickerBridge` + `MainActivity` present the system document picker. Backup exclusions are in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`. iOS `IosCookieStore` (+ `IosCookieJarSource`) writes `Application Support/AnyDownload/cookies.txt`, validates, replaces, deletes, marks the file with `NSURLIsExcludedFromBackupKey`, and removes the picker's temporary copy; `IosCookiePicker` presents `UIDocumentPickerViewController`. The shared `CookieFilePicker` is now suspend so the mobile pickers can be async; `DesktopCookieStore` now uses the shared validation. Web: the extension record is a gap (MV3 `fetch` cannot set `Cookie`, no per-host cookie rules yet), the page keeps `CookieStore.Unavailable`, and no cookie bytes reach the page or localStorage.

Settings copy (owner, 2026-09-30): “A manual copy of app data includes the cookie file.” There is no app-level cipher; the platform backup exclusion is the only automatic-transfer control.

Commands:
- `./gradlew :apps:android-engine-tests:test --tests "com.anydownlod.android.engine.cookies.*"` — 10 tests, 0 failures.
- `./gradlew :apps:android:compileDebugKotlin :apps:android:processDebugResources` — passes.
- `./gradlew :shared:ui:compileKotlinIosSimulatorArm64` — compiles.
- `./gradlew :shared:ui:jvmTest --tests "com.anydownlod.ui.SettingsUiTest"` — passes.
- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.store.DesktopCookieStoreTest"` — passes.
- `./gradlew :apps:web:compileKotlinWasmJs` — compiles.

Next slice: desktop Chrome/Firefox/Safari snapshot with a synthetic store only in the default tests.

**Slice 4 — desktop browser snapshot (2026-09-30).** `anydownload` adds `FirefoxCookieStore` (SQLite `moz_cookies`, plain text), `ChromeCookieStore` (SQLite `cookies`, `v10`/`v11` AES-128-CBC with the browser's own OS secret), and `SafariCookieStore` (binarycookies parser; string offsets are record-relative, verified against the `browser_cookie3` Safari format), plus `BrowserStoreLocator`, `ChromeSecrets`, and `DesktopBrowserCookieImport`. The importer copies the chosen store to a temp snapshot, reads it once, closes it, writes Netscape text through `CookieStore.importText`, and deletes the snapshot. Windows Chrome DPAPI and every browser other than Chrome/Firefox/Safari are recorded gaps. The Settings screen has a separate consent dialog; nothing is read before the user picks a browser. `DesktopCookieJarSource` now feeds the desktop Kotlin engine's per-job jar snapshot, and `sqlite-jdbc` is desktop-only with a license row. Default tests build synthetic SQLite/binarycookies fixtures and inject the locator and Chrome secret; no test reads a real profile.

Commands:
- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.cookies.*"` — 7 tests, 0 failures.
- `./gradlew :shared:ui:jvmTest --tests "com.anydownlod.ui.SettingsUiTest"` — 5 tests, 0 failures (consent step + chosen browser).
- `./gradlew :shared:core:jvmTest` — includes the new `NetscapeCookieFile.format` round-trip cases.

Next slice: settings status Not configured / Configured / Error from the real file, replace/delete during active work, restart from a synthetic file.

**Slice 5 — status, replace, delete, restart (2026-09-30).** `CookieStore` gained `status(): CookieStatus` with `NotConfigured`, `Configured`, and `Error(UNREADABLE | NO_COOKIES | ALL_EXPIRED)`; the value carries no name, value, or path. Desktop, Android, and iOS compute it from the real stored file (excluding expired rows), and `SettingsViewModel` syncs it on open and after every import, browser snapshot, and delete, so the add-form gate never stays on for an empty or expired file. The Settings screen labels the three states and enables Replace and Delete for an Error file too. Deletion during active work is proven at the engine level: an in-flight job keeps its jar snapshot and its redirect hop still carries the cookie, while a job started after the delete fails typed (`INVALID_URL_OPTIONS`). Restart is proven with fresh store instances over the same synthetic file on desktop (the frozen path is reloaded from settings.json) and Android (same app-private directory).

Commands (sequential runs; `org.gradle.parallel=true` makes the timing-sensitive fixture suites flaky when tasks overlap):
- `./gradlew --no-parallel :shared:core:jvmTest --rerun-tasks` — 627 tests, 0 failures.
- `./gradlew --no-parallel :shared:ui:jvmTest --rerun-tasks` — 105 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 142 tests, 0 failures, 15 skipped (live-only).
- `./gradlew --no-parallel :apps:android-engine-tests:test --rerun-tasks` — 41 tests, 0 failures.
- `:shared:ui:compileKotlinIosSimulatorArm64`, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs` — build clean.

Next slice: final verification, E-16, the T-018 boxes, and Phase 11 status.

**Slice 6 — verification (2026-09-30).** All acceptance boxes above are checked because every statement landed. Platform limits recorded: Netscape file only with a 1 MiB cap; Android, iOS, and web never read a browser database; web stores no cookie file (the extension records a gap and the page sees status only); desktop supports Chrome, Firefox, and Safari only, with Windows Chrome DPAPI unsupported; a browser's own store may be decrypted with that browser's OS secret, which is not a cipher for `cookies.txt`; the stored file has no app-level cipher; Android Auto Backup and iOS backup exclude it; a manual copy of app data includes it. Cookies do not guarantee access and do not authorize an unpermitted download: the app still refuses DRM/paywall bypass and the user must have permission to download the media. The E-16 row now records this scope. One test-only fix during verification: `YoutubePlaylistEngineTest.PlaylistTransfer` recorded request URLs in a plain `MutableList` while child jobs ran concurrently; it now uses a `ConcurrentLinkedQueue`, which removed the intermittent lost-request failure (6/6 repeat runs pass). Live-network and iOS-simulator runs remain fixture-only here, a recorded phase limit.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test --rerun-tasks` — BUILD SUCCESSFUL; core 627, ui 105, desktop 142 (15 live skipped), android-engine 41; 0 failures.
- `./gradlew --no-parallel :apps:android:compileDebugKotlin :shared:ui:compileKotlinIosSimulatorArm64 :apps:web:compileKotlinWasmJs` — BUILD SUCCESSFUL.
