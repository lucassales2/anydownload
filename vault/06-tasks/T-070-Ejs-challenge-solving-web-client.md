---
id: T-070
type: task
priority: P0
milestone: D4
tags: [task, engine, youtube, javascript]
---

# T-070 — EJS challenge solving and the `web` client

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

With a `JsRuntime` available, `YoutubeIE` also queries the `web` client, fetches the player JavaScript, solves `sig` and `n` challenges through the bundled solver, and returns the full single-file format set. Without a runtime it behaves exactly as stage 1. Covers E-11.

## Dependencies

- [T-069](T-069-Ejs-bundle-and-jsruntime-port.md).

## Context the next session needs

Upstream at the pin: `extractor/youtube/jsc/provider.py` (`JsChallengeRequest`, `JsChallengeType.N`/`SIG`, `NChallengeOutput`, `SigChallengeOutput`), `jsc/_builtin/ejs.py` (stdin JSON protocol to the solver: player JS, challenge list; stdout JSON: results, preprocessed player cache), `_video.py` (`_extract_player_url`, `_load_player`, `_decrypt_signature`, `_decrypt_nsig`, `signatureCipher` parsing, `_DEFAULT_CLIENTS = ('visionos', 'web')`, `_WEBPAGE_CLIENTS`), `_base.py` `web` client context. The player JS is a few MB: it needs its own read cap (record the value) and an in-memory cache keyed by player id for the app session. Translate only what the `web` client's video path uses.

## Work

- `JsChallengeProvider` (shared): builds the solver input from the player JS and a batch of challenges, calls `JsRuntime.evaluate(EjsScripts.lib + core, ...)`, parses the output, and caches the preprocessed player per player id. Errors are typed and never include script output.
- `YoutubeIE` stage 2: fetch the watch page (bounded; extract `ytcfg`, player URL, and `INNERTUBE_CONTEXT` as upstream does for `web`), request the `web` client's `player` response with the headers upstream sends at the pin, decode `signatureCipher` (`s`, `sp`, `url`) with the `sig` result, apply the `n` result to the `n` query parameter, and merge formats from both clients with upstream's dedupe rule. `visionos` stays first; if the runtime is missing or fails, log once redacted and fall back to stage 1 results.
- Format fields from the `web` client: `formatId` gets the client suffix as upstream does when duplicates differ; `sourcePreference` follows upstream client ordering.
- Harness: fixture player JS is **synthesized** (a tiny script that exposes the same function shapes the solver expects) with fixture `signatureCipher` and `n` values; a live case behind `-PliveExtractorTests=true` asserts the `web` client adds formats and that at least one previously hidden format now has a URL (count only, no URLs in the log).
- Manifest: `jsc/provider.py`, `jsc/_builtin/ejs.py` partial; `_video.py` scope extended.

## Acceptance criteria

- [ ] Fixture tests: runtime present → ciphered formats resolved; runtime absent → stage 1 result, hidden count unchanged; solver error → typed and fell back.
- [ ] Live case passes on the JVM with the Zipline runtime from T-069; evidence records format counts before and after.
- [ ] No fixture or log contains a real player URL, `googlevideo` URL, or token.
- [ ] Oracle from T-062 compares the format set with `player_client=web,visionos`; the diff is empty or explained in Evidence.

## Evidence / notes

Not started.
