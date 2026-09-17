# Contributing

This repository is currently **planning-only**. Please discuss architectural changes before introducing app code or infrastructure.

## Workflow

1. Read the [vault home](vault/Home.md), [open questions](vault/00-project/Open-questions.md), and [decision log](vault/03-decisions/Decision-log.md).
2. Pick a task from [Kanban](vault/Kanban.md). Confirm its dependencies and acceptance criteria before moving it to **In progress**.
3. Update the linked task note, relevant documentation, and any affected architecture decision record together.
4. Move the card to **Review** with evidence. Move it to **Done** only when the acceptance criteria are met.
5. Keep implementation work behind the [M0 approval gate](vault/00-project/Roadmap.md). Feasibility experiments must be labeled as experiments, not production support.

## Documentation changes

- Use relative Markdown links so navigation works in both GitHub and Obsidian.
- Keep one note per task. The board column is its status; do not duplicate status in task frontmatter.
- Mark recommendations **proposed** until explicitly approved. Record evidence, dates, and upstream revisions.
- Update the feature-parity matrix when scope changes. Do not silently drop a MeTube feature or claim untested parity.
- Check links, task IDs, dependencies, frontmatter, and `.obsidian/*.json` before committing.
- Store no cookies, tokens, private URLs, account information, downloaded media, or private research in this public repository. `.gitignore` is not a security boundary.

Templates and conventions are in the [documentation guide](vault/00-project/Documentation-guide.md).

## Code reuse and license

The project license is pending. Do not copy MeTube or YtDlp-kt implementation code into this repository without resolving license obligations. See [security and licensing](vault/04-delivery/Security-and-licensing.md).

There are no build commands or automated application tests yet. Testing requirements are documented in the [testing strategy](vault/04-delivery/Testing-strategy.md).
