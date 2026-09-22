---
id: T-006
type: task
priority: P0
milestone: M0
tags: [task, security, licensing]
---

# T-006 — Review security and licensing

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security plan](../04-delivery/Security-and-licensing.md) · [Risk register](../04-delivery/Risk-register.md)

## Outcome

License obligations and local threat controls before extractor source is copied into the app. Store distribution is out of scope.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [ ] Review threats for pasted URLs, option injection, filesystem paths, local cookies, and logs.
- [ ] Specify on-device cookie consent, storage, deletion, and redaction. There is no app login.
- [ ] Review yt-dlp, FFmpeg, and JS-runtime licenses; obtain an owner decision on the project license before adding a LICENSE or copying extractor code.
- [x] Store publication is out of scope (owner, 2026-09-21).
- [ ] Resolve Q-09: allowlisted options or a closer yt-dlp pass-through. Shell execution stays excluded.

## Evidence / notes

Store distribution was closed by the owner on 2026-09-21. License inventory and Q-09 are still open. Existing security notes are a preliminary inventory, not a legal opinion.
