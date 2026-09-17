# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Status: planning only.** This repository contains an Obsidian documentation vault and a task board. No application, backend, build setup, or downloadable release exists yet.

AnyDownload is the working product name. The repository is named [`anydownlod`](https://github.com/lucassales2/anydownlod) to match the original project folder; final naming is still open.

## Product direction

Bring the capabilities of [MeTube](https://github.com/alexta69/metube) to a multiplatform client experience, using [yt-dlp](https://github.com/yt-dlp/yt-dlp) as the proposed extraction engine:

- Video, audio-only, captions, and thumbnail downloads; format, codec, and quality selection.
- Playlists, channels, batch URL import/export, persistent queues, live progress, cancellation, and retries.
- Completed-download history, file export, custom folders, and output naming templates.
- Cookies for content the user is authorized to access; global options, presets, and security-scoped overrides.
- Channel/playlist subscriptions, clipping, chapter splitting, and SponsorBlock options.
- Self-hosted operation, engine updates, and platform-specific sharing integrations.

These are **planned capabilities, not implemented features**. The [feature-parity matrix](vault/01-product/Feature-parity.md) records the reviewed MeTube baseline, delivery milestones, and deliberate differences. The first vertical slice is smaller than full parity.

“Any” means **sites supported by the installed yt-dlp version**, not every URL or guaranteed access. Availability varies with site changes, authentication, geography, and media restrictions. DRM circumvention is not a goal. Download only content you own or have permission to download, subject to applicable law and platform terms.

## Platform strategy — proposed

| Target | Initial execution model | Later possibilities / constraints |
| --- | --- | --- |
| Android | Kotlin/Compose client → remote engine → device export | Local engine requires a separate packaging, background-work, and licensing spike. |
| iOS | Kotlin/Compose client → remote engine → Files/share export | No standalone yt-dlp CLI assumed; sandbox, background-transfer, and App Store review constraints apply. |
| Desktop | Compose/JVM client on Windows, macOS, and Linux → remote engine | Optional local yt-dlp adapter after feasibility work. |
| Web | Kotlin web client → remote engine → browser download | Compose/Wasm feasibility must be validated; browser cannot launch a native Python/yt-dlp process. |

Shared Kotlin domain logic and networking are proposed. The UI/toolchain and backend are not finalized. Remote-first execution is a recommendation, **not an approved requirement**.

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## Documentation

| Start here | Purpose |
| --- | --- |
| [Vault home](vault/Home.md) | Documentation index and current priorities |
| [Kanban board](vault/Kanban.md) | Task status; each card links to acceptance criteria and dependencies |
| [Product brief](vault/01-product/Product-brief.md) | Goals, audience, scope, and success criteria |
| [MeTube feature parity](vault/01-product/Feature-parity.md) | Traceable feature inventory |
| [Architecture](vault/02-architecture/Architecture.md) | Proposed system boundaries and alternatives |
| [Platform matrix](vault/02-architecture/Platform-matrix.md) | What can be shared and what must be platform-specific |
| [Roadmap](vault/00-project/Roadmap.md) | Milestones and exit gates, without invented deadlines |
| [Open questions](vault/00-project/Open-questions.md) | Decisions needed before implementation |
| [Security and licensing](vault/04-delivery/Security-and-licensing.md) | Trust boundaries, privacy, dependency licenses, and distribution risks |

## Open the Obsidian vault

1. Clone the repository:
   ```sh
   git clone https://github.com/lucassales2/anydownlod.git
   cd anydownlod
   ```
2. In [Obsidian](https://obsidian.md), choose **Open folder as vault** and select this repository's **`vault/`** folder, not the repository root.
3. Open **`Home.md`** for the documentation index.
4. For the visual task board, go to **Settings → Community plugins**, enable community plugins for this vault if you trust them, and install/enable **Kanban** (`obsidian-kanban`, originally by mgmeyers, now maintained under the Obsidian community archive).
5. Open **`Kanban.md`**. If it opens as text, use the command palette's Kanban board-view command.

The board also works as an ordinary Markdown checklist on GitHub or in any editor. The checked-in plugin list does **not** install plugin binaries on a fresh clone. Plugin code and machine-specific workspaces are intentionally excluded from Git.

See the [documentation guide](vault/00-project/Documentation-guide.md) for board rules, templates, and privacy precautions.

## Repository layout

```text
README.md
CONTRIBUTING.md
vault/                     # Open this folder as an Obsidian vault
  Home.md
  Kanban.md
  00-project/              # Roadmap, questions, documentation workflow, glossary
  01-product/              # Product scope, parity, user journeys
  02-architecture/         # Platform constraints, domain, API proposal
  03-decisions/            # Architecture decision records (proposed until approved)
  04-delivery/             # Testing, risks, security, licensing
  05-research/             # Dated upstream findings and pinned references
  06-tasks/                # One note per task
  99-templates/            # Task, ADR, and research templates
  .obsidian/               # Portable vault settings only
```

There are no app build/run commands yet. Start with the feasibility tasks on the [board](vault/Kanban.md), then approve the M0 exit gate before creating application scaffolding.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

**Project license: not selected yet.** Public visibility does not grant a general open-source license. Licensing is an explicit planning task before code reuse or distribution. No upstream implementation or application binaries are included. MeTube, yt-dlp, YtDlp-kt, and Obsidian are independent projects; this project is not affiliated with them.
