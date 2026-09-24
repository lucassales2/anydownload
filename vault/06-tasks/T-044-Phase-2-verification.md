---
id: T-044
type: task
priority: P0
milestone: D2
tags: [task, verification]
---

# T-044 — Phase 2 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [T-004](T-004-Validate-KMP-targets.md)

## Outcome

Phase D2 is shown on all four families, or a host is recorded as blocked. This evidence closes T-004 for the HTTP-only slice. It does not claim a yt-dlp extractor port.

## Dependencies

- [T-038](T-038-Shared-http-engine.md) through [T-043](T-043-Web-extension-download.md).
- [T-006](T-006-Review-security-licensing.md) and the Q-09 close on [T-003](T-003-Approve-product-scope.md).

## Work

For each host, record pass or fail. Use a tiny public or locally served file you are allowed to download. Do not paste private URLs.

1. Desktop: direct file via Kotlin HTTP; one yt-dlp site URL still via CLI (or record that yt-dlp is missing).
2. Android: direct file via Kotlin HTTP; one site URL via Chaquopy, or a written Chaquopy blocker.
3. iOS: direct file in the sandbox; site URL shows extractor-not-implemented.
4. Web: extension present → direct file saves; extension absent → Add refuses; page does not fetch the origin.
5. Shared tests: `./gradlew :shared:core:jvmTest` and the host tests that exist.
6. Update README: D2 status, extension load, Chaquopy note, ADR-004 still later.
7. Fill T-004 acceptance boxes from this evidence, including toolchain versions and which desktop OS was run.
8. Document iOS suspension and browser tab-close honestly.

## Acceptance criteria

- [ ] Four-host table with pass/fail/blocked and versions.
- [ ] T-004 checkboxes updated from this run.
- [ ] README matches D2, not D1-only.
- [ ] No private URL, cookie, or media file was added to the repository.

## Evidence / notes

Not started.
