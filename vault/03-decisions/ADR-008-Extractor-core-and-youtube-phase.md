---
id: ADR-008
type: adr
status: accepted
created: 2026-09-24
tags: [architecture, decisions, engine, platforms, youtube]
---

# ADR-008 — Extractor core and YouTube; equivalence with yt-dlp is the measured goal

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state: an in-process Kotlin port of yt-dlp on every target. Does not supersede [ADR-005](ADR-005-Desktop-metube-phase.md), [ADR-006](ADR-006-Local-http-engine-phase.md), or [ADR-007](ADR-007-Generic-extractor-phase.md). Direct files and the generic subset stay on the shared engine. Desktop CLI and Android Chaquopy stay for every URL the Kotlin registry does not match.

## Context

Phase D3 is done. Each host downloads a direct file and one simple HTML media page through shared Kotlin. Everything else still runs upstream yt-dlp on desktop (CLI) and Android (Chaquopy), and fails typed on iOS and web. The decision log said the next record should capture YouTube via yt-dlp-ejs.

Owner answers, 2026-09-24:

- **The end goal is yt-dlp feature equivalence in Kotlin.** Equivalence means two things: the **core engine** (info_dict and formats model, the format-selection language, HTTP/HLS/DASH downloaders, playlists, output templates, cookies, postprocessors, typed options) and, over time, the **full extractor catalog**. The catalog is reached extractor by extractor; it is a tracked count, not a promise for any one phase.
- **Phase D4** delivers the extractor core and a **YouTube single video** on all four hosts. YouTube is staged: first the JS-less `visionos` client (no runtime), then yt-dlp-ejs with the `web` client.
- The **media toolkit stays not built**. D4 downloads single-file formats only: progressive video or one audio-only stream. Merging and transcoding are the phase after this one.
- The **JavaScript challenge runtime** is a shared `JsRuntime` port with per-host adapters. One embedded engine is preferred: Cash App Zipline's QuickJS binding on JVM, Android, and Kotlin/Native iOS, with JavaScriptCore on iOS only if the spike fails. Desktop may fall back to Deno on `PATH`. The web page runs the solver in its own JavaScript.
- **yt-dlp-ejs is bundled**, pinned to the release yt-dlp vendors at the same tag, with its Unlicense notice and the upstream SHA-512 hashes verified at build time. No runtime script download in D4.
- Desktop CLI and Android Chaquopy **stay for unmatched URLs and become an opt-in test oracle**: `yt-dlp -J` output is compared with the Kotlin info dict on the same URLs.
- Extractor tests run upstream-style `_TESTS` cases: fixtures by default, live only opt-in.
- The phase's slice must run on **all four hosts** before the phase is done.
- The **upstream pin stays `2026.08.19`**, which is also the latest release on 2026-09-24. Move to a newer tag only at a phase boundary and record it.
- The **format-selection language is ported** (`bv*+ba/b`, filters, sort). Typed options compile to a spec; the domain still never carries a spec string.
- The **preview and Edit panel** are fed by the Kotlin extractor on every host.
- After YouTube, **X / Twitter** is the next named site. It is roadmap input, not D4 scope.
- Infrastructure for the catalog is built now: an `InfoExtractor` base that mirrors upstream helpers, a `_TESTS` harness, and a machine-readable port manifest that generates the coverage table in the equivalence note.
- Planning documents are committed as one docs commit.

