---
id: T-062
type: task
priority: P1
milestone: D4
tags: [task, desktop, testing]
---

# T-062 — Desktop yt-dlp oracle: differential tests

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Opt-in desktop tests run the installed `yt-dlp -J` on the same public URL as the Kotlin extractor and compare normalized fields. Divergence is a test failure with a redacted diff. Covers E-28.

## Dependencies

- [T-061](T-061-Engine-extracts-selects-downloads.md) — the Kotlin `InfoDict` to compare.
- [T-033](T-033-Desktop-ytdlp-single-url.md) — the existing `CliProcess` and `PathToolProbe`.

## Context the next session needs

`ProcessBuilder` exists only under `apps/desktop`; the oracle lives in `apps/desktop/src/jvmTest`. It runs only with `-PytDlpOracle=true` and when `yt-dlp` is on `PATH` at the pinned version (`2026.08.19`); otherwise the test is skipped with the version it found. Arguments are a list, never a shell string.

## Work

- `YtDlpOracle.infoDict(url, extractorArgs)`: runs `yt-dlp -J --no-warnings --extractor-args "youtube:player_client=visionos" <url>` (matching the Kotlin client), parses JSON, and maps to a `NormalizedInfo`.
- Normalization: compare `id`, `title`, `duration`, `channel_id`, `upload_date`, `age_limit`, and the format set by `format_id` with `ext`, `vcodec`, `acodec`, `height`, `fps`, `protocol`; ignore URLs, `filesize` within a tolerance, `view_count`, and any field the Kotlin side declares unsupported. The diff prints field names and values only; format URLs are never printed.
- Cases: the public Big Buck Bunny URL from T-060 plus one public audio-only expectation. The URL list lives in the test and is the only allowed input.
- Record the oracle protocol in the equivalence note so other extractors reuse it.

## Acceptance criteria

- [x] `./gradlew :apps:desktop:test -PytDlpOracle=true` compares the YouTube case and passes on 2026.08.19, or fails with a redacted field diff.
- [x] Without the property, or with a different `yt-dlp` version, the test is skipped and says why.
- [x] No test output contains a `googlevideo` URL (asserted).

## Evidence / notes

Done 2026-09-24.

- `apps/desktop/src/jvmTest/.../engine/YtDlpOracle.kt`: runs `yt-dlp -J --no-warnings --extractor-args "youtube:player_client=visionos" <url>` through the existing `CliProcessRunner` (argument list, never a shell string), parses the JSON with kotlinx-serialization, and normalizes `id`, `title`, `channel_id`, `upload_date`, `age_limit`, `duration` (±1s), and formats keyed by `format_id` with `ext`, `vcodec`, `acodec` (missing ≡ `none`), `height`, `fps` (±0.001), and `protocol`. URLs, `filesize`, `view_count`, and storyboard-only rows are ignored. The raw document (which carries signed URLs) is parsed and discarded; stderr is drained and never surfaced; a non-zero exit is reported by code only. `normalizeKotlin(InfoDict)` maps the Kotlin side, and `diff(...)` prints field names and normalized values only.
- `YtDlpOracleTest` has two opt-in cases: the public Big Buck Bunny video and its audio-only formats. `apps/desktop/build.gradle.kts` maps `-PytDlpOracle=true` to the test system property. Without the property the cases are skipped (verified: 2 skipped with `Set -PytDlpOracle=true to run the yt-dlp differential`); with a different `yt-dlp --version` the skip message names the version it found. Both diffs assert the output contains no `googlevideo` host.
- First run found real gaps in the T-060 translation, now fixed in `YoutubeIE`: innertube `isDrc` → `-drc` and URL `xtags` `sr=1` → `-sr` format ids (matching upstream `process_format_stream`), and the watch-page `uploadDate`/`isFamilyFriendly`/`og:restrictions:age` fallbacks upstream reads with `search_meta`. New fixture tests cover both.
- Verification: `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.YtDlpOracleTest" -PytDlpOracle=true` → 2 tests, 0 skipped, 0 failures; stdout `oracle youtube: kotlinFormats=27 oracleFormats=27 diffs=0` and `oracle youtube audio-only: kotlin=10 oracle=10 diffs=0`. Without the property: 2 skipped with the opt-in reason. `:apps:desktop:test` green on rerun (one Compose UI test was flaky once and passed alone and on the full rerun); `:shared:core:jvmTest` 251 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` green; iOS/wasm test/Android/web compiles green; `:tools:port-manifest:check` green.
- The reusable oracle protocol is recorded in [Ytdlp-equivalence.md](../01-product/Ytdlp-equivalence.md) under "Differential oracle (E-28)". No manifest change: this task is test tooling, not a translated upstream module.
