---
type: phase
status: done
milestone: D10
tags: [project, engine, download]
---

# Phase 10 — Shared downloader

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-29. 8 engineer-days.** Started after [T-120](../06-tasks/T-120-Metro-verification.md) was Done. No new site. This phase closed the partial core rows later extractors call.

## Done looks like

Format selection accepts `,` lists, `all`, and `mergeall`, and can select more than one simultaneous stream. An HTTP download can resume inside one attempt. Fragment downloads can run concurrently and skip an unavailable fragment. `GenericIE` finds embeds, iframes, JSON-LD, meta refresh, and HLS/DASH media, not only `<video>`, `<audio>`, and `<source>`. The manifest names each newly translated helper from `common.py` and `_utils.py`.

## Tasks

- [T-133](../06-tasks/T-133-Format-lists.md) — format lists and multi-stream (E-05). Depends on [T-120](../06-tasks/T-120-Metro-verification.md), Done.
- [T-134](../06-tasks/T-134-Resume-attempt.md) — resume inside one attempt (E-07). Depends on T-133.
- [T-135](../06-tasks/T-135-Fragment-concurrency.md) — fragment concurrency (E-10). Depends on T-134.
- [T-136](../06-tasks/T-136-Generic-helpers.md) — the `common.py` / `_utils.py` helpers T-137 and T-138 call (E-03). Depends on T-135.
- [T-137](../06-tasks/T-137-Generic-html-discovery.md) — generic HTML discovery. Depends on T-136.
- [T-138](../06-tasks/T-138-Generic-hls-dash.md) — generic HLS and DASH discovery. Depends on T-137.
- [T-139](../06-tasks/T-139-Phase-10-verification.md) — phase verification. Depends on T-138.

[T-123](../06-tasks/T-123-Shared-downloader.md) stays the phase card; T-139 checked its boxes and moved it to Done.

## Verification

T-139, 2026-09-29:

```
CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest :apps:android-engine-tests:test :tools:port-manifest:check
```

BUILD SUCCESSFUL in 1m 1s, 0 failures: core JVM 577, core wasm 511, core iOS 522, UI iOS 8, web wasm 6, Android engine 26. `./gradlew --console=plain :apps:desktop:cleanTest :apps:desktop:test` alone → 132 tests, 0 failures (the combined-run FFmpeg tagging flake documented in T-112/T-120). `:tools:port-manifest:check` → `port-manifest: ok — 1,751 upstream classes, 0 ported, 5 partial, 0 planned, 1,746 not started`.

**Limits.** Multi-stream selections are not executed (the engine's `MergeAll` refusal), and the compiler still emits one selection per job. There is no cross-attempt resume and no `.part` file survives a relaunch. The meta-refresh hop lives in `GenericIE`, which is still left out of the production registry; the engine's direct HTML path uses `GenericExtractor` and does not follow the hop. No new site, cookies, captions, or clips, and the desktop CLI / Android Chaquopy fall-throughs remain.

## Touch

- [FormatSelector.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/format/FormatSelector.kt) for E-05.
- The HTTP downloader for E-07 resume, left open by [T-056](../06-tasks/T-056-Http-request-port.md).
- [FragmentDownloader.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/download/FragmentDownloader.kt) for E-10.
- [GenericExtractor.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/GenericExtractor.kt). Upstream `generic.py` is 58 KB at the pin. `common.py` is 199 KB; translate the helpers this phase’s extractors and the generic subset call, and name each one in the manifest.
