---
id: ADR-011
type: adr
status: accepted
created: 2026-09-25
tags: [architecture, decisions, engine, twitter]
---

# ADR-011 — X / Twitter status video after spotDL parity

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [ADR-010](ADR-010-Spotdl-parity-phase.md) · [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md) · [ADR-004](ADR-004-Local-kotlin-engine.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state. Does not supersede [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md) or [ADR-010](ADR-010-Spotdl-parity-phase.md). This record schedules the X/Twitter site both named as the next port after YouTube and left outside D6.

## Context

Phase D6 is done ([T-093](../06-tasks/T-093-Phase-6-verification.md), 2026-09-25). [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md) named X/Twitter as the next site after YouTube. [ADR-010](ADR-010-Spotdl-parity-phase.md) put spotDL parity in front of it. Both phases are now verified, so the next named site is X/Twitter.

Baseline inspected 2026-09-25: upstream `yt_dlp/extractor/twitter.py` at tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`) is 1,785 lines and names six classes. `TwitterIE._VALID_URL` matches `x.com`, `twitter.com`, `mobile.x.com`, `mobile.twitter.com`, `www.` and `m.` variants, and the `i/web` form, with `/user/status/<id>` and `/statuses/<id>` paths. Upstream's default Twitter API selection is GraphQL with a hard-coded bearer token plus a guest token; the `syndication` selection calls the public `cdn.syndication.twimg.com/tweet-result` endpoint and needs neither cookies nor a bearer. The status JSON carries metadata and media grouped under `mediaDetails`; video entries expose `video_info.variants` with HLS and MP4 URLs. The existing port already has an extractor registry, an `InfoDict`/format model, a fixture harness, native HLS/DASH handling, and four-host routing; it has no notion of several selectable videos inside one source.

The owner's D7 direction: a public status video on all four hosts, public guest lookup only, no cookies, no vendored `twitter.py`, no `yt-dlp` process for this URL, no guest token or signed media URL committed, and a live status only as an opt-in desktop test.

## Proposed decision

- **Phase D7** follows D6. Tasks are [T-094](../06-tasks/T-094-Twitter-status-extractor.md) through [T-100](../06-tasks/T-100-Phase-7-verification.md), sequenced in the [phase note](../00-project/Phase-7-X-Twitter.md). Nothing starts until T-093 is Done. [T-096](../06-tasks/T-096-Gate-selected-media-download.md) is the gate: a fixture status downloads one file per selected video on desktop. T-097, T-098, and T-099 depend on T-096; T-100 depends on all of them.
- **One translated class.** Only `TwitterIE`'s single-status media path is ported. `TwitterCardIE`, `TwitterAmplifyIE`, `TwitterBroadcastIE`, `TwitterSpacesIE`, and `TwitterShortenerIE` stay unported, as do photos, threads and quoted posts, Spaces, broadcasts, profiles, and cards.
- **Public guest lookup only.** The extractor calls the syndication endpoint with the request's status id. It sends no cookie and copies no upstream `_AUTH`/`_LEGACY_AUTH` bearer. The per-request token that endpoint expects is derived from the id at runtime and never stored. A protected or login-only status fails typed; no login flow is offered.
- **Videos are grouped by a stable media id.** `InfoDict` gains a port-only media list (media id, title, duration, thumbnails, formats). Photos are not media. The preview renders one selectable row per video and preselects the first. The existing Edit panel continues to choose quality; one choice applies to every selected video.
- **Selection is request state.** `DownloadRequest.selectedMediaIds` carries the chosen stable ids. The engine re-extracts the status at download time, resolves each selected video's format again, and writes one file per selected video inside the download root. The job's source URL stays the status URL. An empty selection with videos present downloads nothing and fails typed.
- **Four hosts.** T-096 wires and proves the desktop path with a redacted fixture plus an opt-in live public status. T-097 and T-098 add the Android and iOS registry entries and prove the shared path with JVM-equivalent and simulator fixtures; neither runs a live X/Twitter call. T-099 adds the web registry entry; the extension carries the lookup and the media GET and the page performs no X/Twitter fetch. No host shells out to `yt-dlp` or Python for a matched status URL.
- **Evidence.** The port manifest gains `TwitterIE` as partial with the upstream path, scope, and pin, and the generated coverage block is regenerated. [T-100](../06-tasks/T-100-Phase-7-verification.md) records the four-host table, updates the equivalence note, Roadmap, Home, and the decision log, and accepts this record.
- **Out of D7:** photos, threads, quotes, cards, Spaces, broadcasts, profiles, `t.co`, cookies, login, comments, subtitles, thumbnails-as-artifacts, and any other site.

## Alternatives

- **GraphQL or legacy API with the copied bearer token.** Rejected: it needs a hard-coded application token and, for the logged-out path, a guest token; both are exactly what the owner excluded from the repository, and the query shape is brittle.
- **Port `twitter.py` whole, including Spaces and broadcasts.** Rejected: each is a different media model with its own live protocol; D7 proves the shared preview-selection-download path on the simplest one first.
- **Shell out to `yt-dlp` on desktop for this URL.** Rejected: matched URLs already route to Kotlin on every host, iOS and web have no process, and the point of the port is to remove the adapter for this site.
- **Scrape the status HTML or the oEmbed endpoint.** Rejected: the HTML is renderer-dependent, and the syndication JSON is the public shape upstream itself falls back to.
- **Treat the status as a playlist and reuse the D4 engine unchanged.** Rejected for now: playlists and entries are a later task ([T-012](../06-tasks/T-012-Playlists-and-batches.md)); D7 needs only an explicit selection list on one request.
- **Photos as extra rows or downloads.** Rejected: a photo is not a video and would invent a media path the phase did not verify.

## Consequences

D7 ends with `TwitterIE` as the second translated site extractor (after YouTube), the first multi-video source, and a selection field that later multi-media extractors can reuse. The catalog coverage count moves by one partial. The preview grows selectable rows, and the engine grows a one-job-many-files path. Fixtures are synthesized from the public schema because a recorded status or CDN media URL must not be committed. The syndication response is not a versioned contract; if it changes shape, only the extractor and its fixtures change, and the failure is typed, not a silent empty preview.

Risks: the public syndication endpoint can change or rate-limit without notice (recorded in the [risk register](../04-delivery/Risk-register.md)); a live status may age, be deleted, or turn protected between runs, which is why the live test is opt-in and states its result; some video variants are HLS, so the D4 manifest path must keep working for a fixture with a manifest variant; media URLs expire, so nothing from preview may be reused at download time.

## Validation / approval

Accepted 2026-09-25: [T-100](../06-tasks/T-100-Phase-7-verification.md) recorded the four-host table. Desktop passes the redacted fixture gate (preview, two selected videos, two files, empty and stale selections typed, no CLI process) and the opt-in live status test exists and is skipped by default; Android passes the JVM-equivalent fixture path and `assembleDebug` (no emulator/AVD exists here); iOS passes the simulator fixture through the sandbox file store with no live X/Twitter call; web passes the node and wasm fixtures with the extension carrying the guest lookup and media GET while the page makes zero X/Twitter requests. The manifest lists `TwitterIE` partial and the equivalence coverage block is regenerated (4 partial, 1,747 not started). No cookie, guest token, signed media URL, media file, or vendored `twitter.py` was added. No blocker was written. [T-094](../06-tasks/T-094-Twitter-status-extractor.md) through [T-099](../06-tasks/T-099-Web-extension-x-requests.md) are Done, and [T-096](../06-tasks/T-096-Gate-selected-media-download.md) was marked Done only after T-094 and T-095.
