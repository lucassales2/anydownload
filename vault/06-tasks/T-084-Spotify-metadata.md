---
id: T-084
type: task
priority: P0
milestone: D6
tags: [task, engine, spotify]
---

# T-084 — Spotify metadata

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md) · [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md)

## Outcome

A public Spotify track, album, playlist, or artist URL, and a text search, become song records. No audio is downloaded. The app has no account.

## Dependencies

- [T-083](T-083-Phase-5-verification.md) — D5 verified. D6 does not start earlier.

## Context the next session needs

Package: `com.anydownlod.core.music`. Baseline is spotDL v4.5.2 (`cd4a4203`). The default metadata client is the unauthenticated one. A client id and secret stored on the device select the official Web API. Do not implement OAuth here; that is T-092. Do not call YouTube.

## Work

- Resolve a track to title, artists, album, album artist, duration, ISRC, artwork URL, track number, disc number, year, and explicit flag.
- Expand an album, playlist, or artist URL into those records. A text query (`artist - title`, and the `album:`, `playlist:`, `artist:` prefixes) resolves to the same record shape.
- Unavailable entries stay in the list with a typed reason. One failure does not drop the rest.
- Unit-test against saved public JSON fixtures. No live call is required for this task. Secrets never appear in fixtures.

## Acceptance criteria

- [x] Track, album, playlist, artist, and text search return song records from fixtures.
- [x] The unauthenticated client is the default. Official Web API is selected only when a client id and secret are stored.
- [x] No download, no matcher, and no OAuth in this task.
- [x] Fixture files contain no client secret and no token.

## Evidence / notes

Done 2026-09-25. Package `com.anydownlod.core.music`. No live call: every case uses the saved public JSON fixtures under `shared/core/src/commonTest/resources/fixtures/spotify/` or synthesized public Web API documents. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`utils/spotify.py`, `utils/search.py`, `types/song.py`, `types/album.py`, `types/playlist.py`, `types/artist.py`); no Python is vendored or copied.

What landed:

- `SongRecord` + `SongListResult`/`SongListEntry`: title, artists, album, album artist, album id/type, duration, ISRC, artwork URL, track/disc numbers and counts, year/release date, genres, publisher, explicit, Spotify URL, and list context (`listName`, `listUrl`, `listPosition`, `listLength`).
- `SpotifyQueryParser`: `open.spotify.com`/`play.spotify.com` track, album, playlist, and artist URLs (locale and `si` params stripped), `spotify:` URIs, `album:`/`playlist:`/`artist:` searches, and plain text.
- `SpotifyWebApiClient`: track, album (`/albums/{id}/tracks` pagination), playlist (item `track`/`item`, local and episode entries), artist (album expansion with name dedupe), and search. Per-entry failures keep their typed place: `LOCAL_TRACK`, `NOT_A_TRACK`, `NO_DURATION`, `MISSING_DATA`, `FETCH_FAILED`.
- `SpotifyTokenSource`: `SpotifyEmbedTokenSource` is the default and reads the anonymous session from the public embed page's `__NEXT_DATA__`; `SpotifyClientCredentialsTokenSource` is the app-only official path. `SpotifyMetadataClients.default` picks the official Web API only when both stored fields are present. A 401 refreshes the session once and retries.
- `HttpRequest.authorization` (plus the web-extension request allowlist) is the only way a credential leaves the device; extractor-declared header maps still cannot carry `authorization`, and values never enter diagnostics. The value is never logged or persisted.
- Fixture hygiene test: no saved fixture contains `access_token`, `Bearer`, `client_secret`, or a Spotify preview URL; the only `accessToken` value is the repository's `REDACTED` redaction placeholder.

User OAuth (T-092) is not implemented. The official client uses the client-credentials grant a stored client id and secret require; there is no user login, no AnyDownload account, and no download.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 353 tests, 0 failures (38 new music tests).
- `./gradlew :shared:core:iosSimulatorArm64Test` — 327 tests, 0 failures (30 new common tests).
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — 316 tests, 0 failures (30 new common tests).
- Full T-083 command list (`:shared:core:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :tools:port-manifest:check :apps:web:wasmJsDistribution`) — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.test.mjs` — 23 tests, 0 failures (the `authorization` allowlist case was added).
- New tests: `SpotifyQueryParserTest` (9), `SpotifyMetadataClientTest` (10), `SpotifyTokenSourceTest` (8), `SpotifyTextScoreTest` (3), `SpotifyFixturesTest` (8, JVM saved fixtures).

Note: live anonymous Web API access is rate-limited from this network (public anonymous session returns `429 QUOTA_EXCEEDED`), so no live metadata call is recorded. T-086's desktop gate can use the fixture path or an opt-in live URL when the quota resets.
