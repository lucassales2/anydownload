---
id: T-001
type: task
priority: P0
milestone: M0
tags: [task, planning]
---

# T-001 — Create the planning vault

[Home](../Home.md) · [Kanban](../Kanban.md) · [Documentation guide](../00-project/Documentation-guide.md)

## Outcome

A public-safe, Markdown-backed Obsidian vault, task board, root README and general planning documentation. No application scaffolding.

## Dependencies

None.

## Acceptance criteria

- [x] Vault home, product scope/parity, architecture/platforms, ADRs, roadmap, risks, security, testing and research notes exist and link correctly.
- [x] Kanban has one card per task; task IDs/dependencies/acceptance criteria are consistent and templates are usable.
- [x] Portable Obsidian settings and Kanban configuration exist; plugin binaries and personal state are ignored.
- [x] README explains planning status, opening the vault, platform constraints and pending license.
- [x] Documentation, JSON and public-data checks pass without implying app/runtime tests passed.

## Evidence / notes

Completed 2026-09-16. One-off validation checked 51 Markdown files (49 in the vault), 365 local links, 5 portable JSON settings, 25 unique task cards with acyclic dependencies, and 26 feature-parity rows. All passed. Publishable files contain documentation/configuration only; no detected credential patterns, machine-specific paths or plugin binaries.

Kanban 2.0.51 was installed locally from its upstream release and verified as Git-ignored. A fresh clone requires plugin installation; opening the vault/enabling trusted community plugins is a user step. Visual rendering has not been verified in the Obsidian GUI.

Architecture proposals and subsequent feasibility work remain unapproved/open. No application builds, engine downloads or runtime parity tests were performed.
