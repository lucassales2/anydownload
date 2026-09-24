# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Status: Phase D3 verified (2026-09-24).** The [generic extractor subset](vault/00-project/Phase-3-Generic-Extractor.md) ([ADR-007](vault/03-decisions/ADR-007-Generic-extractor-phase.md)) works on every host family: one local HTML fixture with a single `<video>`/`<audio>`/`<source>` downloads on desktop (shared Kotlin HTTP engine), iOS (sandbox, foreground-only), web (Manifest V3 extension fetches the page and the media; the Compose/Wasm page never fetches an origin), and Android (same shared engine; the pre-existing AGP 9.0.0 vs Compose-1.12 AAR metadata blocker still prevents the APK assemble, so the JVM-equivalent engine tests are the Android evidence — T-041/T-048). Installed `yt-dlp` (desktop) and Chaquopy + pinned `yt-dlp==2026.8.19` (Android) still own every URL the subset does not resolve. A simple media page needs neither; YouTube and postprocessing stay later. The media toolkit (FFmpeg/MediaMuxer/AVFoundation) is **recorded in ADR-007 and not built**. Prior phases: [D2](vault/00-project/Phase-2-Local-Kotlin-Engine.md) / [ADR-006](vault/03-decisions/ADR-006-Local-http-engine-phase.md) and [D1](vault/00-project/Phase-1-Desktop-MeTube.md) / [ADR-005](vault/03-decisions/ADR-005-Desktop-metube-phase.md).

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
| Android | Shared engine wired in the app graph; Chaquopy + pinned yt-dlp adapter behind an `apps/android`-only port. APK build **blocked** in this environment. | **Pass (JVM-equivalent)** — `apps/android-engine-tests` proves the Kotlin route and the Chaquopy fallback; APK assemble remains blocked (AGP/Compose mismatch, T-041). |
| Web | Compose/Wasm UI. The Manifest V3 extension holds `host_permissions` + `downloads`, fetches the page bytes and saves the media; the page never fetches an origin. | **Pass** — real Chromium (Brave 153) run: extension service worker fetched the fixture page and the media; file saved; without the extension Add refuses. |

### Phase D3: one generic-extractor subset

- **Home screen:** the idle screen is the paste-link field alone — no clipboard permission dialog, no automatic clipboard read, no options chrome. A compatible HTTP(S) link opens the metadata preview with **Download** plus a collapsible **Edit download** (video or audio, quality, format; captions, clips, cookies, and the disabled custom yt-dlp JSON stay out). Invalid and multi-line input stays on the field with a typed message.
- **Extractor:** `com.anydownlod.core.extract.GenericExtractor` (package `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract`) is a translated subset of yt-dlp's `generic.py` at tag `2026.08.19` (the Android pin `yt-dlp==2026.8.19`), with the Unlicense notice and `shared/core/NOTICE.md`. `<video src>`, `<audio src>`, and nested `<source src>` candidates resolve against the page URL, pass `UrlPolicy`, and exactly one survivor wins; zero or several fail typed and redacted. `generic.py` is not vendored; no process and no Python in common code.
- **Engine:** `HttpDownloadEngine` reads a bounded 512 KiB page body, runs the extractor, then streams the chosen media URL with the same direct-file path (policy on every hop, redirects, cancel discards the temp file). The media hop never re-extracts; HTML again fails typed.
- **Honest limits (recorded, not fixed here):**
  - iOS downloads are foreground-only; suspending the app suspends the transfer and no completed file is claimed on relaunch. No background `URLSession` is added in this phase.
  - On web, closing the tab ends the page's queue view of the job; the browser's downloader (started through the extension) can continue and finish the file after the tab closes, so a saved file can outlive the tab. The page shows completion only while it can still hear the extension.
  - The desktop classifier fetches up to one bounded page body per submit to route HTML; a HEAD-only host that breaks on GET still falls back to the CLI path.
  - The Android APK cannot assemble in this build environment (AGP 9.0.0 vs Compose 1.12.0 AAR metadata mismatch); the JVM-equivalent `apps/android-engine-tests` suite is the Android evidence. Chaquopy config stays opt-in (`-DchaquopyVersion=17.0.0`, `yt-dlp==2026.8.19`), `apps/android` only.
