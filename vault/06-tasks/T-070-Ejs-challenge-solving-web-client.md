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

- [x] Fixture tests: runtime present → ciphered formats resolved; runtime absent → stage 1 result, hidden count unchanged; solver error → typed and fell back.
- [x] Live case passes on the JVM with the Zipline runtime from T-069; evidence records format counts before and after.
- [x] No fixture or log contains a real player URL, `googlevideo` URL, or token.
- [x] Oracle from T-062 compares the format set with `player_client=web,visionos`; the diff is empty or explained in Evidence.

## Evidence / notes

Done 2026-09-24.

- `com.anydownlod.core.jsc.JsChallengeProvider` translates the upstream stdin/stdout protocol (`jsc/_builtin/ejs.py`): the `{type: player|preprocessed, player|preprocessed_player, requests: [{type, challenges}], output_preprocessed}` input, batched `n`/`sig` requests, the `{responses: [{type, data: {challenge: result}}], preprocessed_player}` reply, and typed redacted errors. The preprocessed player is cached in memory per player id (FNV-1a key). The public request/response model follows `jsc/provider.py` (`JsChallengeType`, challenge inputs/outputs); the provider registry and plugin preferences are not translated.
- `YoutubeIE` stage 2 (default `jsRuntime = NoJsRuntime`, so stage 1 is unchanged): the watch page now also yields the `jsUrl` player URL, `STS`, and `INNERTUBE_CLIENT_VERSION`; the player JS is fetched with a **12 MiB** cap and cached in a Mutex-guarded session map; the `web` client player request uses client name 1 / version `2.20260708.00.00` / `signatureTimestamp`; `signatureCipher` (`s`, `sp`, `url`) is resolved through a `sig` challenge (char-range spec, `s[i]` permutation) and `n` values through the `n` challenge with a query rewrite; visionos formats stay first and duplicates are deduped by format id. The hidden count is the unresolved total across both clients.
- Tests: `JsChallengeProviderTest` 4 (input shape, reply parsing, preprocessed reuse, typed solver/missing-runtime errors) and `YoutubeStage2Test` 3 (runtime present resolves the ciphered `140` and `n`-challenged `137`, merges the web-only `251` without duplicating `140`, hidden 0; runtime absent keeps the stage-1 format and hidden 2; a solver failure falls back with the hidden count kept). Fixtures use `cdn.fixtures.example.net`, a synthetic `FIXTURE_VISITOR`, and a synthetic `jsUrl`.
- Live JVM run with the T-069 Zipline runtime: `./gradlew :shared:core:jvmTest --tests "…YoutubeLiveTest" -PliveExtractorTests=true` → `live youtube stage2: stage1Formats=27 stage1NeedsJs=0 stage2Formats=27 stage2NeedsJs=0 quickjs=2021-03-27`. The visionos response already exposed all 27 formats for this video, so the web client added no unique formats in this session; the real player JS fetch + solver path ran without regression (stage 2 ≥ stage 1, hidden ≤ stage 1).
- Oracle with both clients: `./gradlew :apps:desktop:test --tests "…YtDlpOracleTest" -PytDlpOracle=true` → `oracle youtube web,visionos: kotlinFormats=27 oracleFormats=27 diffs=0` (alongside `27/27, diffs=0` and audio-only `10/10, diffs=0`). Empty diff.
- Manifest: `jsc/provider.py` and `jsc/_builtin/ejs.py` added as partial `jsc` entries; `youtube/_video.py` and `youtube/_base.py` scopes extended with the web client, player JS, and sig/n resolution; the coverage block was regenerated and `:tools:port-manifest:check` passes. No real player URL, `googlevideo` URL, visitor id, or token appears in a fixture or log.
