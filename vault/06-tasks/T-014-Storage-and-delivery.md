---
id: T-014
type: task
priority: P1
milestone: M2
tags: [task, storage, platforms]
---

# T-014 — History, storage policies and artifact delivery

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md)

## Outcome

F-08/F-11–F-13 history, naming, folders, retention and device export with secure multi-artifact support.

## Dependencies

- [T-011](T-011-Durable-queue.md).
- [T-013](T-013-Media-formats.md).

## Acceptance criteria

- [ ] Implement history/errors/retry plus authorized streamed artifact open/download/export and separate device-transfer state.
- [ ] Define video/audio/temp/state roots, custom/default folders and suggestions/exclusions, safe prefixes/templates, collisions and long filenames.
- [ ] Separate record removal, server file deletion and local deletion; implement quotas/disk checks and explicit auto-clear/retention semantics.
- [ ] Test traversal/symlink/Unicode paths, multi-output jobs, Range/large-file memory, expired auth, disk full and per-platform export constraints.
- [ ] Document M2 MVP coverage and remaining parity work.

## Evidence / notes

Not started. A server cannot generally delete an already downloaded file from a browser user's filesystem.
