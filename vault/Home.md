---
title: AnyDownload Home
type: index
tags: [project, index]
---

# AnyDownload

**Phase:** D4 verified 2026-09-24 (extractor core and YouTube single video) · **Targets:** iOS, Android, web (extension), desktop · **Name:** AnyDownload

A local Kotlin Multiplatform downloader. The engine is a Kotlin port of yt-dlp, and the product surface is MeTube's workflows. No backend and no app login. This vault is the living project plan. A draft remote-client scaffold exists and does not match this direction.

**[Task board](Kanban.md)** · **[Roadmap](00-project/Roadmap.md)** · **[Open questions](00-project/Open-questions.md)** · **[Public repository](https://github.com/lucassales2/anydownlod)**

## Start here

1. Read [Phase 4 — Extractor core and YouTube](00-project/Phase-4-Extractor-Core-and-YouTube.md), [ADR-008](03-decisions/ADR-008-Extractor-core-and-youtube-phase.md), and the [yt-dlp equivalence matrix](01-product/Ytdlp-equivalence.md). D4 is verified; the next phase is the media toolkit build or the first named site.
2. Take the first Ready D4 card on [Kanban](Kanban.md). [T-055](06-tasks/T-055-Port-manifest-and-equivalence-matrix.md) is first: the port manifest and the generated coverage table.
3. [ADR-004](03-decisions/ADR-004-Local-kotlin-engine.md) is still the end state. D4 builds the extractor core and translates YouTube for a single video, JS-less first, then with yt-dlp-ejs. The media toolkit stays later; downloads are single-file formats.

## Accepted direction — 2026-09-21

- Product name is AnyDownload. The app is local-only and does not require a backend or a user login.
- Port yt-dlp to Kotlin and port MeTube's workflows into the app. Site coverage follows that port.
- Targets are iOS, Compose/Wasm, Android, and desktop.
- Store publication is out of scope; this is a portfolio project.

License notes for translated extractor code are in T-006. D2 proved a direct-file download on each host. D3 shipped the link-only home screen and one generic subset. D4 built the extractor core and YouTube single video and was verified on 2026-09-24. The end goal, recorded 2026-09-24 in ADR-008, is yt-dlp feature equivalence: the core engine plus the extractor catalog over time, measured in the equivalence matrix. The media toolkit is recorded in ADR-007 and not built yet.

## Documentation map

### Project

- [Documentation guide](00-project/Documentation-guide.md) — opening the vault, Kanban workflow, templates, privacy.
- [Roadmap](00-project/Roadmap.md) — milestone scopes and exit criteria.
- [Phase 1 — Desktop MeTube](00-project/Phase-1-Desktop-MeTube.md) — D1, done.
- [Phase 2 — Local HTTP engine](00-project/Phase-2-Local-Kotlin-Engine.md) — D2, done.
- [Phase 3 — Generic extractor subset](00-project/Phase-3-Generic-Extractor.md) — D3, done.
- [Phase 4 — Extractor core and YouTube](00-project/Phase-4-Extractor-Core-and-YouTube.md) — done 2026-09-24: core, YouTube JS-less, then EJS.
- [Open questions](00-project/Open-questions.md) — approval queue.
- [Glossary](00-project/Glossary.md) — shared terminology.

### Product

- [Product brief](01-product/Product-brief.md) — goals, non-goals, release definition.
- [Feature parity](01-product/Feature-parity.md) — MeTube baseline → planned tasks.
- [yt-dlp equivalence](01-product/Ytdlp-equivalence.md) — core engine rows by hand, extractor coverage generated from `port/manifest.json`.
- [User flows](01-product/User-flows.md) — add, monitor, export, subscribe, recover.

### Architecture and decisions

- [Architecture](02-architecture/Architecture.md)
- [Platform matrix](02-architecture/Platform-matrix.md)
- [Download lifecycle](02-architecture/Download-lifecycle.md)
- [API outline](02-architecture/API-outline.md)
- [Decision log](03-decisions/Decision-log.md)

### Delivery and research

- [Testing strategy](04-delivery/Testing-strategy.md)
- [Risk register](04-delivery/Risk-register.md)
- [Security and licensing](04-delivery/Security-and-licensing.md)
- [Upstream review](05-research/Upstream-review.md) — reviewed 2026-09-16, with pinned source references.
- [Client yt-dlp options](05-research/Client-yt-dlp-options.md) — reviewed 2026-09-23. Python packagers versus the Kotlin port on web, iOS, and Android, plus Spotify via a spotDL-style YouTube match. Does not change ADR-004.

### Templates

- [Task template](99-templates/Task-template.md)
- [ADR template](99-templates/ADR-template.md)
- [Research template](99-templates/Research-template.md)

## Scope reminders

- Site support follows the Kotlin port of yt-dlp. The goal is yt-dlp's coverage. Phase D1 reaches that coverage on desktop by calling the installed yt-dlp, then the port replaces that adapter.
- Spotify URLs use that engine after a YouTube match ([T-037](06-tasks/T-037-Spotify-youtube-match.md)). D1 does not implement the match.
- MeTube-equivalent workflows are the target, not a pixel-for-pixel copy or MeTube protocol compatibility.
- Downloads finish on the device. There is no server job.
- This vault is public. Never paste real credentials or private-media links here.
