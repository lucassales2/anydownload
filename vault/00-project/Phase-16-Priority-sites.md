---
type: phase
status: done
milestone: D16
tags: [project, engine, extractors]
---

# Phase 16 — Priority sites

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30.** 37 engineer-days. Depends on D15. Cumulative raw time through this phase: 90 days, about 18 weeks.

## Done looks like

Each file below has harness cases from public fixtures, a manifest row, and a registry entry. Desktop and Android stop sending that URL to yt-dlp.

- Twitter remainder, 3 days: the other five classes in `twitter.py` (cards, Amplify, broadcasts, Spaces, shortener). No bearer token and no guest token in the repo.
- Huge, 4 days each, 16 days: `bilibili.py` (111 KB), `vimeo.py` (98 KB), `peertube.py` (82 KB), `bbc.py` (80 KB).
- Large, 2 days each, 18 days: TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, archive.org.

## Task

[T-125](../06-tasks/T-125-Priority-sites.md).

## Landed

All 14 files are in the registry, each with harness cases and manifest rows:

- Twitter remainder: cards, Amplify, broadcasts, Spaces, shortener (`TwitterRemainder.kt`).
- Huge: Bilibili, Vimeo, PeerTube, BBC.
- Large: TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, archive.org.

Every file is Partial where the manifest says so; classes without a translated subset carry planned rows with the reason. No bearer token, guest token, cookie, or signed media URL is stored or committed, and no upstream source is vendored. Desktop and Android route matched URLs to Kotlin through the shared production registry; unmatched URLs still fall through to the desktop CLI or Android Chaquopy.

Commands (2026-09-30):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 838, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 35 partial, 80 planned, 1,636 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Limits: live network runs were not part of the default suites; the evidence is the synthetic fixtures. Classes marked Partial keep the named gaps in their manifest scope (login walls, DRM walls, private API paths, and paginated listings). iOS live-network playback remains a simulator gap owned by D12, not this phase.