Upstream facts inspected on 2026-09-24 at tag `2026.08.19`: `_extractors.py` names 1,751 extractor classes; `youtube/_video.py` sets `_DEFAULT_CLIENTS = ('visionos', 'web')` and `_DEFAULT_JSLESS_CLIENTS = ('visionos',)`; `youtube/_base.py` declares `visionos` with `REQUIRE_JS_PLAYER: False` and `INNERTUBE_CONTEXT_CLIENT_NAME: 101`; `youtube/jsc/_builtin/vendor/_info.py` pins yt-dlp-ejs `0.8.0` with SHA-512 hashes for `yt.solver.core.js`, `yt.solver.lib.js`, and their minified and Deno/Bun variants. [yt-dlp/ejs](https://github.com/yt-dlp/ejs) is Unlicense; release `0.8.0` is dated 2026-03-17. [cashapp/zipline](https://github.com/cashapp/zipline) is Apache-2.0; latest release `1.27.0`.

## Decision

- **Phase D4** is the next implementation work. Tasks are [T-055](../06-tasks/T-055-Port-manifest-and-equivalence-matrix.md) through [T-074](../06-tasks/T-074-Phase-4-verification.md), sequenced in the [phase note](../00-project/Phase-4-Extractor-Core-and-YouTube.md). [T-068](../06-tasks/T-068-Gate-jsless-youtube-four-hosts.md) is a mid-phase gate: a JS-less YouTube video on all four hosts before any JavaScript runtime work starts.
- **Equivalence is measured, not asserted.** [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md) lists core capabilities by hand and carries an extractor-coverage table generated from `port/manifest.json`. Every ported module records the upstream path, tag, commit, and the Kotlin file. That note, not the MeTube matrix, is the target for "the Kotlin port of yt-dlp".
- **Extractor core in `shared/core`:** `com.anydownlod.core.extract` gains an `InfoExtractor` base, `InfoDict`, `MediaFormat`, typed extraction errors, a URL-matching registry, and the upstream helper subset (`searchRegex`, `parseJson`, `traverse`, `matchId`, `urlOrNone`, `intOrNone`, `unescapeHtml`). `GenericExtractor` moves onto the base without behavior change. Translations are file-by-file from Unlicense source with the notice and revision; `common.py` and `utils` are not vendored.
- **HTTP port grows:** `HttpTransfer` accepts method, headers, body, and byte ranges on every host, including the extension bridge. Header names are an allowlist the extractor declares. Cookies stay out until [T-018](../06-tasks/T-018-Cookie-lifecycle.md).
- **Format selection:** port `build_format_selector` and the `-S` sort subset. `DownloadOptions` compiles to a spec. In D4 the compiler never emits a merge (`+`); "video" is the best single-file format and "audio" is the best audio-only stream. MP3, WAV, FLAC, and any selection that needs merging or transcoding fail typed with a message that names the media toolkit as not built. No silent downgrade.
- **YouTube, stage 1 (JS-less):** translate the `visionos` innertube `player` request, `streamingData` to formats, playability status to typed errors, and `videoDetails`/microformat to metadata. URL forms: `watch?v=`, `youtu.be/`, `/shorts/`, `/embed/`, `/live/` treated as a video id only. Formats that need a signature or `n` transform are dropped in this stage, as upstream does without a runtime. No playlists, channels, live, comments, subtitles, cookies, or PO tokens in D4.
- **YouTube, stage 2 (EJS):** bundle yt-dlp-ejs `0.8.0` under `third_party/yt-dlp-ejs/` with its UNLICENSE and the upstream hashes; a Gradle task verifies the hashes and generates Kotlin string constants. Port the JS challenge provider protocol, player JS fetch and caching, `signatureCipher` and `n` solving, and the `web` client. `JsRuntime` is the port; Zipline QuickJS on JVM/Android/iOS and the page's JavaScript on web are the adapters. Desktop may probe Deno on `PATH` as a second adapter.
- **Preview:** `MediaPreviewSource` backed by the registry on every host; the Edit panel lists only qualities and containers the extracted formats can satisfy without the toolkit and explains the rest.
- **Hosts:** desktop and Android route registry-matched URLs to Kotlin and never to the CLI or Chaquopy for those URLs; unmatched URLs keep the fallbacks. iOS and web run the same shared path; the web extension carries every request with the declared headers and body.
- **Native HLS and DASH downloaders** (fragment concatenation, AES-128, fMP4 init segments, MPD segment templates) are in D4 as P1 work after the gate; they remove the current misclassification of a manifest as a "direct file". No FFmpeg.
- **Oracle tests:** `apps/desktop` gains opt-in differential tests that run the installed `yt-dlp -J` and compare normalized fields with the Kotlin info dict. Default CI stays fixture-only.
- **Out of D4, explicitly:** merging, audio extraction and transcoding, clips, YouTube playlists/channels/live/comments/subtitles, PO token providers, cookies, X/Twitter and any other site, free-form yt-dlp JSON, runtime EJS updates, retiring the CLI or Chaquopy, store submission.

## Alternatives

- YouTube through EJS only, no JS-less stage. Rejected: the JS-less client proves the extractor core and the four-host routing before four JavaScript runtimes exist, and gives a working fallback when a runtime is missing.
- Port `jsinterp.py` as the shared JavaScript interpreter. Rejected: upstream moved to EJS because the interpreter could not keep up; translating it would repeat that race on every break.
- Per-platform JavaScript engines (Javet or GraalJS, QuickJS, JavaScriptCore). Kept as the fallback if the Zipline spike fails on any host.
- Fetch EJS scripts at runtime from GitHub releases, as yt-dlp does. Deferred: a bundled pinned copy keeps the trust boundary at build time in a phase that also adds a JavaScript runtime.
- Build merging on desktop now. Rejected: the toolkit is a phase of its own on four hosts; D4 downloads single files honestly.
- Hand-maintained coverage table. Rejected: 1,751 extractors need a generated table from a manifest.
- Move to a newer yt-dlp tag. Moot on 2026-09-24: `2026.08.19` is the latest release.

## Consequences

D4 ends with the shared engine owning YouTube single videos on every host at progressive or audio-only quality, an extractor core that later translations plug into, a coverage number that starts near 1 of 1,751, and a test harness that can carry upstream test cases. Best-quality YouTube video still needs the media toolkit; the UI says so instead of merging. The port grows an embedded JavaScript engine and a bundled solver whose version follows the yt-dlp pin.

Risks: YouTube client and challenge behavior change often ([R-07](../04-delivery/Risk-register.md)); Zipline's QuickJS may be older than the version EJS recommends for speed ([R-16](../04-delivery/Risk-register.md)); an innertube response contains signed media URLs, so fixtures must be synthesized or redacted; MV3 `fetch` cannot set every header the client declares; the web page needs a CSP that allows the bundled solver and nothing else.

## Validation / approval

Owner, 2026-09-24, from the planning answers recorded in Context. D4 is complete when [T-074](../06-tasks/T-074-Phase-4-verification.md) records a YouTube single video on each host, JS-less and with EJS, the oracle run, the regenerated coverage table, and the honest limits, or a written blocker.
