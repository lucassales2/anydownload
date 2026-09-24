# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Status: Phase D2 verified (2026-09-23).** One direct HTTP(S) file download now completes on every host family, per [ADR-006](vault/03-decisions/ADR-006-Local-http-engine-phase.md) and [Phase 2](vault/00-project/Phase-2-Local-Kotlin-Engine.md): desktop uses the **shared Kotlin HTTP engine** (installed `yt-dlp` stays only for site URLs), iOS downloads into its sandbox via in-process NSURLSession (foreground-only), web downloads through a **Manifest V3 extension** whose page never fetches an origin, and Android has the same shared engine wired in its app graph (its APK remains blocked in this build environment by a pre-existing AGP 9.0.0 vs Compose-1.12 AAR metadata mismatch — T-041 records the Chaquopy/pinned-yt-dlp adapter config and the exact blocker). The yt-dlp **extractor port** of [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) has not started; D2 is the HTTP-only slice and explicitly does not translate extractors. Prior phase: [Phase 1](vault/00-project/Phase-1-Desktop-MeTube.md) / [ADR-005](vault/03-decisions/ADR-005-Desktop-metube-phase.md).

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

| Target | Execution (D2) |
| --- | --- |
| Desktop | Shared Kotlin HTTP engine for direct files; installed `yt-dlp` CLI stays for site URLs (`apps/desktop` only). |
| iOS | Shared engine + in-process NSURLSession + sandbox `Documents` store. Foreground-only; site URLs fail with “extractor not implemented”. |
| Android | Shared engine wired in the app graph; Chaquopy + pinned yt-dlp adapter behind an `apps/android`-only port. APK build blocked in this environment (see below). |
| Web | Compose/Wasm UI. A Manifest V3 extension holds `host_permissions` + `downloads` and saves files; the page performs no cross-origin fetch. |

### Phase D2: one direct file per host

- **Desktop:** route direct files through `HttpDownloadEngine` (`DesktopRoutingEngine`); yt-dlp is present on PATH here (`/opt/homebrew/bin/yt-dlp`) and the CLI path is preserved and unit-tested. Direct files never spawn a process.
- **iOS:** `IosHttpTransfer` (NSURLSession, one hop per call) + `IosFileStore` (POSIX, Documents). Verified by native tests incl. a real 1 MiB local-fixture download (COMPLETED, file on disk) and an unsigned simulator build + launch.
- **Web:** load the unpacked extension from `apps/web-extension` (see that folder's `README.md`). Without it, Add reports “extension required” and the page makes no network call.
- **Android:** the Android app variant cannot assemble in this environment: androidx Compose 1.12.0 AARs require AGP ≥ 9.1.0 (alpha-only) while the catalog pins AGP 9.0.0; the JVM-equivalent engine tests run under `apps/android-engine-tests`. Chaquopy config is validated (`-DchaquopyVersion=17.0.0`, pinned `yt-dlp==2026.8.19`) and only applies when requested, so default checkouts stay Chaquopy-free.

Toolchain (verified 2026-09-23, macOS 26.5 arm64): Gradle 9.7.1, Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, JDK 21, Xcode 26.5, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target, Chromium 153 (Brave) for the extension check.

The later engine is shared Kotlin and does not shell out to the Python yt-dlp CLI ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md)). Until that port exists, the desktop D1 adapter calls the installed CLI from `apps/desktop` only.

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## How D1 is put together

`shared/core` owns the domain, the `DownloadEngine` / `SubscriptionRepository` / `SettingsRepository` interfaces, validation, and the in-memory fakes every host can run. `shared/ui` owns the Compose screens and depends only on those interfaces. `apps/desktop` owns the JSON store and the only `ProcessBuilder` use, in `com.anydownlod.desktop.engine`; it resolves `yt-dlp` and `ffmpeg` from `PATH` and never bundles them. `shared/network` remains in the tree but is not part of the app path. The in-process Kotlin engine from [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) is still the later target; do not move the desktop process adapter into shared code.

| Module | Purpose |
| --- | --- |
| `shared/core` | Domain, `DownloadEngine` / repository interfaces, validation, **`HttpDownloadEngine`** + URL policy/classifier, platform ports (`HttpTransfer`, `FileStore`, [`WebExtensionBridge`]), in-memory fakes |
| `shared/network` | Withdrawn server client. Kept in the tree, unused |
| `shared/ui` | Compose Multiplatform screens; depends on core interfaces, no process APIs |
| `apps/android` | Android application: real graph (HTTP engine + Chaquopy port); blocked APK build in this env |
| `apps/android-engine-tests` | JVM-equivalent tests for the Android engine sources (variant-unblocked) |
| `apps/desktop` | Desktop host: JSON store, yt-dlp/ffmpeg adapter, routing engine, Compose window |
| `apps/web` | Compose/Wasm application: `WindowExtensionBridge` + `WebExtensionEngine`; never fetches an origin |
| `apps/web-extension` | Manifest V3 extension: host permissions + `downloads`, probes and saves direct files |
| `apps/ios` | SwiftUI/Xcode host for the `AnyDownloadKit` framework; real iOS graph |

Provisional pinned toolchain: Gradle 9.7.1 (wrapper, checksum-pinned), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, Ktor 3.6.0, JDK 21, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target. Minimum platform versions are **not decided**; T-004 and T-006 own that. Intel iOS simulators are unsupported because Compose Multiplatform 1.12 no longer publishes an `iosX64` variant.

### Run the desktop D1 app

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

Treat module names, the `com.anydownlod` package, and pinned versions as provisional; minimum platform versions are still open. Phase D1 is complete: desktop runs the MeTube workflows through an installed yt-dlp, and the Kotlin port remains [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md). Next work is the [T-022](vault/06-tasks/T-022-Parity-audit.md) parity audit and the target work in [T-004](vault/06-tasks/T-004-Validate-KMP-targets.md); do not move the desktop process adapter into shared code.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Lucas Sales.

MIT covers this repository. It does not relicense MeTube, yt-dlp, YtDlp-kt, FFmpeg, or other upstream projects. Do not copy their implementation into this tree until their obligations are recorded. This project is not affiliated with them.
