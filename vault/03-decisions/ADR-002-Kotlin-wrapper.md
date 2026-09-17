---
id: ADR-002
type: adr
status: proposed
created: 2026-09-16
tags: [architecture, decisions, dependencies]
---

# ADR-002 — YtDlp-kt is a reference, not the common engine

[Decision log](Decision-log.md) · [Upstream review](../05-research/Upstream-review.md) · [Security and licensing](../04-delivery/Security-and-licensing.md)

## Context and observed facts

At reviewed commit `e1947a1f7ad0e81aa8825b5958c585b1d338a36f`, YtDlp-kt:

- Uses `org.jetbrains.kotlin.jvm`, not the Multiplatform plugin/target setup.
- Imports `java.io.File`, uses `ProcessBuilder`/`Runtime.exec`, and expects an external yt-dlp/Python executable.
- Constructs a command string and splits it on spaces; argument quoting, lifecycle, streaming progress and cancellation need independent review.
- Is archived on GitHub, with GPL-3.0 license metadata. It does not bundle a universal mobile/browser runtime.

These findings come from the upstream build/source, not from its Kotlin name or README example alone.

## Proposed decision

Do **not** add YtDlp-kt to common KMP code or make it a mandatory engine dependency. Keep it as a researched reference. Evaluate an owned typed adapter to official yt-dlp APIs/structured CLI output for the chosen server; assess any JVM/local use separately.

## Alternatives

- Adopt/fork the wrapper for desktop/server only: may save initial wrapping work, but adds maintenance, correctness and GPL review responsibilities.
- Direct JVM process adapter: control argument arrays and lifecycle, still requires runtime packaging and output contract work.
- Python worker using yt-dlp hooks: closer to upstream API, but adds private IPC and a Python runtime.
- MeTube integration: avoid writing much engine behavior, but verify API/transport and licensing independently.

## Consequences

We avoid a false cross-platform assumption and an archived dependency by default, at the cost of choosing and testing our own engine boundary. No claim is made that process separation or a reimplementation automatically settles licensing obligations.

## Gate

[T-005](../06-tasks/T-005-Choose-backend-engine.md) and [T-006](../06-tasks/T-006-Review-security-licensing.md) must record adoption/rejection, maintenance plan, dependency licenses and evidence. **Status remains Proposed.**
