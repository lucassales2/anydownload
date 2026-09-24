---
id: T-052
type: task
priority: P0
milestone: D3
tags: [task, ui]
---

# T-052 — Home is the link field

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

The idle main screen shows only the paste-link field. The app does not read the clipboard on its own and does not show the clipboard permission dialog.

## Dependencies

None. This card is first in D3.

## Context the next session needs

[App.kt](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/App.kt) shows [ClipboardPermissionDialog](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/clipboard/ClipboardPermissionDialog.kt) when `offerClipboardCheck` is true, then [ClipboardLinkWatcher](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/clipboard/ClipboardLinkWatcher.kt) polls after allow. Desktop and iOS pass `offerClipboardCheck = true`. [AddForm.kt](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/add/AddForm.kt) currently shows the full options form. Settings has a clipboard section that can turn the automatic check back on.

## Work

- The idle screen is one text field for a link. Hide the add-form options, queue chrome, and clipboard banner on that screen.
- Stop launching the permission dialog. Stop automatic clipboard reads. Remove `offerClipboardCheck` from the desktop and iOS entry points.
- Remove the settings toggle that turns the automatic check back on. Leave stored `ClipboardAccess` values alone; do not migrate them.
- The user can still type or use the OS paste into the field. The app does not call the clipboard on launch or on a timer.
- Update `AddFormUiTest` and `ClipboardWatchTest` so they no longer expect the dialog or a poll.

## Acceptance criteria

- [ ] UI tests: the idle screen shows the link field and does not show the clipboard dialog, the clipboard banner, or the add-form option controls.
- [ ] No host passes `offerClipboardCheck = true`. A launch with clipboard access still `UNKNOWN` does not open a dialog.
- [ ] `./gradlew :shared:ui:jvmTest` passes.

## Evidence / notes

Not started.
