---
type: phase
status: done
milestone: D11
tags: [project, engine, cookies]
---

# Phase 11 — Cookies

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 6 engineer-days planned.** Depends on D10, which is Done. Pulled [T-018](../06-tasks/T-018-Cookie-lifecycle.md) forward: cookies are an engine prerequisite, not a screen that waits until the rest of M3. Implementation prompt: [Phase 11 loop](Phase-11-Loop-prompt.md). Owner decisions in that prompt were answered 2026-09-30.

## Done looks like

The user can import, replace, and delete a Netscape cookie file on this device. Jobs store a reference, not the cookie text. Extractor requests attach the jar. Logs, history, fixtures, and this vault contain no cookie contents. The file stays in app-private storage with no app-level cipher. On desktop, an opt-in consent screen can copy Chrome, Firefox, or Safari cookies into that same file once. Browser-store reading stays out on mobile and web (E-16). Synthetic fixtures only.

## Task

[T-018](../06-tasks/T-018-Cookie-lifecycle.md). The jar attaches inside the shared HTTP helper, not in a desktop-only process argument list.

## Verification

Commands, 2026-09-30 (sequential; `org.gradle.parallel=true` makes the timing-sensitive fixture suites flaky when projects overlap):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test --rerun-tasks` — BUILD SUCCESSFUL; core 627, ui 105, desktop 142 (15 live-only skipped), android-engine 41; 0 failures.
- `./gradlew --no-parallel :apps:android:compileDebugKotlin :shared:ui:compileKotlinIosSimulatorArm64 :apps:web:compileKotlinWasmJs` — BUILD SUCCESSFUL.

Landed scope: shared Netscape parser/jar (host, path, secure, expiry) and the trusted `HttpRequest.cookie` attachment for extractor, media, manifest, resume, and fragment requests; app-private `cookies.txt` with Not configured / Configured / Error status on desktop, Android, and iOS; desktop one-shot Chrome/Firefox/Safari snapshot behind a separate consent dialog; Android Auto Backup and iOS backup exclusions; E-16 updated. Limits recorded: no app-level cipher; web stores no cookie file (extension gap); mobile and web never read a browser database; desktop browsers other than Chrome/Firefox/Safari and Windows Chrome DPAPI are gaps; cookies do not guarantee access or authorize an unpermitted download; live-network and iOS-simulator runs remain fixture-only.
