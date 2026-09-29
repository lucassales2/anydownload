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

- [x] Implement history, errors, retry, and open/share of the on-device file.
- [ ] Define video/audio/temp/state roots, custom/default folders and suggestions/exclusions, safe prefixes/templates, collisions and long filenames.
- [x] Separate record removal from deleting the file; implement disk checks and explicit auto-clear/retention.
- [ ] Test traversal, symlink, and Unicode paths, multi-output jobs, large-file memory, disk full, and per-platform save limits.
- [x] Document M2 MVP coverage and remaining parity work.

## Evidence / notes

Not started as this card. [T-110](T-110-History-names-folders.md) and [T-112](T-112-Phase-8-verification.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) add names, collisions, and the remove-versus-delete split, then close this card. A browser download cannot generally be deleted later by the page.

### Closed by Phase 8 (2026-09-29)

- History, errors, retry, open/reveal exist from D1 and are wired on desktop (`openFile`/`revealFile` in `desktopGraph`); the shared history screen shows redacted errors and the three distinct actions.
- State/roots: the jobs document lives in the app state directory (desktop `jobs.json`, Android `getDir("state")`, iOS Application Support, web `localStorage`) and is not the download root (T-102/T-103). Destination folders, safe prefixes, and extractor-derived names go through `RelativePathValidator`/`ArtifactName`; collisions get ` (2)`/` (3)` suffixes and traversal is rejected (T-110). Long-filename truncation is not implemented, so this bullet stays open.
- Removal versus delete versus cancel: `removeHistory` drops the row only, `deleteArtifacts` deletes/marks files and keeps the row, `cancel` never deletes a finished file; disk write failures type `DISK_EXHAUSTED`; clear-completed lives in the shared codec (`JobDocumentStoreTest`, `removeHistoryDropsTheRowButKeepsTheFile`, `deleteArtifactsRemovesThePublishedFile`, `WebExtensionEngineTest.deleteArtifactsReportsTheBrowserLimitationAndKeepsTheRow`).
- Tests: traversal, Unicode titles, multi-output X-status jobs, large-file streaming (chunked writes), and disk full are covered; symlink handling and per-platform save limits are not separately tested, so this bullet stays open.
- M2 MVP coverage and remaining parity work are recorded in [Phase 8](Phase-8-On-device-core.md#verification-2026-09-29), [Feature parity](../01-product/Feature-parity.md), and [Ytdlp equivalence](../01-product/Ytdlp-equivalence.md) (E-14/E-15 partial).
