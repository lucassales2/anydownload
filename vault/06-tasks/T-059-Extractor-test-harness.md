---
id: T-059
type: task
priority: P0
milestone: D4
tags: [task, engine, testing]
---

# T-059 — Extractor test harness: upstream-style `_TESTS`

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [Testing strategy](../04-delivery/Testing-strategy.md)

## Outcome

A Kotlin test harness that runs cases shaped like yt-dlp's `_TESTS`: a URL, an expected `info_dict`, and matchers. Fixture mode replays recorded or synthesized HTTP responses in default CI. Live mode runs the same cases against the public URLs only when a Gradle property is set. Covers E-27.

## Dependencies

- [T-057](T-057-Extractor-core.md) — extractors and `InfoDict`.

## Context the next session needs

Upstream `test/test_download.py` and `test/helpers.py` (`expect_info_dict`, `expect_value`) define the matchers: exact value, `md5:<hex>` for long strings, type matchers (`int`, `str`, `float`), `re:<regex>`, `count:<n>`, `startswith:`, `mincount:`, `maxcount:`, plus `only_matching`, `skip`, and `params`. Translate the matcher semantics, not the runner.

## Work

- `ExtractorCase(url, infoDict: Map<String, Expect>, onlyMatching, skipReason, params)` and `Expect` sealed matchers. Load cases from Kotlin declarations next to each extractor test; do not read Python.
- `FixtureHttpTransfer`: maps `(method, url pattern)` to a recorded response file under `shared/core/src/commonTest/resources/fixtures/<extractor>/`. Recording helper for the JVM only, opt-in, that **redacts** `googlevideo` and other signed URLs, visitor ids, tokens, and cookies before writing; the review checklist for a new fixture is in the file header.
- `runCase(extractor, case)`: fixture mode by default; live mode when `-PliveExtractorTests=true` and the case is in the task note's public list; live failures are reported with the redacted reason, never the body.
- Migrate `GenericExtractorTest` cases to the harness shape where it fits; keep the old tests until the new ones cover them.
- Gradle: `liveExtractorTests` property plumbed like `iosLiveFixturePort`; default CI never sets it.

## Acceptance criteria

- [x] Matcher unit tests for every `Expect` kind, translated from upstream helper tests with the notice.
- [x] Fixture mode runs with no network; a case whose fixture is missing fails with a clear message.
- [x] Live mode is off by default and refuses URLs not on the case's allowlist.
- [x] The recorder's redaction is unit-tested with a synthetic player-style JSON containing signed URLs and tokens.

## Evidence / notes

Done 2026-09-24.

- New `shared/core/src/commonTest/.../extract/harness/`: `ExtractorTestHarness.kt` (`ExtractorCase` with `url`/`infoDict`/`onlyMatching`/`skipReason`/`params`/`routes`/`live`, the `Expect` matchers `Value`, `IntType`, `FloatType`, `StringType`, `Md5`, `RegexMatch`, `StartsWith`, `Count`, `MinCount`, `MaxCount`, `Nested` with upstream `md5:`/`re:`/`count:`/`startswith:`/`mincount:`/`maxcount:` helpers, `CaseResult`, `runCase`), `FixtureHttpTransfer.kt` (`FixtureStore`, `FixtureRoute` with glob URL patterns and inline or resource bodies, `FixtureMissingException`), `FixtureRedactor.kt`, and a pure-Kotlin `Md5.kt` for the upstream `md5:` matcher. Each file carries the Unlicense header and the pin; the Python runner is not translated.
- Dotted-path matching accepts upstream snake_case names (`formats.0.format_id`, `webpage_url`, `extractor_key`) against `InfoDict`/`MediaFormat`.
- Fixture mode injects `ExtractorHttp(FixtureHttpTransfer(...))`; a missing route or resource raises `FixtureMissingException` with only the method and host/path. Live mode requires `ExtractorTestRun.live`, an allowlisted URL, and a real `ExtractorHttp`; otherwise the case is skipped or fails typed. `runCase` is `suspend` so it works on every target.
- Redaction replaces `googlevideo` hosts with a synthetic host, blanks `sig`/`n`/`expire`/`ip`/`visitor*` query values, and blanks `visitorData`/`poToken`/`signatureCipher`/token JSON values; the JVM `FixtureRecorder` (opt-in, JVM-only) writes only the redacted body under `shared/core/src/commonTest/resources/fixtures/<extractor>/` and never writes headers, so cookies cannot leak. `fixtures/self/hello.json` is the reviewed sample resource.
- Gradle: `-PliveExtractorTests=true` becomes the JVM `liveExtractorTests` system property and the native `LIVE_EXTRACTOR_TESTS` environment variable; default CI never sets it.
- Tests: `ExpectTest` 6 (every matcher, RFC 1321 MD5 vectors, redacted failure messages), `FixtureRedactorTest` 3 (synthetic innertube player JSON, ordinary fields kept, visitor query), `ExtractorHarnessTest` 8 (the first migrated `GenericIE` case, dotted paths, missing fixture, wrong expectation, live gate/allowlist, `onlyMatching`, `skip`, unsuitable URL, store-backed route); JVM `ClasspathFixtureStoreTest` 2 and `FixtureRecorderTest` 2 (redaction + path traversal).
- The T-057 common extractor tests moved from `runBlocking` to multiplatform `runTest` so the whole common test source now also compiles for Wasm (`:shared:core:compileTestKotlinWasmJs`); the old `GenericExtractorTest` stays green.
- Manifest: `test/helpers.py` added as a partial `utils` entry naming both `test/helpers.py` and `test/test_download.py` semantics; coverage block regenerated; `NOTICE.md` records the harness.
- Verification: `./gradlew :shared:core:jvmTest` → 225 tests, 0 failures; `:apps:desktop:test` and `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; `:shared:core:compileTestKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin`, `:shared:core:compileKotlinIosSimulatorArm64` green; `:tools:port-manifest:check` green.
