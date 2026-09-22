# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Status: planning, plus a client scaffold that does not match the accepted architecture.** The documentation vault and task board are the source of truth. An early skeleton (task [T-008](vault/06-tasks/T-008-Scaffold-KMP-clients.md)) exists under `shared/` and `apps/`. It was drafted as a remote API client. The accepted direction is a local Kotlin engine ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md)). There is no download implementation yet.

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

| Target | Execution |
| --- | --- |
| Android | In-app Kotlin engine and Compose UI. A download runs while the app process is allowed to run. |
| iOS | Same shared engine. Sandbox file export. The system can suspend the app. |
| Desktop | Same shared engine on Compose/JVM for Windows, macOS, and Linux. |
| Web | Compose/Wasm. No Python subprocess. Cross-origin fetches and in-browser media processing still have to be proven. |

The engine is shared Kotlin. It does not shell out to the Python yt-dlp CLI. See [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md).

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## Client scaffold (unreviewed)

The scaffold is a remote-client draft: shared domain models, a typed API client, a Compose Multiplatform shell that calls `GET /api/v1/capabilities`, and one host per platform. That shape is superseded. The next scaffold work replaces the API client with an in-process engine. See the [roadmap](vault/00-project/Roadmap.md).

| Module | Purpose |
| --- | --- |
| `shared/core` | Domain models, job-state helpers, source-URL pre-validation |
| `shared/network` | Draft Ktor API client. Superseded as the product path; HTTP adapters may remain. |
| `shared/ui` | Compose Multiplatform shell, capability-aware state, iOS framework entry point |
| `apps/android` | Android application module |
| `apps/desktop` | Compose Desktop application |
| `apps/web` | Compose/Wasm browser application, subject to the T-004 spike |
| `apps/ios` | SwiftUI host for the `AnyDownloadKit` framework, generated from `project.yml` |

Provisional pinned toolchain: Gradle 9.7.1 (wrapper, checksum-pinned), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.4.0, Ktor 3.6.0, JDK 21, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target. Minimum platform versions are **not decided**; T-004 and T-006 own that. Intel iOS simulators are unsupported because Compose Multiplatform 1.12 no longer publishes an `iosX64` variant.

```sh
./gradlew :shared:core:jvmTest :shared:network:jvmTest  # shared unit tests
./gradlew :apps:android:assembleDebug                   # app/build/outputs/apk/debug/app-debug.apk
./gradlew :apps:desktop:run                             # desktop window
./gradlew :apps:web:wasmJsBrowserDevelopmentRun         # browser app
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
  core/                    # Domain models and validation
  network/                 # Typed API client and DTOs
  ui/                      # Compose Multiplatform UI and iOS framework
apps/
  android/                 # Android application
  desktop/                 # Compose Desktop application
  web/                     # Compose/Wasm browser application
  ios/                     # SwiftUI/Xcode host for AnyDownloadKit
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

The scaffold predates ADR-004 and still targets a server. Treat module names, the `com.anydownlod` package, and pinned versions as provisional. Next work is a local download on each target ([T-004](vault/06-tasks/T-004-Validate-KMP-targets.md)).

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Lucas Sales.

MIT covers this repository. It does not relicense MeTube, yt-dlp, YtDlp-kt, FFmpeg, or other upstream projects. Do not copy their implementation into this tree until their obligations are recorded. This project is not affiliated with them.
