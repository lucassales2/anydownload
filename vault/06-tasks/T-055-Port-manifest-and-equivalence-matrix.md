---
id: T-055
type: task
priority: P0
milestone: D4
tags: [task, engine, tooling, docs]
---

# T-055 — Port manifest and equivalence coverage generator

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

A machine-readable record of which yt-dlp modules are translated, and a Gradle task that validates it and regenerates the coverage block in the [equivalence note](../01-product/Ytdlp-equivalence.md). Later tasks update the manifest in the same change that ports a module.

## Dependencies

- [T-051](T-051-Phase-3-verification.md) — D3 is done; the generic subset is the first entry.

## Context the next session needs

Upstream `_extractors.py` at tag `2026.08.19` names 1,751 extractor classes. The list of class names is data, not code; the generator may store it as `port/upstream-extractors.json` with the tag and commit it came from. Do not store any upstream source.

## Work

- Create `port/manifest.json`:
  - `upstream`: `{ "repository": "yt-dlp/yt-dlp", "tag": "2026.08.19", "commit": "3a08beaf031ab68f966401ead017ac81fe8486cf", "license": "Unlicense" }`.
  - `modules[]`: `{ "id", "kind": "extractor|core|downloader|jsc|utils", "upstreamPath", "kotlinFiles[]", "status": "ported|partial|planned|not-started", "scope" (free text naming the subset), "tasks[]", "portedAt" }`.
  - Seed with `GenericIE` (partial, HTML5 media subset, `shared/core/.../extract/GenericExtractor.kt`, [T-045](T-045-Generic-extractor-subset.md)) and `YoutubeIE` (planned, single video, [T-060](T-060-Youtube-jsless-extractor.md)).
- Create `port/upstream-extractors.json`: the sorted list of extractor class names from `_extractors.py` at the pin, with the tag and commit. Generate it with the tool below from a fetched copy; do not commit the fetched file.
- Add `tools/port-manifest` as a JVM Gradle module with a `run` task that: validates the manifest schema and that every `kotlinFiles` path exists; counts Ported / Partial / Planned / Not started against the upstream list; rewrites the block between `<!-- port-manifest:coverage:start -->` and `<!-- port-manifest:coverage:end -->` in `vault/01-product/Ytdlp-equivalence.md` with the counts and a table of every non-`not-started` module; fails on an unknown extractor id.
- Add a `check`-time task that fails when the manifest is invalid, so a ported module cannot land without a manifest entry.
- Document the commands in the README testing block.

## Acceptance criteria

- [ ] `./gradlew :tools:port-manifest:run` regenerates the coverage block with 1,751 upstream classes, 0 ported, 1 partial, 1 planned.
- [ ] Validation fails on a missing Kotlin file path and on an extractor id not in the upstream list (unit tests).
- [ ] No upstream source file is committed; only the names list, tag, and commit.
- [ ] README and the equivalence note describe the manifest and the command.

## Evidence / notes

Not started.
