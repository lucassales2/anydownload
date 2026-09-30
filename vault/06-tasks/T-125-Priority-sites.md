---
id: T-125
type: task
priority: P0
milestone: D16
tags: [task, engine, extractors]
---

# T-125 — Priority sites

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 16](../00-project/Phase-16-Priority-sites.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

Twitter’s remaining classes, and the 13 high-traffic files named in Phase 16, are in the registry. Estimate 37 engineer-days.

## Dependencies

- [T-019](T-019-Subscriptions.md) and [T-020](T-020-Sharing-and-UX.md).

## Acceptance criteria

- [x] Twitter cards, Amplify, broadcasts, Spaces, and the shortener are translated or marked Partial. No bearer or guest token is committed.
- [x] Bilibili, Vimeo, PeerTube, and BBC each have harness cases and a manifest row.
- [x] TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, and archive.org each have harness cases and a manifest row.
- [x] A matched URL on desktop and Android routes to Kotlin, not the CLI or Chaquopy.
- [x] `:tools:port-manifest:check` passes.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).

**File 1 of 14 — Twitter remainder (2026-09-30).** `TwitterRemainder.kt` adds the five remaining `twitter.py` classes at the pin:

- `TwitterCardIE` matches `/i/cards/tfw/v1/<id>` and `/i/videos/tweet/<id>` and delegates to the status extractor through the new registry `redirectUrl` seam (upstream `url_result(TwitterIE)`).
- `TwitterAmplifyIE` reads the `twitter:amplify:vmap` meta and parses the VMAP `videoVariant`/`MediaFile` subset (URL-decoded variants, bitrate, URL dimensions, player-meta dimensions on the first format) plus the `twitter:image:src` thumbnail; the HLS manifest is not expanded at extract time.
- `TwitterShortenerIE` resolves `t.co/<code>` and the `tco:<code>` keyword with a bounded redirect chain and the `curl` user agent, strips the safety-warning prefix, and re-enters the registry.
- `TwitterBroadcastIE` (`/i/broadcasts/…`, `/i/events/…`) and `TwitterSpacesIE` (`/i/spaces/<13-char id>`) match and fail typed because their upstream calls need the Twitter API bearer/guest token; no token is committed and no token appears in fixtures, logs, or this vault.

The registry re-dispatch is bounded at 5 hops (`ExtractorRegistry.MAX_REDIRECTS`) and `InfoDict.redirectUrl` is the port-only delegation field. `productionExtractorRegistry` includes all five, so desktop and Android route these URL forms to Kotlin rather than the CLI/Chaquopy. Manifest rows were added for the five classes, and `TwitterIE`'s row now points at the siblings. Tests: `TwitterRemainderTest` (10 cases, including one harness case for Amplify), `ExtractorRegistryTest` redirect re-dispatch and loop bound, and the updated `SupportedUrlValidatorTest`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — BUILD SUCCESSFUL.
- Cross-target compiles (wasm main/test, iOS simulator main/test, Android debug, web) — BUILD SUCCESSFUL.

Next: file 2, Bilibili.

**File 2 of 14 — Bilibili (2026-09-30).** `BilibiliIE.kt` adds `BiliBiliIE` and `BiliBiliPlayerIE`, translated from `yt_dlp/extractor/bilibili.py` at the pin:

- `BiliBiliIE` matches `/video/BV…`, `/video/av…`, and the festival `?bvid=` form; reads `window.__INITIAL_STATE__`, calls the pagelist API for anthologies, WBI-signs `/x/player/wbi/playurl` (key permutation, `wts`, MD5 `w_rid`), maps dash audio/video and the legacy `durl` fragment list, and turns a multi-part video without `?p=` into registry entries so the engine expands bounded child jobs. Part URLs extract one part each.
- `BiliBiliPlayerIE` matches `player.bilibili.com/player.html?aid=…` and canonicalizes to the `av` watch URL through the registry `redirectUrl` seam.
- Fixed on the way: the pagelist response’s `data` array is read with `array`, not `obj`, and `String.format`/`toSortedMap` were replaced with common multiplatform code so wasmJs and iOS compile.
- 22 manifest rows: `BiliBiliIE` and `BiliBiliPlayerIE` partial; the bangumi, cheese, intl, space, lists, search, category, audio, dynamic, and live classes planned, each with the reason.
- `BilibiliIETest` has 8 cases including the harness case from synthetic fixtures. Every host and media address is `*.example`; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 760, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 16 partial, 34 planned, 1,701 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 3, Vimeo.

