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

- [x] Review threats for pasted URLs, option injection, filesystem paths, local cookies, and logs.
- [x] Specify on-device cookie consent, storage, deletion, and redaction. There is no app login.
- [x] Project license is MIT (owner, 2026-09-21). See the repository `LICENSE`.
- [x] Review yt-dlp, FFmpeg, and JS-runtime licenses before copying extractor code or bundling a media toolkit.
- [x] Review spotDL's MIT license before copying matcher source. Reimplementing its workflow does not require that copy.
- [x] Store publication is out of scope (owner, 2026-09-21).
- [x] Resolve Q-09: allowlisted options or a closer yt-dlp pass-through. Shell execution stays excluded.

## Evidence / notes

Evidence (2026-09-23): verified Chaquopy from upstream - `LICENSE.txt` states “Copyright (c) 2017-2025 Chaquo Ltd and contributors” (MIT); GitHub API license `spdx_id` = `MIT`; `master` `VERSION.txt` = 17.1.0; latest release tag = 17.0.0. Confirmed spotDL MIT on README. Wrote the local-engine threat table and the on-device cookie decision into [Security and licensing](../04-delivery/Security-and-licensing.md) and added the Chaquopy + embedded CPython inventory row. No extractor source was copied in this task.

Store distribution was closed by the owner on 2026-09-21. The project license is MIT as of the same day. Q-09 is allowlist (owner, 2026-09-23, ADR-006).

Owner license rule, 2026-09-23: translate Unlicense yt-dlp (and Unlicense ejs) into this MIT repo with notices. Never copy MeTube, NewPipe, or YtDlp-kt. Reimplement spotDL's workflow; copy its MIT source only after this task writes that review. D2 copies **no** extractor source.

Done on 2026-09-23:

- Local-engine threat notes (pasted URLs, option injection, paths, cookies, logs) written into [Security and licensing](../04-delivery/Security-and-licensing.md).
- On-device cookie decision recorded: consent on import, on-device storage, redaction, deletion/expiry, encryption-at-rest deferred to M3/T-018.
- License inventory rows present: yt-dlp Unlicense vs release-binary GPL, FFmpeg build-dependent LGPL/GPL, yt-dlp-ejs + JS runtime, Chaquopy MIT + embedded CPython (new row), and spotDL MIT.
- No Chaquopy adapter and no extractor files were added; T-041 may add Chaquopy after referencing these rows.
