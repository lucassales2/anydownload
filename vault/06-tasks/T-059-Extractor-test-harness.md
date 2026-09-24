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

- [ ] Matcher unit tests for every `Expect` kind, translated from upstream helper tests with the notice.
- [ ] Fixture mode runs with no network; a case whose fixture is missing fails with a clear message.
- [ ] Live mode is off by default and refuses URLs not on the case's allowlist.
- [ ] The recorder's redaction is unit-tested with a synthetic player-style JSON containing signed URLs and tokens.

## Evidence / notes

Not started.
