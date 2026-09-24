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
- [x] Project license is MIT (owner, 2026-09-21). See the repository `LICENSE`.
- [ ] Review yt-dlp, FFmpeg, and JS-runtime licenses before copying extractor code or bundling a media toolkit.
- [ ] Review spotDL's MIT license before copying matcher source. Reimplementing its workflow does not require that copy.
- [x] Store publication is out of scope (owner, 2026-09-21).
- [x] Resolve Q-09: allowlisted options or a closer yt-dlp pass-through. Shell execution stays excluded.

## Evidence / notes

Store distribution was closed by the owner on 2026-09-21. The project license is MIT as of the same day. Q-09 is allowlist (owner, 2026-09-23, ADR-006).

Owner license rule, 2026-09-23: translate Unlicense yt-dlp (and Unlicense ejs) into this MIT repo with notices. Never copy MeTube, NewPipe, or YtDlp-kt. Reimplement spotDL's workflow; copy its MIT source only after this task writes that review. D2 copies **no** extractor source.

Still open on this task (do these in D2 before Chaquopy lands):

- Write the local-engine threat notes (pasted URLs, option injection, paths, cookies, logs) into [Security and licensing](../04-delivery/Security-and-licensing.md).
- Cookie consent, on-device storage, deletion, and redaction. Encryption-at-rest can be “later, M3” if stated.
- Record yt-dlp Unlicense vs release-binary GPL, FFmpeg build-dependent LGPL/GPL, yt-dlp-ejs + JS runtime, Chaquopy MIT + embedded CPython, and spotDL MIT. This is the inventory, not a lawyer opinion.
- Do not add Chaquopy or copy extractor files until those rows exist.
