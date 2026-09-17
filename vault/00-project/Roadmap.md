---
type: roadmap
tags: [project, delivery]
---

# Roadmap

[Home](../Home.md) · [Kanban](../Kanban.md) · [Feature parity](../01-product/Feature-parity.md)

Dates and effort estimates are intentionally unset until feasibility work is complete. All milestones describe future work; the initial deliverable is documentation only.

| Milestone | Deliverable | Exit gate | Tasks |
| --- | --- | --- | --- |
| **M0 — Plan and de-risk** | Vault, public repository, approved scope, four-target feasibility, backend decision, security/license review, wireframes/API draft. | Owner accepts scope and architecture ADRs; target/toolchain evidence exists; distribution and license blockers have explicit decisions. No production scaffolding before this gate. | T-001–T-007 |
| **M1 — Remote vertical slice** | Shared clients on Android, iOS, desktop, web; authenticated engine; submit one authorized URL, show progress, export a result. | Demonstrate the same workflow on all four target families, including a failed job and an unauthenticated request rejection. Record desktop OS/browser coverage. | T-008–T-010 |
| **M2 — Reliable downloader core** | Durable queues, retries, playlists/channels, batch links, video/audio profiles, history, safe file delivery and storage. | Jobs recover after restart; reconnect does not duplicate work; files are not loaded wholly into memory; core acceptance tests pass. This is the usable MVP, not full MeTube parity. | T-011–T-014 |
| **M3 — MeTube parity candidate** | Captions, thumbnails, clips, chapters, SponsorBlock, safe presets/options, cookies, subscriptions, sharing, self-host configuration and updates. | Every parity row has tested evidence or a specifically approved/documented difference. No blanket “full parity” claim while gaps remain. | T-015–T-022 |
| **M4 — Release readiness** | Signed/distributable platform packages, accessibility and security audits, operations and user documentation. | Release checklist, dependency notices, supported-platform matrix, recovery tests, and distribution approvals complete. An iOS build is not App Store approval. | T-023 |
| **M5 — Optional local engines** | Desktop and Android local-execution feasibility and subsequent scope proposal. | Separate ADRs approve runtime packaging, updates, licensing, and cancellation/background behavior; no local feature claim based on a wrapper alone. | T-024–T-025 |

## Current priorities

1. [Approve scope and naming](../06-tasks/T-003-Approve-product-scope.md).
2. [Validate all four KMP targets](../06-tasks/T-004-Validate-KMP-targets.md).
3. [Compare backend/engine approaches](../06-tasks/T-005-Choose-backend-engine.md).
4. [Review security, licensing, and store constraints](../06-tasks/T-006-Review-security-licensing.md).
5. Turn the results into [wireframes and a reviewed API contract](../06-tasks/T-007-Define-UX-and-contract.md).

## Sequencing rules

- M0 experiments may use throwaway prototypes only after the owner starts that task; they are not part of this initial documentation setup.
- T-008/T-009 require the M0 gate. Android/iOS/web are not postponed to an unspecified “later” platform release.
- M3 work can be developed in parallel once each task's dependencies are met; security is part of M1, not a final hardening add-on.
- M5 is optional and does not block remote-mode parity or release. No standalone iOS/browser engine is promised.
- Deployment location, hosting cost, storage quotas, and number of users remain [open questions](Open-questions.md); this plan does not authorize any deployment.

## Release definitions

**Vertical slice:** proves architecture across platforms with one small end-to-end flow.

**MVP:** remote-mode core downloader with durable state and useful video/audio workflows.

**Parity candidate:** all reviewed MeTube features are represented and ready for evidence-based audit.

**Release:** platform, security, licensing, and operations gates passed. If a feature differs for security or platform reasons, publish the difference rather than calling it identical.
