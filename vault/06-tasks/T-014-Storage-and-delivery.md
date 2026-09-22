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

F-08/F-11–F-13 history, naming, folders, and retention for files saved on the device.

## Dependencies

- [T-011](T-011-Durable-queue.md).
- [T-013](T-013-Media-formats.md).

## Acceptance criteria

- [ ] Implement history, errors, retry, and open/share of the on-device file.
- [ ] Define video/audio/temp/state roots, custom/default folders and suggestions/exclusions, safe prefixes/templates, collisions and long filenames.
- [ ] Separate record removal from deleting the file; implement disk checks and explicit auto-clear/retention.
- [ ] Test traversal, symlink, and Unicode paths, multi-output jobs, large-file memory, disk full, and per-platform save limits.
- [ ] Document M2 MVP coverage and remaining parity work.

## Evidence / notes

Not started. A browser download cannot generally be deleted later by the page.