**File 3 of 14 — Vimeo (2026-09-30).** `VimeoIE.kt` adds the public single-video subset of `VimeoIE` from `yt_dlp/extractor/vimeo.py` at the pin:

- URL forms: `vimeo.com/<id>`, `www.vimeo.com/<id>`, the 10-hex unlisted suffix, `vimeo.com/channels/<channel>/<id>`, and `player.vimeo.com/video/<id>`. Album, showcase, ondemand, event, review, watchlater, and other collection prefixes do not match.
- Watch pages read `vimeo.clip_page_config` / `vimeo.vod_title_page_config` (or the channel `data-config-url`) and download the player config JSON; player pages read the inline `playerConfig`. The config maps progressive files, HLS and DASH CDN manifests (recorded as `m3u8_native` / `http_dash_segments`; the engine parses them at download), the DASH `json=1` indirection, text tracks, thumbs, chapters, owner, live flags, description, date, view count, and license. Password-protected playback fails typed.
- Not translated: the private API path (`_extract_from_api`, unlisted-hash API, OAuth clients, original/source formats), logged-in-only formats, and the other ten `vimeo.py` classes (ondemand, channel, user, album, groups, review, watchlater, likes, pro, event), each with a planned manifest row and the reason. No Vimeo API token, OAuth secret, cookie, or signed media URL is stored.
- Fixed on the way: the URL regex had to drop a named group with an underscore, which Java/Kotlin regex rejects (`unlisted_hash` → anonymous group).
- Tests: `VimeoIETest` has 9 cases (URL matching, watch config, meta fallback, player inline config, channel `data-config-url`, harness case, password, missing config, upcoming event). Every host and address is `*.example`.
- 11 manifest rows: `VimeoIE` partial; the ten remaining classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 769, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures. (One `DesktopSpotifyDownloadGateTest` run failed on a transient postprocessing check and passed on the next rerun.)
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 17 partial, 44 planned, 1,690 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 4, PeerTube.

**File 4 of 14 — PeerTube (2026-09-30).** `PeerTubeIE.kt` adds the public video subset of `PeerTubeIE` from `yt_dlp/extractor/peertube.py` at the pin:

- URL forms: `/api/v1/videos/<id>`, `/videos/watch/<id>`, `/videos/embed/<id>`, `/w/<id>`, and `peertube:<host>:<id>`, for the 22-character and dashed UUID shapes. Account, channel, and playlist prefixes do not match this class.
- One `https://<host>/api/v1/videos/<id>` call maps: progressive `files`, `streamingPlaylists` HLS manifests (recorded as `m3u8_native`; the engine parses them at download) and their files, the `0p` audio-only label, captions, the 250-character long-description call, account/channel names and ids, duration, views, `publishedAt`, the NSFW age limit, and `thumbnailPath`. A streaming playlist with no files leaves `isLive` true.
- The upstream `_INSTANCES_RE` known-instance allowlist (a generated list) is deliberately not copied: the URL shape is the match and the API response is trusted as the PeerTube contract. `PeerTubePlaylistIE` (account/channel/playlist listings) is planned with its manifest row.
- Tests: `PeerTubeIETest` has 8 cases (URL forms, API mapping, short/embed/scheme forms, live playlist, long description, captions failure, harness case, unavailable API). Every host and address is `*.example`.
- 2 manifest rows: `PeerTubeIE` partial; `PeerTubePlaylistIE` planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 777, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures. (`JavaNetPlatformTest.cancelMidStreamDeletesTheTempFile` hit its 10-second timeout once under load and passed on the next run; the full core rerun was clean.)
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 18 partial, 45 planned, 1,688 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 5, BBC.

**File 5 of 14 — BBC (2026-09-30).** `BBCCoUkIE.kt` adds the programme/iPlayer subset of `BBCCoUkIE` from `yt_dlp/extractor/bbc.py` at the pin:

