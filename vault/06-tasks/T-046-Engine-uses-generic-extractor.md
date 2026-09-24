---
id: T-046
type: task
priority: P0
milestone: D3
tags: [task, engine, kmp]
---

# T-046 — Engine uses the generic extractor

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

`HttpDownloadEngine` downloads the media URL from a matching HTML page. Direct files are unchanged. HTML this subset cannot resolve still fails typed.

## Dependencies

- [T-045](T-045-Generic-extractor-subset.md).

## Work

- On `NEEDS_EXTRACTOR`, read a bounded HTML body (cap the bytes; do not hold a huge page), run `GenericExtractor`, then stream the chosen URL with the existing transfer and temp-file publish.
- The media response must classify as a direct file. If it is HTML again, fail typed. Do not recurse.
- Apply `UrlPolicy` to the media request and its redirects, same as a direct file.
- Cancel during the HTML read or the media stream discards the temp file.
- Progress for the media body stays as D2 defined it. Do not invent speed or ETA.
- `WebExtensionEngine` is wired in [T-050](T-050-Web-extension-generic.md), not here.
- Custom yt-dlp JSON stays ignored.

## Acceptance criteria

- [ ] JVM tests: matching HTML fixture completes as a file; direct file still completes; zero-match and two-match HTML fail typed; media URL that redirects to a blocked address fails without saving; cancel mid-media leaves no completed file.
- [ ] Tests use an in-memory dispatcher or local mock. No live host.
- [ ] `./gradlew :shared:core:jvmTest` passes.

## Evidence / notes

Not started.
