---
id: T-060
type: task
priority: P0
milestone: D4
tags: [task, engine, youtube]
---

# T-060 — YouTube extractor, stage 1: JS-less `visionos` client

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)

## Outcome

`YoutubeIE` in shared Kotlin turns a single-video URL into an `InfoDict` with metadata and the formats the `visionos` innertube client returns, without any JavaScript. No download in this task.

## Dependencies

- [T-057](T-057-Extractor-core.md), [T-059](T-059-Extractor-test-harness.md).

## Context the next session needs

Upstream at the pin: `extractor/youtube/_base.py` (1,350 lines; `INNERTUBE_CLIENTS`, `visionos` = `clientName VISIONOS`, `clientVersion 1.02`, `INNERTUBE_CONTEXT_CLIENT_NAME 101`, `REQUIRE_JS_PLAYER: False`), `extractor/youtube/_video.py` (4,592 lines; `_DEFAULT_JSLESS_CLIENTS = ('visionos',)`). Translate only the video path for one client. Package `com.anydownlod.core.extract.youtube`.

A player response contains signed `googlevideo` URLs. Fixtures must be synthesized with `https://rr1---sn-example.googlevideo.example/videoplayback?...` style hosts and fake parameters. Never record a real player response into the repo.

## Work

- `_VALID_URL` subset: `youtube.com/watch?v=`, `youtu.be/<id>`, `youtube.com/shorts/<id>`, `youtube.com/embed/<id>`, `youtube.com/live/<id>`, `youtube-nocookie.com/embed/<id>`, `music.youtube.com/watch?v=`. Everything else (playlists, channels, `@handle`, search) is `UnsupportedUrl` in D4. Video id is 11 characters from the upstream class.
- Innertube `player` request: `POST https://www.youtube.com/youtubei/v1/player?prettyPrint=false` with the `visionos` context from `_base.py`, `videoId`, `contentCheckOk`, `racyCheckOk`, the client headers, and the `playbackContext` fields upstream sends for that client. No API key beyond what upstream sends at the pin. No visitor data persistence.
- Parse `playabilityStatus` to `ExtractionError`: `LOGIN_REQUIRED`, `AGE_VERIFICATION`/`age_limit`, `UNPLAYABLE`, `ERROR` with reasons for private/removed/geo, `LIVE_STREAM_OFFLINE`. Live videos (`isLive`, `isLiveContent` with `hlsManifestUrl` only) fail typed as not supported in D4.
- `streamingData.formats` and `adaptiveFormats` → `MediaFormat`: `itag` → `formatId`, `mimeType` → `ext`/`vcodec`/`acodec` via `parseCodecs`, `bitrate`/`averageBitrate`, `width`/`height`/`fps`, `contentLength`, `audioQuality`, `audioSampleRate`, `quality`/`qualityLabel`, `url`. Formats with `signatureCipher` and no `url`, and any `n` parameter needing a transform, are **dropped** in this stage as upstream does without a runtime; count them so the engine can report "N more formats need the JavaScript runtime". Set `downloaderOptions.httpChunkSize` as upstream does for this client.
- `videoDetails` and `microformat.playerMicroformatRenderer` → title, channel, channelId, duration, viewCount, description, uploadDate, thumbnails (sorted by size), `isLive`, `ageLimit`.
- Harness cases: translate the first upstream `_TESTS` entry (`YE7VzlLtp-4`, Big Buck Bunny) as the live case; fixture cases from synthesized player JSON for a normal video, a login-required video, an age-gated video, a private video, a removed video, a live stream, and a response with only ciphered formats.
- Manifest: `youtube/_base.py` partial, `youtube/_video.py` partial (single video, visionos).

## Acceptance criteria

- [ ] Fixture cases pass for every URL form and every typed failure above.
- [ ] Live case passes with `-PliveExtractorTests=true` on the public Big Buck Bunny URL; evidence records the date and the format count, never the URLs returned.
- [ ] No fixture in the repo contains a real `googlevideo.com` host, visitor id, or token (grep in the test).
- [ ] Notice headers and manifest name both upstream files at the pin.

## Evidence / notes

Not started.
