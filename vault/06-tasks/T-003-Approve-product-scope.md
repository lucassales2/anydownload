---
id: T-003
type: task
priority: P0
milestone: M0
tags: [task, planning, product]
---

# T-003 — Approve product scope and naming

[Home](../Home.md) · [Kanban](../Kanban.md) · [Product brief](../01-product/Product-brief.md) · [Open questions](../00-project/Open-questions.md)

## Outcome

Owner-approved product boundaries and milestone definitions, including whether the app is local-only.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [x] Confirm working/final name and whether to retain the `anydownlod` repository spelling.
- [x] Decide target audience, server ownership/trust, single-owner versus multi-user scope, and whether standalone mobile operation is mandatory.
- [x] Approve MVP versus parity scope and pinned MeTube baseline; prioritize external integrations and clarify protocol compatibility.
- [x] Explicitly approve or revise security-scoped option differences and “supported sites, not every media URL” product language.
- [x] Record approver/date and update the brief, open questions, roadmap and ADR-001 as applicable.

## Evidence / notes

Owner, 2026-09-21:

- Product name is AnyDownload (`anydownload`). Repository slug stays `anydownlod` until a rename is requested.
- The app is local-only on iOS, Compose/Wasm, Android, and desktop. No backend, no app login, no multi-user server.
- Goal is a Kotlin port of yt-dlp's site support and MeTube's workflows. MeTube protocol compatibility is not required. The pinned 2026-09-16 MeTube review stays the workflow baseline.
- Store publication is out of scope. Portfolio builds only.
- Recorded in the product brief, open questions, roadmap, and [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). ADR-001 and ADR-003 are superseded.

Owner, 2026-09-23 ([ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md)):

- Q-09 is **allowlist only**. Typed options the app builds. Free-form yt-dlp JSON stays disabled. No shell, plugins, or path escape.
- Product language stays “supported sites, not every media URL.” D2 proves a direct HTTP(S) file on each host. Site coverage grows with the Kotlin port. Android Chaquopy and the desktop CLI are interim adapters, not a claim that every host already matches yt-dlp.
