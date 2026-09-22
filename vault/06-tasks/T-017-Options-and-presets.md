---
id: T-017
type: task
priority: P1
milestone: M3
tags: [task, configuration, security]
---

# T-017 — Global options, presets and safe overrides

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-19/F-20 configurable options with an approved safety boundary, also supporting F-15 embedding settings.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md).

## Acceptance criteria

- [ ] Define approved typed/API option mappings; test global environment/file precedence, ordered presets, overrides and null clearing.
- [ ] Support watched config updates with validation and immutable effective options per attempt; invalid reloads preserve known-good configuration.
- [ ] Cover approved rate/proxy/archive/metadata/subtitle/postprocessing controls without exposing executable hooks or arbitrary paths/network overrides.
- [ ] Enforce safety after option merging so no layer/null value can disable mandatory protections.
- [ ] Record approved parity differences and tests for unknown/unsafe options and conflicting presets.

## Evidence / notes

Not started. Free-form yt-dlp options can execute commands. Q-09 decides allowlist versus a closer pass-through. Shell execution stays out.
