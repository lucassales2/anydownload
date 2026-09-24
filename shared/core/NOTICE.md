# NOTICE — generic extractor subset

`com.anydownlod.core.extract.GenericExtractor` is a Kotlin translation of a
small subset of yt-dlp's generic extractor.

- Upstream file read: `yt_dlp/extractor/generic.py`
- Upstream revision: tag `2026.08.19` (pip pin `yt-dlp==2026.8.19`, the
  Android Chaquopy pin) — commit `3a08beaf031ab68f966401ead017ac81fe8486cf`
- Read on: 2026-09-24
- Upstream license: Unlicense
  (https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE)

The translation covers only:

- `<video src>`, `<audio src>`, and `<source src>` nested inside those
  elements, case-insensitively;
- relative URL resolution against the page URL, and the upstream
  `orderedSet()` dedupe and `unescapeHTML()`/`\/` de-escaping for the values;
- at most one direct HTTP(S) media URL out, with the shared `UrlPolicy`
  applied to every candidate.

Not translated: playlists, embeds, iframes, JSON-LD, meta refresh, HLS, DASH,
site extractors, and the rest of `generic.py`. `generic.py` is not vendored
and nothing in shared code runs Python or a subprocess.

## NOTICE — extractor core (T-057)

`com.anydownlod.core.extract` adds the extractor base and helper subset named
by these upstream files at the same revision (tag `2026.08.19`, commit
`3a08beaf031ab68f966401ead017ac81fe8486cf`, read 2026-09-24, Unlicense):

- `yt_dlp/extractor/common.py` — `InfoExtractor` (`_VALID_URL`, `suitable`,
  `_match_id`, `_real_extract`), the info dict / format model, typed errors,
  and the HTTP helper behavior (`_download_webpage`, `_download_json`,
  redirect handling). `GenericIE` is the D3 HTML5 subset placed on that base.
- `yt_dlp/utils/_utils.py` — `_search_regex`, `_html_search_meta`,
  `_parse_json`, `traverse_obj` (subset), `url_or_none`, `int_or_none`,
  `float_or_none`, `str_or_none`, `unescapeHTML`, `parse_codecs`,
  `mimetype2ext`, `parse_iso8601`, `unified_strdate`.

Only the functions listed in `port/manifest.json` scope strings are
translated. `common.py`, `_utils.py`, and every extractor file stay unvendored.


## NOTICE — format selector and sorter (T-058)

`com.anydownlod.core.format` translates the D4 subset of these upstream files
at tag `2026.08.19`, commit
`3a08beaf031ab68f966401ead017ac81fe8486cf`, read 2026-09-24, Unlicense:

- `yt_dlp/YoutubeDL.py` — `build_format_selector` grammar (SINGLE, GROUP,
  PICKFIRST, MERGE), `_build_format_filter`, and the `best`/`worst`/type/`*`/
  `.N` selection and incomplete-format fallback.
- `yt_dlp/utils/_utils.py` — the `FormatSorter` field tables, default order,
  `+`/`~`/`:` handling, the preference tuple logic, and the
  `_fill_sorting_fields` derivations for the D4 field subset.

Not translated: extractor JS/player handling, `all`/`mergeall`, multiple
simultaneous streams, extractor `_format_sort_fields` beyond what D4 needs,
and the full `YoutubeDL` option surface. `YoutubeDL.py` and `_utils.py` are
not vendored.


## NOTICE — extractor test harness (T-059)

`shared/core/src/commonTest/.../extract/harness` translates the matcher
semantics from yt-dlp's `test/test_download.py` (`expect_info_dict`) and
`test/helpers.py` (`expect_value`, `md5` handling) at tag `2026.08.19`,
commit `3a08beaf031ab68f966401ead017ac81fe8486cf`, read 2026-09-24,
Unlicense. The Python test runner is not translated and no Python is read at
test time. Recorded fixtures pass through `FixtureRedactor` before they enter
the repository; signed URLs, tokens, visitor data, and cookies never do.


## NOTICE — YouTube stage 1, JS-less `visionos` client (T-060)

`com.anydownlod.core.extract.youtube.YoutubeIE` translates a subset of these
upstream files at tag `2026.08.19`, commit
`3a08beaf031ab68f966401ead017ac81fe8486cf`, read 2026-09-24, Unlicense:

- `yt_dlp/extractor/youtube/_base.py` — the `visionos` `INNERTUBE_CLIENTS`
  entry (client name 101, version 1.02, Apple/visionOS fields, user agent),
  `_extract_context` language/time-zone handling, `generate_api_headers`, and
  the `youtubei/v1/player` call shape from `_call_api`.
- `yt_dlp/extractor/youtube/_video.py` — the single-video subset of
  `_VALID_URL`, `_generate_player_context`, `_get_checkok_params`,
  `_extract_player_response` (with the watch-page visitor id sent as
  `X-Goog-Visitor-Id` for that request only), the playability-status error
  mapping, the `streamingData` to format fields in `process_format_stream`,
  `http_chunk_size`, and the `_real_extract` metadata for a single video.

Not translated: playlists, channels, live streams, comments, subtitles,
cookies, PO token providers, the JavaScript challenge pipeline (`yt-dlp-ejs`
and `sig`/`n` solving), storyboards, and every innertube client other than
`visionos`. Formats that need a signature or `n` transform are dropped and
counted in `InfoDict.formatsNeedingJs`. `_base.py` and `_video.py` are not
vendored.


## NOTICE — bundled yt-dlp-ejs and Zipline (T-069)

- `third_party/yt-dlp-ejs/0.8.0/` bundles the solver scripts yt-dlp
  `2026.08.19` vendors (`yt.solver.core.js`, `yt.solver.core.min.js`,
  `yt.solver.lib.js`, `yt.solver.lib.min.js`), their Unlicense, and the
  upstream SHA3-512 hashes from `jsc/_builtin/vendor/_info.py` at commit
  `3a08beaf031ab68f966401ead017ac81fe8486cf`. The Gradle task
  `:shared:core:verifyEjsBundle` fails on a missing or tampered file, and
  `EjsScripts.kt` is generated from the verified minified pair. Nothing is
  downloaded at runtime.
- `app.cash.zipline:zipline:1.27.0` (Apache-2.0, with the QuickJS MIT notice)
  is the embedded JavaScript runtime for JVM, Android, and Kotlin/Native. The
  spike recorded QuickJS version `2021-03-27` and solver-script evaluation on
  the JVM and iOS Simulator. The runtime only evaluates the bundled solver.
