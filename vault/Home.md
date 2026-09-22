---
title: AnyDownload Home
type: index
tags: [project, index]
---

# AnyDownload

**Phase:** planning · **Targets:** iOS, web (Compose/Wasm), Android, desktop · **Name:** AnyDownload

A local Kotlin Multiplatform downloader. The engine is a Kotlin port of yt-dlp, and the product surface is MeTube's workflows. No backend and no app login. This vault is the living project plan. A draft remote-client scaffold exists and does not match this direction.

**[Task board](Kanban.md)** · **[Roadmap](00-project/Roadmap.md)** · **[Open questions](00-project/Open-questions.md)** · **[Public repository](https://github.com/lucassales2/anydownlod)**

## Start here

1. Read the [product brief](01-product/Product-brief.md) and [MeTube feature inventory](01-product/Feature-parity.md).
2. Review the [platform constraints](02-architecture/Platform-matrix.md). Compose/Wasm and iOS are in scope and still unproven for a local engine.
3. Review the [architecture](02-architecture/Architecture.md) and [ADR-004](03-decisions/ADR-004-Local-kotlin-engine.md).
4. Work through the ready tasks on [Kanban](Kanban.md). The next engineering gate is a local download on each target.

## Accepted direction — 2026-09-21

- Product name is AnyDownload. The app is local-only and does not require a backend or a user login.
- Port yt-dlp to Kotlin and port MeTube's workflows into the app. Site coverage follows that port.
- Targets are iOS, Compose/Wasm, Android, and desktop.
- Store publication is out of scope; this is a portfolio project.

License notes for ported code, and proof that Wasm and iOS can complete a download, are still open.

## Documentation map

### Project

- [Documentation guide](00-project/Documentation-guide.md) — opening the vault, Kanban workflow, templates, privacy.
- [Roadmap](00-project/Roadmap.md) — milestone scopes and exit criteria.
- [Open questions](00-project/Open-questions.md) — approval queue.
- [Glossary](00-project/Glossary.md) — shared terminology.

### Product

- [Product brief](01-product/Product-brief.md) — goals, non-goals, release definition.
- [Feature parity](01-product/Feature-parity.md) — MeTube baseline → planned tasks.
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

### Templates

- [Task template](99-templates/Task-template.md)
- [ADR template](99-templates/ADR-template.md)
- [Research template](99-templates/Research-template.md)

## Scope reminders

- Site support follows the Kotlin port of yt-dlp. The goal is yt-dlp's coverage; the first build is one URL on each target.
- MeTube-equivalent workflows are the target, not a pixel-for-pixel copy or MeTube protocol compatibility.
- Downloads finish on the device. There is no server job.
- This vault is public. Never paste real credentials or private-media links here.
