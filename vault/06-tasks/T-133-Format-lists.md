---
id: T-133
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, formats]
---

# T-133 — Format lists and multi-stream

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

`FormatSpec` and `FormatSelector` cover the whole E-05 selection language: a `,` list keeps every child's selection, `all` selects every downloadable format best-first, `mergeall` folds every usable stream into one merged selection (best first), and a `+` node can yield more than one merge pair. Tests only: the engine keeps its one-job consumption until a later task wires multi-selection through. Covers E-05.

## Dependencies

- [T-120](T-120-Metro-verification.md) — Done 2026-09-29. D10 does not start before the Metro gate passes.

## Context the next session needs

Upstream `YoutubeDL.build_format_selector` at the pin. The reference semantics:

- `isinstance(selector, list)` (`,`) builds every child selector and concatenates what each one yields.
- `all` yields `_check_formats(ctx['formats'][::-1])` — every format, best first (upstream keeps `ctx['formats']` sorted worst-first, best last).
- `mergeall` folds every format with `vcodec != none or acodec != none` from best to worst into one merged selection.
- `+` (MERGE) yields `itertools.product(selector_1(ctx), selector_2(ctx))`, so several pairs, not one.
- `/` (PICKFIRST) keeps the first child with a non-empty result; `()` (GROUP) passes through.

Today `FormatSelector.select` returns one `Selection`: `firstNonEmpty` stops at the first child, `merge` takes the first `Single` on each side, and `FormatSpec` has no `all`/`mergeall` atom. The D4 one-download note sits at the top of `FormatSelector.kt`.

Files: `anydownload` and `FormatSelector.kt`. Tests: `anydownload`.

## Work

- `FormatSpec.parse`: add `all` and `mergeall` as `Single` atoms (exact lowercase, as upstream matches them), distinct from explicit format ids. `/`, `,`, `+`, `()`, and `[key op value]` behavior stays.
- `FormatSelector`: evaluate a spec to every selection it yields. Keep `Selection.Single` and the two-stream `Selection.Merge(video, audio)` for the existing callers, and add a shape for a merged selection over N formats (`mergeall`). Comma lists concatenate; `+` yields the product; `/` keeps the first non-empty; `all` yields worst-first; `mergeall` yields one merged selection. DRM formats stay dropped.
- Keep a compatibility entry point so `HttpDownloadEngine` compiles unchanged (for example `select` returns the first selection, plus a `selectAll` for the full list).
- Tests: translate `test/test_YoutubeDL.py` selection cases for `,`, `all`, and `mergeall` (Unlicense notice) into Kotlin tests using synthetic format lists, and pin that the D4 specs still yield the same single `Single`/`Merge`/`None`.
- Update the `YoutubeDL.py` scope in `port/manifest.json` to name `,` lists, `all`, `mergeall`, and multiple simultaneous streams. Rerun `:tools:port-manifest:run` and `:tools:port-manifest:check`.

## Acceptance criteria

- [x] `FormatSpec` tests cover `all` and `mergeall`, alone and inside `,`, `/`, `+`, and `()`.
- [x] A comma list returns every child's selection; `+` returns every video/audio pair; `all` returns every non-DRM format best-first; `mergeall` returns one merged selection over every usable stream.
- [x] A test pins that the D4 specs (no `,`/`all`/`mergeall`) still yield one `Single`/`Merge`/`None`, so the engine sees no behavior change.
- [x] The `YoutubeDL.py` manifest scope names the new coverage, and `:tools:port-manifest:check` is green.
- [x] `:shared:core:jvmTest` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No new site, no engine behavior change, and no commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Format selection landed:** `FormatSpec.Single` carries the exact-lowercase `all` and `mergeall` keyword atoms. `FormatSelector.selectAll` returns every selection the way upstream's selector generator does: `,` and `()` concatenate their children, `/` keeps the first non-empty child, `+` is the left-outer `itertools.product`, `all` yields every non-DRM format best-first, and `mergeall` yields one `Selection.MergeAll` over every usable stream best-first. `select` stays the one-selection view the D4 engine consumes. Semantics re-read from `YoutubeDL.py` at the pin (`_build_selector_function`, `_check_formats`).
- **Engine seam:** `resolveSelection` gained the exhaustive `Selection.MergeAll` branch, which returns a typed `FormatResolution.Unsupported` ("more than two streams merged at once"). Existing `Single`/`Merge`/`None` specs resolve exactly as before.
- **One test was corrected during verification:** upstream `bv*+ba` yields exactly one pair (each side picks its best), so `mergeProducesEveryVideoAudioPair` now uses `all[vcodec!=none]+all[acodec!=none]` for several pairs, while `aMergeNodeYieldsMerge` and `d4SpecsStillYieldExactlyOneSelection` pin the single-pair D4 behavior.
- **Commands:** `./gradlew --console=plain :tools:port-manifest:run :tools:port-manifest:check` → BUILD SUCCESSFUL; `port-manifest: ok — 1,751 upstream classes, 0 ported, 5 partial, 0 planned, 1,746 not started`, and the regenerated `YoutubeDL.py` row in `vault/01-product/Ytdlp-equivalence.md` names the `,`/`all`/`mergeall`/`+` coverage and `T-133`. `./gradlew --console=plain :shared:core:jvmTest` → BUILD SUCCESSFUL, 534 tests, 0 failures, 0 errors (17 in `FormatSelectorTest`, 10 in `FormatSpecTest`). `./gradlew --console=plain :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL. Combined rerun of all four commands → BUILD SUCCESSFUL.
- No new site, no engine behavior change for existing specs, no vendored yt-dlp, and no commit. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).
