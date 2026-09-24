# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Next: Phase D4 planned (2026-09-24).** [Extractor core and YouTube](vault/00-project/Phase-4-Extractor-Core-and-YouTube.md) ([ADR-008](vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)): an `InfoExtractor` base, format-spec selector, `_TESTS` harness, and port manifest, then YouTube single video on all four hosts — first the JS-less `visionos` client, then yt-dlp-ejs `0.8.0` (bundled, hash-verified) on an embedded QuickJS runtime. Single-file formats only; the media toolkit is still not built. Progress toward yt-dlp is measured in the [equivalence matrix](vault/01-product/Ytdlp-equivalence.md) (1,751 upstream extractors at tag `2026.08.19`). Nothing from D4 has landed yet.
>
> **Status: Phase D4 verified (2026-09-24).** The [extractor core and YouTube single video](vault/00-project/Phase-4-Extractor-Core-and-YouTube.md) ([ADR-008](vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)) runs on every host family: the shared Kotlin extractor core (InfoExtractor/InfoDict/MediaFormat, registry, helpers, format-spec selector, test harness, port manifest) plus YouTube for one public video, first with the JS-less `visionos` client and then with the bundled yt-dlp-ejs 0.8.0 solver on an embedded runtime (Zipline QuickJS 2021-03-27 on desktop/Android/iOS, the page's own JavaScript on web). Desktop, iOS, and Android resolve a matched YouTube URL in Kotlin without the CLI or Chaquopy; web carries every request through the Manifest V3 extension and the page runs the solver itself. HLS/DASH manifests download as one file with no FFmpeg. Installed `yt-dlp` (desktop) and Chaquopy + pinned `yt-dlp==2026.8.19` (Android) still own every URL the registry does not match and are the opt-in oracle. The media toolkit (FFmpeg/MediaMuxer/AVFoundation) is **recorded in ADR-007 and not built**: single-file formats only. Prior phases: [D3](vault/00-project/Phase-3-Generic-Extractor.md) / [ADR-007](vault/03-decisions/ADR-007-Generic-extractor-phase.md), [D2](vault/00-project/Phase-2-Local-Kotlin-Engine.md) / [ADR-006](vault/03-decisions/ADR-006-Local-http-engine-phase.md), and [D1](vault/00-project/Phase-1-Desktop-MeTube.md) / [ADR-005](vault/03-decisions/ADR-005-Desktop-metube-phase.md).

The product name is **AnyDownload**. The repository is named [`anydownlod`](https://github.com/lucassales2/anydownlod) to match the original project folder.

## Product direction

A local app that ports [yt-dlp](https://github.com/yt-dlp/yt-dlp) to Kotlin and brings [MeTube](https://github.com/alexta69/metube) workflows onto the device. No backend and no app login. Store publication is out of scope; this is a portfolio project.

- Video, audio-only, captions, and thumbnail downloads; format, codec, and quality selection.
- Playlists, channels, batch URL import/export, persistent queues, live progress, cancellation, and retries.
- Completed-download history, file export, custom folders, and output naming templates.
- Cookies for content the user is authorized to access; global options, presets, and security-scoped overrides.
- Channel/playlist subscriptions, clipping, chapter splitting, and SponsorBlock options.
- Platform-specific sharing. The engine and queue live in the app.

These are **planned capabilities, not implemented features**. The [feature-parity matrix](vault/01-product/Feature-parity.md) records the reviewed MeTube baseline, delivery milestones, and deliberate differences. The first vertical slice is one local URL on each target, not full site coverage.

“Any” means **the sites the Kotlin port of yt-dlp can handle**, with yt-dlp's coverage as the goal. Availability varies with site changes, geography, and media restrictions. DRM circumvention is not a goal. Download only content you own or have permission to download, subject to applicable law and platform terms.

## Platform strategy

| Target | Execution (D3) | D3 fixture HTML result (2026-09-24) |
| --- | --- | --- |
| Desktop | Shared Kotlin HTTP engine incl. the generic subset; installed `yt-dlp` CLI stays for unresolved URLs (`apps/desktop` only). | **Pass** — fixture HTML via Kotlin, no process; unresolved page still on the CLI path (yt-dlp 2026.08.19 on PATH). |
| iOS | Shared engine + in-process NSURLSession + sandbox `Documents` store. Foreground-only; unresolved HTML fails typed “extractor not implemented”. | **Pass** — native simulator run downloaded the fixture media into the sandbox; unresolved page fails typed. |
| Android | Shared engine wired in the app graph; Chaquopy + pinned yt-dlp adapter behind an `apps/android`-only port. | **Pass (JVM-equivalent)** — `apps/android-engine-tests` proves the Kotlin route and the Chaquopy fallback. Debug APK assembles with AGP 9.1.1. |
| Web | Compose/Wasm UI. The Manifest V3 extension holds `host_permissions` + `downloads`, fetches the page bytes and saves the media; the page never fetches an origin. | **Pass** — real Chromium (Brave 153) run: extension service worker fetched the fixture page and the media; file saved; without the extension Add refuses. |

### Phase D4: extractor core and YouTube single video

- **Extractor core:** `com.anydownlod.core.extract` (InfoExtractor base, InfoDict/MediaFormat, URL registry, HTTP helper, helpers) with `com.anydownlod.core.format` (format-spec parser/evaluator, typed-options compiler) and `com.anydownlod.core.download` (HLS/DASH parsers, AES-128 in Kotlin, fragment downloader). `port/manifest.json` records every translated module with its upstream path, tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), and the Unlicense notice; the generated coverage table lives in the [equivalence note](vault/01-product/Ytdlp-equivalence.md).
- **YouTube:** one public video through the JS-less `visionos` client and, with the runtime, the `web` client; the bundled yt-dlp-ejs 0.8.0 scripts are hash-verified at build time and never downloaded at runtime. No playlists, channels, live streams, comments, subtitles, cookies, PO-token providers, or other sites.
- **Hosts (verified 2026-09-24):**

| Target | Runtime | Formats with runtime | Formats without runtime | Result |
| --- | --- | --- | --- | --- |
| Desktop | Zipline QuickJS 2021-03-27 | 27 (web + visionos, needsJs 0) | 27 (visionos, needsJs 0) | **Pass** — live Kotlin M4A download completed with no CLI process; oracle diffs 0/0/0; unmatched URLs still go to the installed CLI; a local HLS fixture downloads byte-exact as one file. |
| Android | Zipline QuickJS 2021-03-27 | same shared engine | same shared engine | **Pass (JVM-equivalent)** — `apps/android-engine-tests` (14 tests) plus the Debug APK with AGP 9.1.1; no emulator/AVD is available in this environment. |
| iOS | Zipline QuickJS 2021-03-27 | same shared engine | same shared engine | **Pass (fixtures)** — 279 simulator tests; the simulator's NSURLSession cannot reach YouTube (typed timeout, recorded in T-066/T-068), so the live YouTube row is **blocked** with the host live paths proving the code. Foreground-only. |
| Web | The page's own JavaScript (Worker) | 27 via the web client | 27 via visionos | **Pass** — Brave 153.1.95.104 (Chromium 153.0.8010.53): the extension service worker carried every network request, the page ran the solver in its own Worker, the page made **0** YouTube requests, and without the extension Add refuses. 22 extension node tests. |

- **Live and oracle runs (2026-09-24, public video only):** live stage 1 = 27 formats/needsJs 0; live stage 2 = 27 formats/needsJs 0, no regression, QuickJS 2021-03-27; oracle visionos 27/27 diffs 0, web+visionos 27/27 diffs 0, audio-only 10/10 diffs 0. No URLs, cookies, tokens, or media are recorded.
- **Honest limits (recorded, not fixed here):**
  - Single-file quality only: a selection that needs a merge or a transcode fails typed (`UNSUPPORTED_FORMAT`), and MP3/WAV/FLAC stay disabled until the media toolkit exists. No FFmpeg, MediaMuxer, or AVFoundation postprocessing is added in D4.
  - PO-token-gated formats are dropped and counted as needing JavaScript; playlists, channels, live streams, comments, subtitles, and cookies are not ported.
  - iOS downloads are foreground-only, and the iOS simulator cannot reach YouTube here; the live evidence is the host live paths.
  - Web download progress is the browser downloader's; MV3 `fetch` refuses forbidden request-header names (the extension reports the effective names); the Compose/Wasm page never fetches an origin.
  - The embedded QuickJS (2021-03-27) is older than the version yt-dlp-ejs recommends (2025-04-26); solve time varies per host and is bounded by the runtime timeout. YouTube changes faster than the pin; the port is pinned to yt-dlp `2026.08.19`.
- **Commands:** `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :shared:core:iosSimulatorArm64Test :apps:android:assembleDebug :tools:port-manifest:check`, opt-in live `-PliveExtractorTests=true`, opt-in oracle `-PytDlpOracle=true`, extension tests `node --test apps/web-extension/test/bridge.test.mjs`, and the wasm production bundle `:apps:web:wasmJsBrowserDistribution`.

### Phase D3: one generic-extractor subset

- **Home screen:** the idle screen is the paste-link field alone — no clipboard permission dialog, no automatic clipboard read, no options chrome. A compatible HTTP(S) link opens the metadata preview with **Download** plus a collapsible **Edit download** (video or audio, quality, format; captions, clips, cookies, and the disabled custom yt-dlp JSON stay out). Invalid and multi-line input stays on the field with a typed message.
- **Extractor:** `com.anydownlod.core.extract.GenericExtractor` (package `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract`) is a translated subset of yt-dlp's `generic.py` at tag `2026.08.19` (the Android pin `yt-dlp==2026.8.19`), with the Unlicense notice and `shared/core/NOTICE.md`. `<video src>`, `<audio src>`, and nested `<source src>` candidates resolve against the page URL, pass `UrlPolicy`, and exactly one survivor wins; zero or several fail typed and redacted. `generic.py` is not vendored; no process and no Python in common code.
- **Engine:** `HttpDownloadEngine` reads a bounded 512 KiB page body, runs the extractor, then streams the chosen media URL with the same direct-file path (policy on every hop, redirects, cancel discards the temp file). The media hop never re-extracts; HTML again fails typed.
- **Honest limits (recorded, not fixed here):**
  - iOS downloads are foreground-only; suspending the app suspends the transfer and no completed file is claimed on relaunch. No background `URLSession` is added in this phase.
  - On web, closing the tab ends the page's queue view of the job; the browser's downloader (started through the extension) can continue and finish the file after the tab closes, so a saved file can outlive the tab. The page shows completion only while it can still hear the extension.
  - The desktop classifier fetches up to one bounded page body per submit to route HTML; a HEAD-only host that breaks on GET still falls back to the CLI path.
  - Web request port (`HttpTransfer` over the extension, T-056): the extension applies the same request-header allowlist, but MV3 `fetch` refuses forbidden names — `Origin`, `Referer`, `User-Agent`, `Cookie`, `Host`, `Content-Length`, `Accept-Encoding`, `Sec-*`, and `Proxy-*`. The extension reports the effective header set it actually sent (names only) so the extractor can report the difference; the page still never fetches an origin itself. For YouTube's innertube `POST`, MV3 forces `Origin` to the extension origin and YouTube answers 403 to any non-YouTube origin, so a static `declarativeNetRequest` rule (`apps/web-extension/rules.json`) sets `Origin: https://www.youtube.com` and the visionos user agent on `youtube.com/youtubei/*`. Web download progress is the browser downloader's: the page shows unknown progress until `chrome.downloads` reports completion. The page runs the bundled EJS solver in its own JavaScript (a Worker built from a Blob URL, T-072) with the CSP `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:; connect-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'` — no remote script origin, and the extension never runs the solver.
  - Chaquopy config stays opt-in (`-DchaquopyVersion=17.0.0`, `yt-dlp==2026.8.19`), `apps/android` only. The JVM-equivalent `apps/android-engine-tests` suite is the Android engine evidence.
- **Later, explicitly:** the rest of the generic extractor and site extractors, YouTube / yt-dlp-ejs, merge / audio extraction / clips (toolkit recorded in ADR-007, not built), Spotify matching, free-form yt-dlp JSON, and retiring the desktop CLI / Chaquopy.

Toolchain (verified 2026-09-24, macOS 26.5.2 arm64): Gradle 9.7.1, Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.1.1, JDK 21, Xcode 26.5, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target, Chromium 153 (Brave 153.1.95.104) for the extension check, installed `yt-dlp` 2026.08.19 on PATH for the desktop CLI fallback.

The later engine is shared Kotlin and does not shell out to the Python yt-dlp CLI ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md)). Until that port exists, the desktop D1 adapter calls the installed CLI from `apps/desktop` only.

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## How D1 is put together

