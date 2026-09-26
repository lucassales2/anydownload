---
id: T-092
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-092 — Spotify library

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

Liked songs, the user's playlists, saved albums, and followed artists resolve after the user logs in to Spotify on the device. This is not an AnyDownload account. Covers F-34.

## Dependencies

- [T-086](T-086-Spotify-download.md).

## Context the next session needs

spotDL queries: `saved`, `all-user-playlists`, `all-user-saved-albums`, `all-user-followed-artists`. They need `--user-auth`. Public queries from T-084 must keep working with no login. The token stays in the platform secret store. Tests use a fake token response, never a real login.

## Work

- Settings offers a Spotify login that stores the OAuth token on the device. Logout deletes it.
- Those four queries expand through T-084's record shape and then the T-086 download path.
- Without a token, the query fails typed and explains that Spotify login is required. It does not fall back to an empty download.

## Acceptance criteria

- [x] The four library queries fail typed when no token is stored.
- [x] With a fixture token, each query returns song records and can be queued.
- [x] Logout removes the token. Logs and the vault contain no token.
- [x] A public track URL still downloads with no login.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (the four `--user-auth` query words and the downloader's user-library flow); no Python is vendored or copied.

What landed:

- `SpotifyTokenStore` + `InMemorySpotifyTokenStore` + `DesktopSpotifyTokenStore` (owner-only file under the app state directory) + `SpotifyStoredTokenSource`.
- `SpotifyAuthService`: `isLoggedIn`, `login(token)`, `logout`, the PKCE `authorizeUrl`, and the `exchangeCode` token exchange (fixture-tested; PKCE sends no client secret). The Settings screen has a Spotify section with status, a token field, Log in, and Log out.
- `SpotifyLibraryClient` + `SpotifyLibraryQueries`: `saved`, `all-user-playlists`, `all-user-saved-albums`, and `all-user-followed-artists`; `/me/tracks`, `/me/playlists`, `/me/albums`, and `/me/following` pagination, with each playlist, album, and artist expanded through the shared metadata client using the user token. Every query fails typed with `NotAuthorized` when no token is stored.
- The service's `preview` handles the library words and keeps the public path unchanged; a public track preview never touches the library client. `songRecordEntry` was extracted from `SpotifyWebApiClient` so the library and metadata paths return the same record shape.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 465 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 428, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 417, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 85, 0 failures; `:apps:desktop:test` — 128, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Acceptance cases: `SpotifyLibraryClientTest.theFourLibraryQueriesFailTypedWithoutAToken`; `savedReturnsRecordsWithAFixtureToken`, `userPlaylistsSavedAlbumsAndFollowedArtistsExpand`, `aFixtureTokenCanPreviewAndQueueTheLibrary`; `loginStoresTheTokenAndLogoutRemovesIt` and `SpotifyAuthUiTest.loginStoresTheTokenAndLogoutClearsIt`; `aPublicTrackStillWorksWithoutALogin`; `thePkceExchangeStoresTheToken` and `theAuthorizeUrlCarriesPkceAndScopes`.
- Tokens ride only in the explicit `authorization` field; no fixture, URL, log, record, or vault entry carries one. The Settings login stores a token the user supplies from their own Spotify login; the full browser PKCE redirect capture is a host follow-up. The desktop FFmpeg tests have a pre-existing intermittent flake under repeated full-suite runs (each test passes in isolation and the suite passed on the recorded run).
