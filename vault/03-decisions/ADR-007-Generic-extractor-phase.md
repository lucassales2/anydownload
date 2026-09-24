---
id: ADR-007
type: adr
status: accepted
created: 2026-09-23
tags: [architecture, decisions, engine, platforms]
---

# ADR-007 — First extractor is a generic subset; media toolkit recorded, not built

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state: an in-process Kotlin port of yt-dlp on every target. Does not supersede [ADR-005](ADR-005-Desktop-metube-phase.md) or [ADR-006](ADR-006-Local-http-engine-phase.md). Direct files stay on the shared HTTP engine. Desktop CLI and Android Chaquopy stay for every URL this phase's extractor does not resolve.

## Context

Phase D2 is done. Each host can save one direct HTTP(S) file. HTML still fails with “extractor not implemented” on iOS and web, and still goes to the desktop CLI or Android Chaquopy. The decision log said the next records should capture the per-target media toolkit and the first extractor translation.

Owner answers, 2026-09-23:

- This loop exits when **one non-YouTube extractor** works on iOS, Android, web, and desktop. It turns a simple HTML page into one direct media URL. The existing HTTP engine downloads that file.
- The extractor is a **small subset of yt-dlp's generic extractor**, translated from Unlicense source with notices. Not the rest of generic. Not a named site. Not YouTube.
- The **media toolkit is recorded now and not implemented**. This loop does not merge, transmux, or extract audio.

## Decision

- **Phase D3** is the next implementation work. Tasks are [T-045](../06-tasks/T-045-Generic-extractor-subset.md) through [T-051](../06-tasks/T-051-Phase-3-verification.md), sequenced in the [phase note](../00-project/Phase-3-Generic-Extractor.md).
- Shared Kotlin owns `GenericExtractor` (name may match the code). It reads an HTML document and returns at most one HTTP(S) media URL taken from `<video src>`, `<audio src>`, or `<source src>` inside those elements. Relative URLs resolve against the page URL. Every candidate passes the existing `UrlPolicy`. Zero candidates or more than one candidate is a typed failure, not a guess.
- Out of this extractor: playlists, embeds, iframes, YouTube, JSON-LD, meta refresh, HLS, DASH, and the rest of yt-dlp's `generic.py`. Do not vendor that file. Translate only this subset. Record the upstream revision and the Unlicense notice in the repo.
- `HttpDownloadEngine` keeps direct-file behavior. When classification is HTML, it runs this extractor and then streams the chosen URL with the existing transfer. HTML this subset cannot resolve still fails with the typed extractor error.
- Desktop: pages this extractor resolves go through shared Kotlin. Every other site URL stays on the installed CLI.
- Android: the same split. Unresolved site URLs stay on the Chaquopy port. No new Python.
- iOS: the same shared extractor. Unresolved HTML stays “extractor not implemented”. Foreground-only remains acceptable.
- Web: the page still does not fetch origins. The extension fetches the HTML and the media file. The Kotlin extractor runs on the HTML bytes. Without the extension, Add does not pretend a download started.
- **Media toolkit, recorded and not built in D3:**
  - Desktop: FFmpeg and ffprobe already on `PATH`. Do not vendor them.
  - Android: platform `MediaMuxer` / `MediaExtractor` when postprocessing starts. No bundled FFmpeg.
  - iOS: `AVFoundation` when postprocessing starts. No subprocess.
  - Web: no merge and no audio extract. Documented gap until a later phase.
- YouTube, yt-dlp-ejs, Spotify matching, and free-form yt-dlp JSON stay out of this phase.

## Alternatives

- Prove the extractor on the JVM only. Rejected: owner wants the same page URL on all four hosts in this loop.
- YouTube plus yt-dlp-ejs in this loop. Rejected: that is its own phase after a non-YouTube extractor exists.
- Implement merge or audio extract now. Rejected: the toolkit is chosen, not built.
- Port the whole generic extractor. Rejected: the subset above is the slice; the rest of generic is a later translation.

## Consequences

D3 proves one HTML-to-file path on every family without claiming site coverage. Desktop and Android keep upstream yt-dlp for everything else. A later phase adds YouTube by executing yt-dlp-ejs, then postprocessing with the toolkit recorded here. Chaquopy is not retired in D3.

Risks: generic pages in the wild rarely match this subset, and the UI must not claim they will. A page with two media elements fails closed. Extension host permissions stay as wide as D2 until a later tightening.

## Validation / approval

Owner, 2026-09-23, from the planning answers that chose one extractor on all four hosts, the generic subset, and record-only toolkit. D3 is complete when [T-051](../06-tasks/T-051-Phase-3-verification.md) records that HTML fixture on each host, or a written blocker.
