---
id: T-089
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-089 — Fallback audio providers

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

When YouTube Music and YouTube miss, the matcher tries SoundCloud, Bandcamp, Piped, and slider.kz, in that order, and only if the user includes them. Covers F-31.

## Dependencies

- [T-086](T-086-Spotify-download.md).

## Context the next session needs

The default provider list stays YouTube Music only, matching spotDL's config. Extra providers are opt-in. A provider the current host cannot fetch fails that attempt and the search continues. Do not add a Spotify audio provider.

## Work

- Add the four providers as matcher backends that return candidate URLs. Download still goes through `DownloadEngine`.
- If the engine has no extractor for that URL, the attempt fails typed and the next provider runs.
- Tests use fixture results for each provider, including a host that cannot fetch one of them.

## Acceptance criteria

- [x] Default search does not call SoundCloud, Bandcamp, Piped, or slider.kz.
- [x] With those providers enabled, a YouTube miss can still return one of their URLs.
- [x] An unsupported URL fails that attempt and does not abort the remaining providers.
- [x] No Spotify CDN or preview URL is ever returned.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`providers/audio/soundcloud.py`, `bandcamp.py`, `piped.py`, `sliderkz.py`); no Python is vendored or copied.

What landed:

- `AudioSource` gains soundcloud, bandcamp, piped, and slider.kz; `AudioProviders` names them and builds only the enabled fallbacks in spotDL's order.
- `SoundcloudAudioProvider` (public search page to web client id to the public v2 track search; `/preview/` URLs are dropped), `BandcampAudioProvider` (fuzzy search, then mobile tralbum details for the top candidates), `PipedAudioProvider` (public instance search; every result is normalized to a `youtube.com/watch` URL the existing extractor owns), and `SliderKzAudioProvider` (direct audio URLs; the live service is shut down, so it normally misses).
- `AudioMatcher.withFallbacks(...)` keeps YouTube Music then YouTube first and appends the enabled fallbacks; a `canDownload` predicate rejects a URL the host cannot fetch, and the remaining candidates and providers still run. `AudioMatcher.default` stays YouTube Music + YouTube and never touches a fallback.
- `AppSettings.spotifyFallbackProviders` (empty by default) and the desktop wiring pass the enabled list; the desktop `canDownload` accepts extractor-matched URLs and direct media extensions.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 443 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 406, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 395, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 127, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Acceptance cases: `AudioMatcherTest.theDefaultMatcherNeverCallsTheFallbackProviders`, `anEnabledFallbackRunsAfterYouTubeMisses`, `anUnsupportedUrlFailsThatAttemptOnly`, `aFallbackTheHostCannotFetchDoesNotStopTheNextOne`; `AudioFallbackProvidersTest` per-provider parsing over public fixture shapes, the opt-in factory order, and `noFallbackProviderEverReturnsASpotifyUrl`.
- Note: `AppSettings.spotifyFallbackProviders` is the opt-in seam (empty by default); a Settings toggle is not part of this task, so the fallbacks stay off unless the stored list names them.
