---
type: guide
tags: [project, workflow]
---

# Documentation guide

[Home](../Home.md) · [Kanban](../Kanban.md) · [Roadmap](Roadmap.md)

## Open this vault

In Obsidian, choose **Open folder as vault** and select the repository's `vault/` directory. Start at `Home.md`.

The vault uses ordinary Markdown and relative links, which Obsidian indexes for backlinks and graph view. No paid Obsidian service, Dataview, or task-management service is required. Folder-based graph colors distinguish product, architecture, decisions, and tasks.

### Kanban plugin

1. In **Settings → Community plugins**, turn off Restricted mode **for this vault only** if you trust community plugins.
2. Browse for **Kanban**, plugin ID `obsidian-kanban` (original author mgmeyers; community archive repository).
3. Install and enable it, then open `Kanban.md`. Use the Kanban board-view command if it opens in Markdown mode.

Plugin reference: [community registry](https://github.com/obsidianmd/obsidian-releases/blob/master/community-plugins.json) · [source and releases](https://github.com/community-archive/obsidian-kanban).

The board contains `kanban-plugin: board` frontmatter and remains usable as plain Markdown without a plugin. Portable settings and the desired plugin ID are versioned; downloaded plugin JavaScript, themes, and workspace files are ignored. **A fresh clone still requires plugin installation.** Do not commit vendored plugin binaries.

## Board workflow

| Column | Meaning |
| --- | --- |
| Backlog | Planned work; may depend on unfinished decisions or tasks. |
| Ready | Small enough to begin; prerequisites are met. Approval/research tasks may be ready before application work is. |
| In progress | Actively being worked on. Suggested WIP limit: 2 cards per contributor. |
| Blocked | An active task cannot continue; record the blocker and next action in its note. |
| Review | Work exists; acceptance evidence or approval is pending. |
| Done | Acceptance criteria met, evidence linked, and related documentation updated. |

The **board column is the only task-status source of truth**. Task-note checkboxes track acceptance criteria, not overall status. Leave a card unchecked until Done; drag it to the appropriate column as work changes. Future GitHub Issues can link to a task, but there is no automatic synchronization.

Each task has a stable `T-NNN` ID, priority, milestone, dependencies, acceptance checklist, and evidence/notes section. `P0` is a gate or foundational requirement; `P1` is required product/parity work; `P2` is optional or exploratory. Milestone order and dependencies determine when work is eligible, not priority alone.

## Editing conventions

- Prefer one topic per note, descriptive filenames without spaces, and relative Markdown links.
- Keep frontmatter small. Use `type` and `tags`; task notes also have `id`, `priority`, and `milestone`.
- Use core **Templates → Insert template** with the configured `99-templates/` folder. Replace placeholders before publishing a new note.
- Record significant technical choices using the [ADR template](../99-templates/ADR-template.md). States are **Proposed**, **Accepted**, **Rejected**, or **Superseded**. Include approver/date only when approval actually occurs.
- Cite upstream source revisions and the date inspected. Do not describe a moving `master` branch as a tested release.
- Keep the [parity matrix](../01-product/Feature-parity.md), roadmap, task notes, and ADRs aligned.
- Before marking Done, check local links, duplicate task IDs/cards, dependencies, JSON settings, and public-data safety.

## Public-data boundary

All tracked notes are intended for a public GitHub repository. Store no personal cookies, credentials, media, internal hostnames, private URLs, or sensitive screenshots here. `vault/private/`, runtime folders, common credential files, and local Obsidian state are ignored, but ignore rules do not prevent accidental publication of secrets in another file or Git history.

Review `git diff --cached` and `git status` before pushing. If a credential is exposed, revoke/rotate it; deleting the line is not sufficient.

## Done for planning work

A planning task is done when its required artifact exists and its acceptance checks pass. Creating a proposal does **not** mean the proposed architecture or business scope has been approved. Feasibility tasks are still open until experiments produce evidence.