`shared/core` owns the domain, the `DownloadEngine` / `SubscriptionRepository` / `SettingsRepository` interfaces, validation, and the in-memory fakes every host can run. `shared/ui` owns the Compose screens and depends only on those interfaces. `apps/desktop` owns the JSON store and the only `ProcessBuilder` use, in `com.anydownlod.desktop.engine`; it resolves `yt-dlp` and `ffmpeg` from `PATH` and never bundles them. `shared/network` remains in the tree but is not part of the app path. The in-process Kotlin engine from [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) is still the later target; do not move the desktop process adapter into shared code.

| Module | Purpose |
| --- | --- |
| `shared/core` | Domain, `DownloadEngine` / repository interfaces, validation, **`HttpDownloadEngine`** + URL policy/classifier + **`GenericExtractor`** (yt-dlp subset, Unlicense notice), platform ports (`HttpTransfer`, `FileStore`, [`WebExtensionBridge`]), in-memory fakes |
| `shared/network` | Withdrawn server client. Kept in the tree, unused |
| `shared/ui` | Compose Multiplatform screens; depends on core interfaces, no process APIs |
| `apps/android` | Android application: real graph (HTTP engine + Chaquopy port) |
| `apps/android-engine-tests` | JVM-equivalent tests for the Android engine sources (variant-unblocked) |
| `apps/desktop` | Desktop host: JSON store, yt-dlp/ffmpeg adapter, routing engine, Compose window |
| `apps/web` | Compose/Wasm application: `WindowExtensionBridge` + `WebExtensionEngine`; never fetches an origin |
| `apps/web-extension` | Manifest V3 extension: host permissions + `downloads`, probes pages, fetches the HTML for the Kotlin extractor, saves the chosen media |
| `apps/ios` | SwiftUI/Xcode host for the `AnyDownloadKit` framework; real iOS graph |

