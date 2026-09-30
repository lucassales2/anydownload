---
id: T-136
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, extractors]
---

# T-136 — Helpers the generic slices call

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Only the `common.py` / `_utils.py` helpers that T-137 and T-138 call are translated, and each one is named in `port/manifest.json`. The rest of `common.py` stays untranslated. Covers E-03.

## Dependencies

- [T-135](T-135-Fragment-concurrency.md) — Done 2026-09-29.

## Context the next session needs

- `ExtractorUtils.kt` already has `searchRegex` (`_search_regex`), `htmlSearchMeta` (`_html_search_meta`), `parseJson`, `traverse`, `urlOrNone`, `intOrNone`, `floatOrNone`, `strOrNone`, `unescapeHtml`, `parseCodecs`, `mimetype2ext`, `parseIso8601`, `unifiedStrdate`, and `parseFilesize`.
- T-137 (embeds/iframes/JSON-LD/meta refresh) and T-138 (m3u8/mpd discovery) need the missing pieces. The JSON-LD path, for example, leans on `_search_json_ld` / `_yield_json_ld` and `js_to_json`; the meta-refresh and URL paths may add small regex helpers. The exact call list is fixed by those two task notes before this one translates anything.
- Manifest entries to extend: `common.py` and `_utils.py`, each with a scope that names the translated functions.

Files: `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/ExtractorUtils.kt` (or a small adjacent file), `port/manifest.json`.

## Work

- Inventory the helpers T-137 and T-138 call by re-reading the upstream paths at the pin. Do not copy or commit upstream source.
- Translate only those functions, with the Unlicense header and the pin.
- Unit-test each helper with fixture strings that mirror upstream cases.
- Update the `common.py` and `_utils.py` scopes in `port/manifest.json` to name every newly translated function, then rerun `:tools:port-manifest:run` and `:tools:port-manifest:check`.
- Do not translate the rest of `common.py` and do not vendor yt-dlp.

## Acceptance criteria

- [x] Each newly translated helper has a name in the manifest scope and a passing unit test.
- [x] No helper is inlined untested and unnamed.
- [x] The manifest still records the rest of `common.py` as out of scope.
- [x] `:shared:core:jvmTest` is green; `:tools:port-manifest:check` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Translated helpers:** `_utils.py` gains `jsToJson` (`js_to_json` subset: string-aware comment handling, single/backtick quotes, unquoted keys, trailing commas, `undefined`/`void 0`, hex/octal integers, `new Date("...")`/`new X(...)`, `parseInt`; no `vars`, template interpolation, or `new Map`) and `determineExt` (`determine_ext` with the `KNOWN_EXTENSIONS` subset the HLS/DASH slice needs). `common.py` gains `JsonLd.objects` (`_yield_json_ld`: every top-level object from `application/ld+json` blocks, strict JSON first then the `jsToJson` fallback, arrays flattened) and `JsonLd.entries` (the `traverse_json_ld` `@graph` walk of `_json_ld`: top-level `@context` required, top-level `{"@context", "@graph"}` expanded); the VideoObject/interaction/chapter mapping of `_json_ld` stays out. New file `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/JsonLd.kt`.
- **Tests:** `ExtractorUtilsTest.jsToJsonNormalizesTheCommonLiteralForms` and `determineExtReadsTheTailBeforeTheQuery` (comments in and out of strings, escapes, hex/octal, `new Date`, `undefined`/`void 0`, trailing commas; extension tails before the query), plus `JsonLdTest` (block scanning in document order, a JS-literal block through the `jsToJson` fallback, `@graph` expansion, contextless block skipped, nested graphs not re-expanded).
- **Manifest:** `common.py` and `_utils.py` scopes name every new helper and the remaining limits; `JsonLd.kt` is in the `common.py` file list. `:tools:port-manifest:run`/`check` report `port-manifest: ok — 1,751 upstream classes, 0 ported, 5 partial, 0 planned, 1,746 not started`, and the generated equivalence rows show the new scopes.
- **Commands:** `./gradlew --console=plain :tools:port-manifest:run :tools:port-manifest:check :shared:core:jvmTest :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL; JVM 550 tests / 0 failures, iOS 500 tests / 0 failures.
- Two flaky test-infrastructure issues were fixed to keep the suite stable: the JDK test server in `ManifestEngineDownloadTest` now uses a daemon thread pool (a parked fragment no longer blocks the single default dispatcher), and `JavaNetChunkedDownloadTest` waits for the failure `finally` to discard the temp before asserting an empty root.
- No commit.
