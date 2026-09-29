---
id: T-113
type: task
priority: P0
milestone: D9
tags: [task, metro, docs]
---

# T-113 — Metro migration plan and prompt

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

The Metro migration is written down before any build change: ADR-013 proposes the graph and ViewModel design, the loop prompt that drives T-114–T-122 lives in the project notes, every task has a note with dependencies and acceptance, and the board carries the `#metro` cards.

## Dependencies

None. This task must be Done before T-114 starts.

## Work

- Write [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) with status proposed. It records the current duplication (five copies of the production extractor list, double `DesktopSpotifyTokenStore`), the screen-owned queue state, the Metro `1.4.5` / Kotlin `2.4.20` compatibility finding, the four host graphs, `SharedEngineBindings`, and the MetroX `QueueViewModel`.
- Copy the owner's prompt to [Metro-Loop-prompt](../00-project/Metro-Loop-prompt.md) with the same front matter and context as [Phase-8-Loop-prompt](../00-project/Phase-8-Loop-prompt.md).
- Write task notes T-114 through T-122. Each note carries outcome, dependencies, work, acceptance, and an Evidence section.
- Add the `#metro` cards to the board. T-113 and the task notes are the only change: no Gradle, Kotlin, source, or behavior change.

## Acceptance criteria

- [x] ADR-013 exists with status proposed and names the T-114–T-122 plan.
- [x] `vault/00-project/Metro-Loop-prompt.md` carries the prompt with the task list, dependencies, rules, and loop.
- [x] T-114 through T-122 notes exist with outcome, dependencies, acceptance, and Evidence sections.
- [x] The `#metro` cards T-113 through T-122 are on the board; T-114 through T-122 are in Backlog.
- [x] No Gradle, Kotlin, or source change was made in this task.
- [x] The D8 notes and D8 cards were not edited.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Docs written:** `vault/03-decisions/ADR-013-Metro-graphs-and-viewmodels.md` (proposed), `vault/00-project/Metro-Loop-prompt.md`, and `vault/06-tasks/T-114` through `T-122`.
- **Board:** ten `#metro` cards added; T-114–T-122 left in Backlog, T-113 moved to Done. No D8 card was moved or edited.
- **Compatibility check:** `curl -sS https://zacsweers.github.io/metro/latest/compatibility/` on 2026-09-29 lists Kotlin `2.4.20` for Metro `1.2.0` and newer; the latest stable tag from the GitHub releases API is `1.4.5` (2026-09-24). T-114 re-checks before pinning.
- **No Gradle change:** only `vault/` files were written. No source file, version catalog, or build script was touched.
- **Prompt copied:** the owner's prompt text is in the Prompt block of the loop-prompt note, with a short “Notes for the next session” section outside the block.
