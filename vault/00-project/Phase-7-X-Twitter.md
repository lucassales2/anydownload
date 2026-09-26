---
type: phase
status: done
milestone: D7
tags: [project, engine, twitter, delivery]
---

# Phase 7 — X / Twitter status video

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md) · [Phase 6](Phase-6-SpotDL-Parity.md)

**Phase D7 is done (2026-09-25).** [T-100](../06-tasks/T-100-Phase-7-verification.md) recorded the four-host result: desktop passes the redacted fixture gate and the opt-in live status test is skipped by default; Android passes the JVM-equivalent fixture path and `assembleDebug`; iOS passes the simulator fixture with no live X/Twitter call; web passes the node and wasm fixtures with the extension carrying the lookup and the media GET while the page fetches nothing. ADR-004 remains the end state. The upstream pin stays `2026.08.19`. This phase translated the `TwitterIE` single-status media path: a public X/Twitter status becomes a preview where the user selects its videos, and each selected video downloads as its own file on all four hosts. Public guest lookup only, no cookies.

Owner direction, 2026-09-25: X/Twitter is the next named site. [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md) named it next after D4; [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md) placed it after D6. This phase is that site, narrowed to status videos.

Baseline inspected 2026-09-25: upstream `yt_dlp/extractor/twitter.py` at tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`, is 1,785 lines and names six classes: `TwitterIE`, `TwitterCardIE`, `TwitterAmplifyIE`, `TwitterBroadcastIE`, `TwitterSpacesIE`, and `TwitterShortenerIE`. D7 translates only `TwitterIE`'s video path for `/user/status/<id>`. Upstream's default Twitter API selection is GraphQL with a hard-coded bearer and a guest token; the port uses the public syndication lookup (`cdn.syndication.twimg.com/tweet-result`) instead, which is cookie-free and needs no bearer. The syndication JSON carries the status metadata and `mediaDetails` with `video_info.variants`. Upstream's `_AUTH`/`_LEGACY_AUTH` values and any guest token must never enter the repo.

## Done looks like

Paste a public `x.com`, `twitter.com`, `mobile.x.com`, or `mobile.twitter.com` `/user/status/<id>` URL. Preview shows the status metadata and one row per video, grouped by a stable media id; the first row is preselected and the user can select any subset. Photos never become rows. Download writes exactly one file per selected video inside the download root on desktop, Android, iOS, and web. The job's source URL is the status URL; media URLs are re-resolved at download time, never held over from preview. The existing Edit panel still chooses quality. Photo-only, protected, and deleted posts fail typed and download nothing. No cookie is sent, no `yt-dlp` process runs for this URL, and no guest token, media URL, or media file is committed.

## Rules for every D7 task

- No backend, login, MeTube HTTP/Socket.IO client, or AnyDownload account.
- Public guest lookup only. Do not send cookies and do not copy upstream's hard-coded bearer values or the GraphQL query. A protected or login-only post fails typed.
- Do not vendor `twitter.py` and do not copy it wholesale. A translation is file-by-file with the Unlicense notice and the pin, as every other ported module.
- Common code has no `ProcessBuilder`. Desktop does not run the installed `yt-dlp` for a matched X/Twitter status; no CLI process for this URL on any host. Unmatched URLs keep their existing routes.
- Never write guest tokens, cookies, signed media URLs, or private URLs into fixtures, logs, history, the vault, or the repo. Tests use redacted public JSON; media addresses point at `*.example` hosts.
- The upstream pin stays `2026.08.19`. Do not move it mid-phase.
- Live tests are opt-in on desktop only and never run in default CI. Android, iOS, and web run redacted fixtures only.
- One file per selected video. The engine re-extracts at download time. An empty selection downloads nothing.
- Photos, threads and quoted posts, Spaces, broadcasts, profiles, `t.co`, and cards are out. A card that is only a YouTube link is not downloaded here.
- Do not add cookies, login, comments, or PO tokens. A source that needs them fails typed.
- Do not commit a media file. Do not commit unless a task explicitly tells you to.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout

```text
shared/core
  extract/twitter/    TwitterIE, the status/media model, the guest lookup
  InfoDict + preview  port-only media list, grouped by stable media id
