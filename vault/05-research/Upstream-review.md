---
type: research
reviewed: 2026-09-16
tags: [research, dependencies, parity]
---

# Upstream review

[Home](../Home.md) · [Feature parity](../01-product/Feature-parity.md) · [Architecture](../02-architecture/Architecture.md) · [License review](../04-delivery/Security-and-licensing.md)

**Method:** inspected public GitHub metadata, README files, relevant build/source/UI/API files, and official platform documentation on 2026-09-16. No app, engine download, target build or runtime parity test was performed. Upstream source was consulted, not copied into this repository.

## Reference snapshots

| Project | Reviewed revision | Repository state / license metadata |
| --- | --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | [`c7fb478d21e9e59524befbe23f7801bb267fb880`](https://github.com/yt-dlp/yt-dlp/tree/c7fb478d21e9e59524befbe23f7801bb267fb880) | Active; Unlicense source. Bundled binaries have additional/different obligations. |
| [YtDlp-kt](https://github.com/dinaraparanid/YtDlp-kt) | [`e1947a1f7ad0e81aa8825b5958c585b1d338a36f`](https://github.com/dinaraparanid/YtDlp-kt/tree/e1947a1f7ad0e81aa8825b5958c585b1d338a36f) | Archived; GPL-3.0 metadata; last reviewed commit dated 2023-01-03. |
| [MeTube](https://github.com/alexta69/metube) | [`6708a882294a6e8c5ffe097354c7eee42eb0f309`](https://github.com/alexta69/metube/tree/6708a882294a6e8c5ffe097354c7eee42eb0f309) | Active; AGPL-3.0 metadata. This commit anchors the initial parity inventory. |

These are source commits, not a recommendation to ship untested `master` snapshots. Record tested releases/artifact hashes when selecting dependencies.

## yt-dlp findings

- [README and runtime dependencies](https://github.com/yt-dlp/yt-dlp/blob/c7fb478d21e9e59524befbe23f7801bb267fb880/README.md#dependencies): Python CLI/library ecosystem; supports a broad extractor catalog, not every URL or guaranteed access.
- FFmpeg/ffprobe are important for merging separate streams and postprocessing. Format/container selection does not imply every codec can play on every device.
- Current full YouTube support also requires **yt-dlp-ejs and a supported JavaScript runtime**; upstream recommends Deno among supported choices. Optional browser-impersonation dependencies may matter for some sites.
- [Release licensing](https://github.com/yt-dlp/yt-dlp/blob/c7fb478d21e9e59524befbe23f7801bb267fb880/README.md#licensing): source/PyPI license and bundled executable licenses differ; PyInstaller executables include GPLv3+ components.
- Site support requires frequent maintenance. Pin versions, expose diagnostics and validate updates/rollback; do not distribute uncontrolled engine changes during jobs.
- Python API options are not always a one-to-one transformation of CLI flags. Use documented mappings and integration tests.

**Implication:** define an engine adapter and a complete runtime/update/license inventory rather than assuming “add a Kotlin dependency” solves extraction.

## YtDlp-kt findings

- [README](https://github.com/dinaraparanid/YtDlp-kt/blob/e1947a1f7ad0e81aa8825b5958c585b1d338a36f/README.md) describes a CLI wrapper distributed via JitPack.
- [Build file](https://github.com/dinaraparanid/YtDlp-kt/blob/e1947a1f7ad0e81aa8825b5958c585b1d338a36f/build.gradle) applies `org.jetbrains.kotlin.jvm` (Kotlin 1.7.21 at this revision), not KMP targets.
- [Implementation](https://github.com/dinaraparanid/YtDlp-kt/blob/e1947a1f7ad0e81aa8825b5958c585b1d338a36f/src/main/kotlin/com/dinaraparanid/ytdlp_kt/YtDlp.kt) uses `java.io.File`, `ProcessBuilder`, `Runtime.exec` and external executables. Command construction/splitting and process lifecycle need review before any adoption.
- GitHub reports the repository as archived. Kotlin language use does not imply iOS/Wasm support or solve Android runtime packaging.

**Implication:** treat it as reference material, not the shared engine dependency. See [ADR-002](../03-decisions/ADR-002-Kotlin-wrapper.md).

## MeTube findings

- [README](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/README.md) describes a self-hosted yt-dlp web UI, multi-architecture containers, persistence, storage/options configuration, cookies, subscriptions and integrations.
- [UI](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/ui/src/app/app.html) and [format definitions](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/ui/src/app/interfaces/formats.ts) add important parity details: captions/thumbnail modes, codec profiles, clips, chapters, SponsorBlock, batch tools and subscription filtering/controls.
- [Format/postprocessing logic](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/app/dl_formats.py) shows format-dependent audio artwork, caption fallback/conversion and postprocessor behavior. Requested options are not all unconditional output guarantees.
- [Server handlers](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/app/main.py) use Python/aiohttp and **Socket.IO**. An ordinary WebSocket client is not sufficient for compatibility.
- The README explicitly warns that arbitrary per-download yt-dlp options may permit command execution. Secure parity needs a reviewed restriction rather than blindly copying that capability.
- Generic active-download pause/resume and queue reordering were not established in reviewed controls. Subscription pause/resume is confirmed. Full library management is outside MeTube's stated scope.
- Browser extensions, mobile helpers, shortcuts and Raycast are ecosystem integrations, not all built into the core server. Matching workflows and supporting their existing wire protocol are different requirements.

**Implication:** use a traceable [parity matrix](../01-product/Feature-parity.md), not a vague “like MeTube” checklist. Compare reusing a MeTube service versus implementing its behavior before committing to a new backend.

## Platform and vault references

- [KMP platform stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html) — distinguishes core KMP and Compose support; checked page lists Android/iOS/JVM Stable and Wasm/Compose web Beta. Recheck when choosing versions.
- [Compose Multiplatform and Jetpack Compose](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html), [Kotlin/Wasm overview](https://kotlinlang.org/docs/wasm-overview.html) — UI/runtime context.
- [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) — distribution feasibility requires a dedicated review, especially 5.2.3 and 2.5.2.
- [Obsidian vault help](https://help.obsidian.md/vault) and [community plugin registry](https://github.com/obsidianmd/obsidian-releases/blob/master/community-plugins.json) — portable Markdown vault with optional community Kanban.
- [Kanban release 2.0.51](https://github.com/community-archive/obsidian-kanban/releases/tag/2.0.51) — latest release returned by GitHub during setup. Original mgmeyers / registry repository URLs redirect to the community archive. Plugin ID remains `obsidian-kanban`; plugin code stays local and ignored.

## Next research

[T-004](../06-tasks/T-004-Validate-KMP-targets.md): actual four-target/UI/export experiment. [T-005](../06-tasks/T-005-Choose-backend-engine.md): runnable engine/backend comparison. [T-006](../06-tasks/T-006-Review-security-licensing.md): detailed security/license/store review. [T-022](../06-tasks/T-022-Parity-audit.md): runtime parity evidence against the pinned baseline.
