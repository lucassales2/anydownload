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

- [ ] `./gradlew :apps:desktop:test -PytDlpOracle=true` compares the YouTube case and passes on 2026.08.19, or fails with a redacted field diff.
- [ ] Without the property, or with a different `yt-dlp` version, the test is skipped and says why.
- [ ] No test output contains a `googlevideo` URL (asserted).

## Evidence / notes

Not started.