shared/ui             video rows and selection on the existing preview; Edit panel unchanged
apps/desktop          registry entry; fixture gate and opt-in live test only
apps/android          registry entry; JVM-equivalent fixture path
apps/ios              registry entry; simulator fixture only
apps/web              registry entry; the extension carries every request
```

Package: `com.anydownlod.core.extract.twitter`. Downloads stay on the existing engines.

## Task order

Do them in this order. Dependencies are the links in each task note. Do not start until [T-093](../06-tasks/T-093-Phase-6-verification.md) is Done.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-094](../06-tasks/T-094-Twitter-status-extractor.md) | `TwitterIE` for the four host forms; guest lookup from redacted fixtures; videos grouped by media id; typed photo-only/protected/deleted; no download |
| 2 | [T-095](../06-tasks/T-095-Preview-status-videos.md) | Preview lists the videos; the first is preselected; photos are not rows; Edit still chooses quality |
| 3 | [T-096](../06-tasks/T-096-Gate-selected-media-download.md) | **Gate:** `DownloadRequest` carries selected media ids; re-extract; one file per selected video; empty selection downloads nothing |
| 4 | [T-097](../06-tasks/T-097-Android-routes-x-status.md) | Android registry, JVM-equivalent fixture test, `assembleDebug` if no emulator |
| 5 | [T-098](../06-tasks/T-098-Ios-x-status-download.md) | iOS registry, simulator fixture, no live X/Twitter |
| 6 | [T-099](../06-tasks/T-099-Web-extension-x-requests.md) | Web: the extension carries the lookup and the media GET; the page never fetches X or Twitter |
| 7 | [T-100](../06-tasks/T-100-Phase-7-verification.md) | Four-host table, port manifest, equivalence row, Roadmap, Home, decision log; accept ADR-011 |

## What this phase covers

| X/Twitter input or behavior | AnyDownload | Task |
| --- | --- | --- |
| Public status with one video | One preview row, preselected; one file named from the status title | T-094–T-096 |
| Public status with several videos | One row per video grouped by stable media id; the user selects a subset; one file per selected video | T-094–T-096 |
| Video variants (HLS, MP4) | HLS through the D4 manifest path; direct MP4 variants with `tbr` and dimensions; sorted by the existing selector | T-094, T-096 |
| Quality and container | The existing Edit panel over the listed videos; one choice applies to each selected video | T-095 |
| Photo-only status | Typed failure, no rows, no download | T-094 |
| Protected or login-only status | Typed login-required failure; no cookie is offered | T-094 |
| Deleted or not-found status | Typed unavailable failure | T-094 |
| Quoted posts, threads, cards, Spaces, broadcasts, profiles, `t.co` | Out of D7; the extractor ignores them and no row or download is invented | — |

## Explicitly later or out of scope

- Photos, photo galleries, and the `/photo/<n>` URL suffix.
- Threads, quoted tweets, and cards, including a card that is only a YouTube link.
- Spaces, broadcasts, Amplify, profiles, the `t.co` shortener, and every class other than `TwitterIE`.
- Cookies, login, `auth_token`, GraphQL, API v1.1, and any hard-coded bearer token.
- Comments, views as a download input, subtitles, and thumbnails as artifacts.
- Any other site. The pin moves only at a phase boundary and never mid-D7.

## Verified (2026-09-25)

[T-100](../06-tasks/T-100-Phase-7-verification.md) recorded the four-host table:

| Host | Preview + selection | Download | No CLI / no cookies |
| --- | --- | --- | --- |
| Desktop | **Pass** — fixture status lists its two videos; the first is preselected. | **Pass** — two selected videos land as two files; the unselected URL is never fetched; the opt-in live test exists and was skipped (no URL configured). | **Pass** — the route is KOTLIN and zero `yt-dlp` processes start. |
| Android | **Pass (JVM-equivalent)** — the two stable ids preview through the shared engine; no emulator/AVD exists here. | **Pass (JVM-equivalent)** — two files with the fixture bytes; the Chaquopy port receives nothing. | **Pass** — the registry match routes to KOTLIN before any probe. |
| iOS | **Pass (simulator)** — the two stable ids preview through the shared engine and the sandbox `IosFileStore`. | **Pass (simulator)** — two files with the fixture bytes; no live X/Twitter. Foreground-only. | **Pass** — no Python or CLI on iOS. |
| Web | **Pass** — the page lists the videos; `WebAppGraph` registers `TwitterIE` over the extension request port. | **Pass (wasm + node fakes)** — one file per selected video through `chrome.downloads`; a browser end-to-end UI run is the same gap as D6. | **Pass** — the page makes zero X/Twitter/syndication fetches; the extension drops the MV3-forbidden `user-agent`, and no cookie or authorization rides the lookup. |

Honest limits stay recorded: only the public single-status video path; photos, threads, cards, Spaces, broadcasts, profiles, `t.co`, cookies, login, and the GraphQL/legacy API are out; the live check is desktop-only and opt-in; web has no browser end-to-end X/Twitter run. The manifest lists `TwitterIE` partial and the coverage block regenerated to 4 partial and 1,747 not started. [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) is accepted.

## How to start a session

Paste the prompt in [Phase 7 loop prompt](Phase-7-Loop-prompt.md) as the first message, after D6 is verified.

1. Read this note and [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md).
2. On [Kanban](../Kanban.md), take the first D7 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up another site, photos, threads, or a cookie flow.
