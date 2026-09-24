---
id: T-051
type: task
priority: P0
milestone: D3
tags: [task, verification]
---

# T-051 — Phase 3 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

Phase D3 is shown on all four families, or a host is recorded as blocked. This is one HTML fixture, not a yt-dlp site catalog and not YouTube.

## Dependencies

- [T-045](T-045-Generic-extractor-subset.md) through [T-050](T-050-Web-extension-generic.md).

## Work

Use a local HTML page with one media element and a local media file. Do not paste private URLs.

1. Desktop: matching page via Kotlin; one non-matching site URL still via CLI (or record that yt-dlp is missing).
2. Android: matching page via Kotlin; one unresolved site URL via Chaquopy, or the existing APK blocker plus the JVM-equivalent test.
3. iOS: matching page in the sandbox; unresolved HTML shows extractor-not-implemented.
4. Web: extension present saves the media; extension absent refuses; page does not fetch the origin.
5. Shared tests: `./gradlew :shared:core:jvmTest` and the host tests that exist.
6. Update README status to D3. State that the media toolkit is recorded and not built, and that YouTube is still later.
7. Document iOS suspension and browser tab-close honestly, including that an in-flight extension download can outlive the tab.

## Acceptance criteria

- [ ] Four-host table with pass/fail/blocked and versions.
- [ ] README matches D3.
- [ ] No private URL, cookie, or media file was added to the repository.
- [ ] No FFmpeg, MediaMuxer, or AVFoundation postprocessing landed in this phase.

## Evidence / notes

Not started.
