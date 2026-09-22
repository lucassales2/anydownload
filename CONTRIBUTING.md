# Contributing

This repository is planning-first. The vault and task board are the stable part of the project. An **unreviewed client scaffold** exists ahead of the M0 exit gate at the owner's request; discuss architectural changes to it before treating them as approved, and do not present scaffold behavior as production support.

## Workflow

1. Read the [vault home](vault/Home.md), [open questions](vault/00-project/Open-questions.md), and [decision log](vault/03-decisions/Decision-log.md).
2. Pick a task from [Kanban](vault/Kanban.md). Confirm its dependencies and acceptance criteria before moving it to **In progress**.
3. Update the linked task note, relevant documentation, and any affected architecture decision record together.
4. Move the card to **Review** with evidence. Move it to **Done** only when the acceptance criteria are met.
5. Follow [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md): local Kotlin engine, no backend, no app login. The checked-in client scaffold is a remote-client draft. Do not extend it with a server. Copying yt-dlp or MeTube source still waits on the license note in [T-006](vault/06-tasks/T-006-Review-security-licensing.md).

## Documentation changes

- Use relative Markdown links so navigation works in both GitHub and Obsidian.
- Keep one note per task. The board column is its status; do not duplicate status in task frontmatter.
- Mark recommendations **proposed** until explicitly approved. Record evidence, dates, and upstream revisions.
- Update the feature-parity matrix when scope changes. Do not silently drop a MeTube feature or claim untested parity.
- Check links, task IDs, dependencies, frontmatter, and `.obsidian/*.json` before committing.
- Store no cookies, tokens, private URLs, account information, downloaded media, or private research in this public repository. `.gitignore` is not a security boundary.

Templates and conventions are in the [documentation guide](vault/00-project/Documentation-guide.md).

## Code reuse and license

The project license is pending. Do not copy MeTube, yt-dlp, or YtDlp-kt implementation code into this repository until [T-006](vault/06-tasks/T-006-Review-security-licensing.md) records the license obligations. See [security and licensing](vault/04-delivery/Security-and-licensing.md).

Client build commands and shared unit tests exist now; see the [README](README.md). Testing requirements remain documented in the [testing strategy](vault/04-delivery/Testing-strategy.md), and the scaffold's tests are not evidence that the M0 feasibility criteria are met.