Provisional pinned toolchain: Gradle 9.7.1 (wrapper, checksum-pinned), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.1.1, Ktor 3.6.0, JDK 21, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target. Minimum platform versions are **not decided**; T-004 and T-006 own that. Intel iOS simulators are unsupported because Compose Multiplatform 1.12 no longer publishes an `iosX64` variant.

### Run the desktop app

`yt-dlp` and `ffmpeg` must be installed and on `PATH`; nothing is bundled. Downloads write under the folder chosen in Settings (default `~/Downloads/AnyDownload`). Queue, history, subscriptions, and settings persist outside the download root in the OS app-data folder (`~/Library/Application Support/AnyDownload` on macOS).

```sh
./gradlew :apps:desktop:run
./gradlew :apps:desktop:test    # store, engine, parser, and headless UI tests
```

```sh
./gradlew :shared:core:jvmTest                       # shared engine + platform tests
./gradlew :shared:core:iosSimulatorArm64Test         # native iOS tests (runs in the simulator)
./gradlew :apps:desktop:test                         # desktop store/engine/UI tests
./gradlew :apps:android-engine-tests:test            # JVM-equivalent Android engine tests
./gradlew :tools:port-manifest:run                   # regenerate the extractor coverage block from port/manifest.json
./gradlew :tools:port-manifest:check                 # validate port/manifest.json and the coverage block
node --test apps/web-extension/test/bridge.test.mjs  # extension logic tests
node tools/fixture-server/server.mjs --port 8123      # opt-in local fixture server for the live native/desktop checks
./gradlew :apps:web:wasmJsBrowserDevelopmentRun      # browser app (install the extension for downloads)
./gradlew :apps:web:wasmJsBrowserDistribution        # production web bundle
./gradlew :apps:android:assembleDebug                # blocked in this env (AGP/Compose mismatch, see above)
xcodebuild -project apps/ios/AnyDownload.xcodeproj -scheme AnyDownload \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Android builds need `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`. Regenerate `apps/ios/AnyDownload.xcodeproj` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) after changing `apps/ios/project.yml`.

## Documentation

| Start here | Purpose |
| --- | --- |
| [Vault home](vault/Home.md) | Documentation index and current priorities |
| [Kanban board](vault/Kanban.md) | Task status; each card links to acceptance criteria and dependencies |
| [Product brief](vault/01-product/Product-brief.md) | Goals, audience, scope, and success criteria |
| [MeTube feature parity](vault/01-product/Feature-parity.md) | Traceable feature inventory |
| [Architecture](vault/02-architecture/Architecture.md) | Local engine and the withdrawn remote design |
| [Platform matrix](vault/02-architecture/Platform-matrix.md) | What can be shared and what must be platform-specific |
| [Roadmap](vault/00-project/Roadmap.md) | Milestones and exit gates, without invented deadlines |
| [Open questions](vault/00-project/Open-questions.md) | Accepted decisions and what is still open |
| [Security and licensing](vault/04-delivery/Security-and-licensing.md) | Trust boundaries, privacy, dependency licenses, and distribution risks |

## Open the Obsidian vault

1. Clone the repository:
   ```sh
   git clone https://github.com/lucassales2/anydownlod.git
   cd anydownlod
   ```
2. In [Obsidian](https://obsidian.md), choose **Open folder as vault** and select this repository's **`vault/`** folder, not the repository root.
3. Open **`Home.md`** for the documentation index.
4. For the visual task board, go to **Settings → Community plugins**, enable community plugins for this vault if you trust them, and install/enable **Kanban** (`obsidian-kanban`, originally by mgmeyers, now maintained under the Obsidian community archive).
5. Open **`Kanban.md`**. If it opens as text, use the command palette's Kanban board-view command.

The board also works as an ordinary Markdown checklist on GitHub or in any editor. The checked-in plugin list does **not** install plugin binaries on a fresh clone. Plugin code and machine-specific workspaces are intentionally excluded from Git.

See the [documentation guide](vault/00-project/Documentation-guide.md) for board rules, templates, and privacy precautions.

## Repository layout

```text
README.md
CONTRIBUTING.md
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/                    # Wrapper and version catalog (libs.versions.toml)
port/                      # Port manifest and the upstream extractor name list (data only)
tools/port-manifest/       # Build-time Gradle task: validate the manifest, generate the coverage block
shared/
  core/                    # Domain, interfaces, validation, in-memory fakes
  network/                 # Withdrawn server client (unused by D1)
  ui/                      # Compose Multiplatform screens and iOS framework