- URL forms: `bbc.co.uk/programmes/<pid>` and `bbc.co.uk/iplayer/[<channel>/](episode|playlist)/<pid>`; article and episode-list suffixes do not match.
- The page is read for a `vpid` (`mediator.bind({...})` player JSON first, then the `"vpid"` field). The public media selector (`open.live.bbc.co.uk/mediaselector/6/.../mediaset/<set>/vpid/<id>`) is called for `iptv-all` and `pc`; direct HTTP media, HLS (`m3u8_native`), DASH (`http_dash_segments`), and English TTML captions map onto the info dict. A `geolocation`/`notukerror` result falls back to the next media set and fails typed as geo-restricted only when no format was produced.
- Not translated: HDS, RTMP, ASX connections, the legacy programme-playlist fallback (a page without a vpid fails typed), the `bbc.com` scraper (`BBCIE`), the article page, and the iPlayer playlist classes, each with a planned manifest row. iPlayer TV playback is geo- and licence-restricted; a login wall or DRM wall is Partial.
- Tests: `BBCCoUkIETest` has 8 cases (URL forms, media selector mapping, vpid field, iPlayer playlist URL, geo fallback, all-geo typed failure, missing vpid, harness case). Every host and address is `*.example`.
- 6 manifest rows: `BBCCoUkIE` partial; `BBCIE`, the article, both iPlayer playlist classes, and the programme playlist planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 785, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 19 partial, 50 planned, 1,682 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 6, TikTok.

**File 6 of 14 — TikTok (2026-09-30).** `TikTokIE.kt` adds `TikTokIE` and the `TikTokVMIE` redirect from `yt_dlp/extractor/tiktok.py` at the pin:

- URL forms: `www.tiktok.com/@<user>/video/<id>`, `/embed/<id>`, `/share/video/<id>`, and `www.tiktokv.com`; `vm.tiktok.com`, `vt.tiktok.com`, and `www.tiktok.com/t/<code>` resolve one redirect hop with the `facebookexternalhit` user agent and re-enter the registry.
- The page's `__UNIVERSAL_DATA_FOR_REHYDRATION__` `webapp.video-detail` item maps: bitrateInfo `UrlKey` parsing (`v1200_h264_720p_1500000` style, `bytevc1` → `h265`, portrait/landscape dimensions, dedupe), play and watermarked download addresses, the music track, captions (`cla_info.caption_infos` then `subtitleInfos`), thumbnails, author and stats metadata, and the 10216/10222 login-required and 10204 blocked status codes. The `epochSecondsToDate` helper moved from Bilibili into `ExtractorUtils` and both extractors share it.
- Not translated: the app API (device registration, mobile endpoints), the challenge-cookie/impersonation path, logged-in-only access, and the user, sound, effect, tag, collection, and live classes, each with a planned manifest row. A login wall is Partial.
- Tests: `TikTokIETest` has 6 cases (URL forms, web data mapping, private status, blocked status, short-link registry redirect, harness case). Every host and address is `*.example`.
- 8 manifest rows: `TikTokIE` and `TikTokVMIE` partial; the six listing/live classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 791, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 21 partial, 56 planned, 1,674 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 7, SoundCloud.

**File 7 of 14 — SoundCloud (2026-09-30).** `SoundcloudIE.kt` adds `SoundcloudIE` and the `SoundcloudEmbedIE` redirect from `yt_dlp/extractor/soundcloud.py` at the pin:

- URL forms: `soundcloud.com/<uploader>/<title>[/<token>]`, `m.soundcloud.com`, the `api.soundcloud.com`/`api-v2.soundcloud.com` track URLs with `secret_token`, and the `w|player|p.soundcloud.com/player?...url=` embed (decoded and re-entered through the registry). Set, user, station, related, and search paths do not match.
- The API client id is discovered at runtime from the main page's script assets and refreshed once on an auth failure; it is never stored or committed. `media.transcodings` map to progressive and `m3u8_native` audio formats with codec, ext, abr, quality, and preview metadata; `ctr-`/`cbc-` DRM protocols are dropped and a DRM-only track fails typed. Artwork size families, secret tokens, duration, date, views, description, and uploader map onto the info dict.
- Not translated: the original download format, OAuth login, sets/playlists, users, stations, related, search, and comments, each with a planned manifest row. A login wall or DRM wall is Partial.
- Tests: `SoundcloudIETest` has 6 cases (URL forms, track mapping, API track URL, DRM-only failure, embed registry redirect, harness case). Every host and address is `*.example`.
- 9 manifest rows: `SoundcloudIE` and `SoundcloudEmbedIE` partial; the seven listing classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 797, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 23 partial, 63 planned, 1,665 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 8, Facebook.

