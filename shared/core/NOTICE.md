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