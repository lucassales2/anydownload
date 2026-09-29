---
id: T-017
type: task
priority: P1
milestone: D14
tags: [task, configuration, security]
---

# T-017 — Global options, presets and safe overrides

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-19/F-20 configurable options with an approved safety boundary, also supporting F-15 embedding settings.

## Dependencies

- [T-016](T-016-Clips-chapters-SponsorBlock.md). Phase: [Phase 14](../00-project/Phase-14-Options-archive.md). Estimate 8 engineer-days.

## Acceptance criteria

- [ ] Define approved typed/API option mappings; test global environment/file precedence, ordered presets, overrides and null clearing.
- [ ] Support watched config updates with validation and immutable effective options per attempt; invalid reloads preserve known-good configuration.
- [ ] Cover approved rate/proxy/archive/metadata/subtitle/postprocessing controls without exposing executable hooks or arbitrary paths/network overrides.
- [ ] Enforce safety after option merging so no layer/null value can disable mandatory protections.
- [ ] Record approved parity differences and tests for unknown/unsafe options and conflicting presets.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D14 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Q-09 is the allowlist. Free-form JSON stays disabled. Shell execution stays out. This phase also covers output templates, the download-archive file, proxy, rate limit, and sleep.