**File 8 of 14 — Facebook (2026-09-30).** `FacebookIE.kt` adds `FacebookIE`, `FacebookPluginsVideoIE`, `FacebookRedirectURLIE`, and `FacebookReelIE` from `yt_dlp/extractor/facebook.py` at the pin:

- `FacebookIE` matches the classic video forms (`/<page>/videos/<id>`, `video.php?v=`, `video/embed?v=`, `watch/?v=`, posts, events, groups, watchparty, and the `facebook:<id>` keyword), reads the `handleServerJS`/`s.handle` `VideoConfig` instances, and maps `sd_src`/`hd_src`/`sd_src_no_ratelimit`/`hd_src_no_ratelimit` plus the `dash_manifest` XML through the shared `Mpd` parser. `og:` metadata, `ownerName`, `data-utime`, and view counts map onto the info dict; every format carries the `facebookexternalhit/1.1` user agent and the 250 MiB chunk hint. A page without the classic data and a login marker fails typed as a sign-in wall.
- The reel, plugins (`href=`), and `flx/warn` (`u=`) classes decode or canonicalize one URL and re-enter the registry.
- Not translated: the `data-sjs` RelayPrefetchedStreamCache/GraphQL paths, the tahoe fallback, watch parties, ads, and photos; `FacebookAdsIE` is a planned row. A login wall or DRM wall is Partial.
- Tests: `FacebookIETest` has 8 cases (URL forms, classic mapping, login wall, missing data, reel/plugins registry redirects, safety redirect, harness case). Every host and address is `*.example`. Fixed on the way: Java regex requires literal `{`/`}` to be escaped inside the server-JS patterns, where Python's regex was lenient.
- 5 manifest rows: four partial; `FacebookAdsIE` planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 805, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 27 partial, 64 planned, 1,660 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 9, Twitch.

**File 9 of 14 — Twitch (2026-09-30).** `TwitchIE.kt` adds `TwitchVodIE` and `TwitchStreamIE` from `yt_dlp/extractor/twitch.py` at the pin:

- `TwitchVodIE` matches `twitch.tv/<channel>/v|video/<id>`, `twitch.tv/videos/<id>`, the player, and the schedule forms. The public GraphQL `VideoMetadata` persisted query and the `videoPlaybackAccessToken` call mint a runtime usher HLS URL recorded as one `m3u8_native` format (the engine resolves the variants at download time). Title, description, duration, owner, date, views, and a `0x0`/original thumbnail pair map onto the info dict.
- `TwitchStreamIE` matches `twitch.tv/<channel>` (listing paths excluded), `go|m.twitch.tv`, and `player.twitch.tv/?channel=`. Stream metadata plus `streamPlaybackAccessToken` mint the usher HLS URL; an offline channel fails typed as not-yet-available.
- The `CLIENT_ID` constant is Twitch's public web GraphQL identifier, not a credential; no OAuth token, cookie, guest token, or signed media URL is stored. Storyboards, chapter moments, clips, collections, and the listing classes are not translated; their five classes are planned rows. A login wall or DRM wall is Partial.
- Test tooling: `FixtureRoute` gained an optional `requestBodyContains`, so two POSTs to the same GraphQL URL can be answered by different fixtures.
- Tests: `TwitchIETest` has 5 cases (URL forms, VOD mapping, stream mapping, offline typed failure, harness case). Every id, host, value, and signature is `*.example` or `fixture-*`.
- 7 manifest rows: `TwitchVodIE` and `TwitchStreamIE` partial; the five clip/collection/listing classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 810, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 29 partial, 69 planned, 1,653 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 10, Instagram.