- **Later, explicitly:** the rest of the generic extractor and site extractors, YouTube / yt-dlp-ejs, merge / audio extraction / clips (toolkit recorded in ADR-007, not built), Spotify matching, free-form yt-dlp JSON, and retiring the desktop CLI / Chaquopy.

Toolchain (verified 2026-09-24, macOS 26.5.2 arm64): Gradle 9.7.1, Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, JDK 21, Xcode 26.5, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target, Chromium 153 (Brave 153.1.95.104) for the extension check, installed `yt-dlp` 2026.08.19 on PATH for the desktop CLI fallback.

The later engine is shared Kotlin and does not shell out to the Python yt-dlp CLI ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md)). Until that port exists, the desktop D1 adapter calls the installed CLI from `apps/desktop` only.

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## How D1 is put together

`shared/core` owns the domain, the `DownloadEngine` / `SubscriptionRepository` / `SettingsRepository` interfaces, validation, and the in-memory fakes every host can run. `shared/ui` owns the Compose screens and depends only on those interfaces. `apps/desktop` owns the JSON store and the only `ProcessBuilder` use, in `com.anydownlod.desktop.engine`; it resolves `yt-dlp` and `ffmpeg` from `PATH` and never bundles them. `shared/network` remains in the tree but is not part of the app path. The in-process Kotlin engine from [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) is still the later target; do not move the desktop process adapter into shared code.

| Module | Purpose |
| --- | --- |
| `shared/core` | Domain, `DownloadEngine` / repository interfaces, validation, **`HttpDownloadEngine`** + URL policy/classifier + **`GenericExtractor`** (yt-dlp subset, Unlicense notice), platform ports (`HttpTransfer`, `FileStore`, [`WebExtensionBridge`]), in-memory fakes |
| `shared/network` | Withdrawn server client. Kept in the tree, unused |
| `shared/ui` | Compose Multiplatform screens; depends on core interfaces, no process APIs |
| `apps/android` | Android application: real graph (HTTP engine + Chaquopy port); blocked APK build in this env |
| `apps/android-engine-tests` | JVM-equivalent tests for the Android engine sources (variant-unblocked) |
| `apps/desktop` | Desktop host: JSON store, yt-dlp/ffmpeg adapter, routing engine, Compose window |
| `apps/web` | Compose/Wasm application: `WindowExtensionBridge` + `WebExtensionEngine`; never fetches an origin |
| `apps/web-extension` | Manifest V3 extension: host permissions + `downloads`, probes pages, fetches the HTML for the Kotlin extractor, saves the chosen media |
| `apps/ios` | SwiftUI/Xcode host for the `AnyDownloadKit` framework; real iOS graph |

Provisional pinned toolchain: Gradle 9.7.1 (wrapper, checksum-pinned), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, Ktor 3.6.0, JDK 21, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target. Minimum platform versions are **not decided**; T-004 and T-006 own that. Intel iOS simulators are unsupported because Compose Multiplatform 1.12 no longer publishes an `iosX64` variant.

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
node --test apps/web-extension/test/bridge.test.mjs  # extension logic tests
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

Treat module names, the `com.anydownlod` package, and pinned versions as provisional; minimum platform versions are still open. Phases D1, D2, and D3 are complete: desktop runs the MeTube workflows through an installed yt-dlp, every host downloads one direct file, and one generic-extractor subset runs on all four families ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) remains the end state). Next work is the [T-022](vault/06-tasks/T-022-Parity-audit.md) parity audit and the target work in [T-004](vault/06-tasks/T-004-Validate-KMP-targets.md); do not move the desktop process adapter into shared code.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Lucas Sales.

MIT covers this repository. It does not relicense MeTube, yt-dlp, YtDlp-kt, FFmpeg, or other upstream projects. Do not copy their implementation into this tree until their obligations are recorded. This project is not affiliated with them.
