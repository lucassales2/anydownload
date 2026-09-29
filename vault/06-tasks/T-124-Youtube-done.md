---
id: T-124
type: task
priority: P0
milestone: D12
tags: [task, engine, youtube]
---

# T-124 — YouTube done at the pin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 12](../00-project/Phase-12-Youtube-done.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The 21 `Youtube*` classes at pin `2026.08.19` are accounted for in the manifest. Estimate 15 engineer-days.

## Dependencies

- [T-018](T-018-Cookie-lifecycle.md).

## Acceptance criteria

- [ ] Remaining innertube clients the pin uses, playlist continuations, channels, mixes, and `--playlist-items` are translated or marked Partial with a reason.
- [ ] A PO-token provider interface exists. Formats that need a token are dropped until a provider is configured.
- [ ] Subtitle tracks and chapter metadata are on the info dict. Writing those files is T-015 and T-016.
- [ ] Live YouTube and live HLS fail only for the cases the phase note still lists as out.
- [ ] The default playlist cap stays 50. A higher cap requires an explicit user limit.
- [ ] Harness cases pass. The desktop oracle diffs normalized fields only. No cookie, token, or signed media URL is committed.
- [ ] `:tools:port-manifest:check` passes and the equivalence rows for YouTube name this task.

## Evidence / notes

Not started. iOS simulator reachability stays a recorded limit, as in T-074. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).