apps/
  android/                 # Android application (fake-backed shell)
  desktop/                 # Desktop host: JSON store, yt-dlp adapter, window
  web/                     # Compose/Wasm application (fake-backed shell)
  ios/                     # SwiftUI/Xcode host for AnyDownloadKit (fake-backed shell)
vault/                     # Open this folder as an Obsidian vault
  Home.md
  Kanban.md
  00-project/              # Roadmap, questions, documentation workflow, glossary
  01-product/              # Product scope, parity, user journeys
  02-architecture/         # Platform constraints, domain, API proposal
  03-decisions/            # Architecture decision records; ADR-004 is accepted
  04-delivery/             # Testing, risks, security, licensing
  05-research/             # Dated upstream findings and pinned references
  06-tasks/                # One note per task
  99-templates/            # Task, ADR, and research templates
  .obsidian/               # Portable vault settings only
```

Treat module names, the `com.anydownlod` package, and pinned versions as provisional; minimum platform versions are still open. Phases D1 through D4 are complete: desktop runs the MeTube workflows through an installed yt-dlp, every host downloads one direct file, one generic-extractor subset runs on all four families, and the extractor core plus YouTube single video runs with and without the embedded JavaScript runtime ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) remains the end state). The next record is the media toolkit build (merge and audio extract per [ADR-007](vault/03-decisions/ADR-007-Generic-extractor-phase.md)) or the first named site, X/Twitter; the [T-022](vault/06-tasks/T-022-Parity-audit.md) parity audit and the target work in [T-004](vault/06-tasks/T-004-Validate-KMP-targets.md) still stand. Do not move the desktop process adapter into shared code.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Lucas Sales.

MIT covers this repository. It does not relicense MeTube, yt-dlp, YtDlp-kt, FFmpeg, or other upstream projects. Do not copy their implementation into this tree until their obligations are recorded. This project is not affiliated with them.