**File 10 of 14 — Instagram (2026-09-30).** `InstagramIE.kt` adds `InstagramIE` and the `InstagramIOSIE` redirect from `yt_dlp/extractor/instagram.py` at the pin:

- URL forms: `/p/`, `/tv/`, `/reel/`, and `/reels/`, optionally behind one profile segment; `/share/`, tags, and stories do not match.
- The page's `data-sjs` RelayPrefetchedStreamCache data is searched recursively for `xig_polaris_media.if_not_gated_logged_out` (or the older `xdt_api__v1__media__shortcode__web_info.items`). Video versions, the `video_dash_manifest` XML through the shared MPD parser, image candidates (reversed), caption, user, `taken_at`, and views map onto the info dict; carousels become bounded `InfoDict.media` items. Numeric pks convert to shortcodes with the upstream base-64 alphabet. A page without the data falls back to the `og:video` meta pair and then fails typed as a sign-in wall.
- `InstagramIOSIE` converts `instagram://media?id=<pk>` to its shortcode and re-enters the registry.
- Not translated: the API `media/<id>/info/` path, comments, the logged-in product path, stories, users, and tags, each with a planned row. A login wall or DRM wall is Partial.
- Tests: `InstagramIETest` has 7 cases (URL forms, logged-out mapping, carousel media, `og:video` fallback, gated typed failure, iOS redirect, harness case). Every host, pk, and address is synthetic.
- 5 manifest rows: `InstagramIE` and `InstagramIOSIE` partial; the story, tag, and user classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 817, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 31 partial, 72 planned, 1,648 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 11, Dailymotion.

**File 11 of 14 — Dailymotion (2026-09-30).** `DailymotionIE.kt` adds `DailymotionIE` from `yt_dlp/extractor/dailymotion.py` at the pin:

- URL forms: `dai.ly/<id>`, `dailymotion.<tld>/video|embed/video|swf/video/<id>`, `player.html?video=<id>`, and the `lequipe.fr` host forms; playlist and user paths do not match.
- The public `player/metadata/video/<xid>` JSON maps: the `qualities` map to HTTP formats (with `H264-<w>x<h>[-60]` dimensions/fps and the `#cell` suffix stripped) and `m3u8_native` HLS formats, the `subtitles.data` map to VTT/SRT tracks, `posters`/`thumbnails` to sized thumbnails, and duration, `created_time`, owner, explicit flag, and `is_live`. A `DM007` error fails typed as geo-restricted; other errors as unavailable.
- Not translated on purpose: the authenticated GraphQL media call and its hardcoded OAuth client id/secret (credentials must not enter the repo), so description and like/view counts stay out. The playlist, search, and user classes are planned rows. A login wall or DRM wall is Partial.
- Tests: `DailymotionIETest` has 5 cases (URL forms, metadata mapping, geo error, generic error, harness case). Every host and address is `*.example`.
- 4 manifest rows: `DailymotionIE` partial; the playlist, search, and user classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 822, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 32 partial, 75 planned, 1,644 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 12, VK.

**File 12 of 14 — VK (2026-09-30).** `VKIE.kt` adds `VKIE` from `yt_dlp/extractor/vk.py` at the pin:

- URL forms: `vk.com/video_ext.php?oid=…&id=…` (plus the `m|new|vksport` hosts and `daxab.com`), `vk.com/video<oid>_<id>`, `vk.com/clip<oid>_<id>`, and the `?z=video…` group form; plain listing and profile URLs do not match.
- The embed page's `var playerParams = ({…})` and the public `al_video.php` POST both expose `player.params[0]`: `url<cache>` progressive formats (with the height parsed from the key), HLS (`m3u8_native`), DASH (`http_dash_segments`), RTMP, subtitle entries, title, author, duration, thumbnail, date, live flag, and the page's view count. Login payload code 3, error code 8, region blocks, and removal messages fail typed.
- Not translated: user-video listings, wall posts, VK Play records/live, chapter `time_codes`, and the YouTube/RuTube/Dailymotion/OK/Sibnet embed re-dispatch; the four classes are planned rows. A login wall or DRM wall is Partial.
- Tests: `VKIETest` has 5 cases (URL forms, embed mapping, watch AJAX mapping, login code, harness case). Every host and address is `*.example`.
- 5 manifest rows: `VKIE` partial; the four listing/play classes planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 827, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 33 partial, 79 planned, 1,639 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 13, Patreon.

