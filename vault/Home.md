---
title: AnyDownload Home
type: index
tags: [project, index]
---

# AnyDownload

**Phase:** planning · **Targets:** Android, iOS, desktop, web · **Working name:** AnyDownload

A Kotlin Multiplatform downloader inspired by MeTube, with yt-dlp as the proposed media engine. This vault is the living project plan; **no application has been implemented**.

**[Task board](Kanban.md)** · **[Roadmap](00-project/Roadmap.md)** · **[Open questions](00-project/Open-questions.md)** · **[Public repository](https://github.com/lucassales2/anydownlod)**

## Start here

1. Read the [product brief](01-product/Product-brief.md) and [MeTube feature inventory](01-product/Feature-parity.md).
2. Review the [platform constraints](02-architecture/Platform-matrix.md) before assuming yt-dlp can run on every device.
3. Review the [proposed architecture](02-architecture/Architecture.md) and [decision records](03-decisions/Decision-log.md).
4. Work through the ready tasks on [Kanban](Kanban.md); implementation begins only after the M0 exit gate is approved.

## Immediate decisions

- Confirm product/repository naming, intended users, and whether a user-managed remote server is acceptable.
- Test the Kotlin/Compose toolchain on all four targets, especially browser accessibility and iOS file export.
- Choose a backend strategy: a MeTube integration, a Kotlin API with an isolated yt-dlp worker, or a smaller Python service.
- Resolve license, cookie trust, and store-distribution constraints before selecting native runtime dependencies.

These are recommendations and questions, **not decisions already made on the owner's behalf**.

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

- Support follows the installed yt-dlp version; no promise to download every video or bypass DRM.
- MeTube-equivalent workflows are the target, not a pixel-for-pixel copy or automatic wire-protocol compatibility.
- “Downloaded on server” and “saved on this device” are different states.
- Local desktop/Android execution is a later feasibility track; the remote-first proposal supports all four clients.
- This vault is public. Never paste real credentials or private-media links here.