**File 13 of 14 — Patreon (2026-09-30).** `PatreonIE.kt` adds the public post subset of `PatreonIE` from `yt_dlp/extractor/patreon.py` at the pin:

- URL forms: `patreon.com/posts/<id>`, `creation?hid=<id>`, and the `/<user>/posts/<slug>-<id>` form.
- The public `api/posts/<id>` JSON:API response (the upstream fields/includes query, bracket-encoded) maps the title, tag-stripped content, `image.large_url`/`url` thumbnail, `published_at`, user include, campaign include, `post_file` (direct file or `video`/`.m3u8` HLS), and `media` includes with `download_url` + `size_bytes` + mimetype extension. One attachment becomes `formats`; several become bounded `InfoDict.media` items. An embed-only post re-dispatches to a converted Vimeo player URL or the raw embed URL. `current_user_can_view = false` fails typed as a patron wall.
- Not translated: the campaign/search classes, comments, the media API for inlined `data-media-id` content, and the Vids.io/YouTube embed classification; `PatreonCampaignIE` is a planned row. A login wall or DRM wall is Partial.
- Tests: `PatreonIETest` has 6 cases (URL forms, post-file mapping, multiple media items, embed redirect, patron wall, harness case). Every host and address is `*.example`.
- 2 manifest rows: `PatreonIE` partial; `PatreonCampaignIE` planned.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 833, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 34 partial, 80 planned, 1,637 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

Next: file 14, archive.org.

**File 14 of 14 — archive.org (2026-09-30).** `ArchiveOrgIE.kt` adds the `ArchiveOrgIE` details/embed subset from `yt_dlp/extractor/archiveorg.py` at the pin:

- URL forms: `archive.org/details/<identifier>` and `/embed/<identifier>`, with an optional `/<entry>` suffix and query string.
- The embeddable player's `<play-av playlist=…>` attribute JSON demarks the playlist entries and subtitle tracks (with the HTML entities decoded); the public `metadata/<identifier>` API supplies the item metadata, file list, thumbnails, and formats. Unknown extensions and private files (no archive.org sign-in) are skipped. A single entry becomes the main info dict with `formats`; a multi-entry item becomes bounded child-job entries addressed by `/details/<identifier>/<entry>` URLs.
- Not translated: `YoutubeWebArchiveIE` (planned under T-124), reviews, and the Mixch host. A login wall or DRM wall is Partial.
- Tests: `ArchiveOrgIETest` has 5 cases (URL forms, single-entry mapping, multi-entry child jobs, entry-specific URL, harness case). Every id, host, and address is synthetic; archive.org's string-typed numbers are parsed locally.
- 1 new manifest row for this file (`ArchiveOrgIE` partial; `YoutubeWebArchiveIE` was already planned under T-124).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 838, ui 108, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures. (`SpotifyPreviewUiTest` failed once on a Compose idle race and passed on the rerun; the final rerun was clean.)
- `./gradlew --no-parallel :tools:port-manifest:run` then `:tools:port-manifest:check` — ok: 1,751 upstream classes, 35 partial, 80 planned, 1,636 not started.
- Cross-target compiles (`:shared:core` wasm/iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — build clean.

**T-125 complete (2026-09-30).** All 14 files are in: the Twitter remainder plus Bilibili, Vimeo, PeerTube, BBC, TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, and archive.org. Every one has harness cases and manifest rows, `:tools:port-manifest:check` passes, and desktop/Android route every matched URL to Kotlin through the shared production registry (`DesktopRouteClassifier` returns `KOTLIN` on `registry.suitableFor(url) != null` before any probe or CLI; Android injects the same `SharedEngineBindings` registry into `AndroidRouteClassifier`). Unmatched URLs still fall through to the desktop CLI or Android Chaquopy. Phase 16 is done.
