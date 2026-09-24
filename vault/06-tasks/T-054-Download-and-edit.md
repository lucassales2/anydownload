---
id: T-054
type: task
priority: P0
milestone: D3
tags: [task, ui]
---

# T-054 — Download and collapsible edit

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

The metadata preview has two actions: Download, and Edit download. Edit expands and collapses. The panel starts collapsed. Expanded content is video or audio, quality, and format.

## Dependencies

- [T-053](T-053-Validate-then-preview.md).

## Work

- **Download** starts the job with the options currently selected (defaults when the panel was never opened).
- **Edit download** toggles one panel. Collapsed hides the choices. Expanded shows media type (video or audio), quality, and format. Those choices feed the same allowlist options Download uses.
- Captions, clips, cookies, destination, and the disabled custom yt-dlp JSON stay out of this panel.
- Do not invent formats the preview metadata did not return. Unknown quality stays unknown.

## Acceptance criteria

- [x] UI tests: the panel is collapsed on open; Edit download reveals video/audio, quality, and format; collapsing hides them; Download submits one job and does not require the panel to have been opened.
- [x] The panel does not show captions, clips, cookies, or custom JSON.
- [x] `./gradlew :shared:ui:jvmTest` passes.

## Evidence / notes

Done 2026-09-24.

- `PreviewScreen` now offers Download plus an Edit download toggle (new strings `preview_edit` / `preview_edit_hide`, EN + pt-BR). The toggle expands one panel (`preview-edit-panel`, new `PreviewEditPanel.kt`) that starts collapsed and never appears while metadata is still loading.
- The panel is a strict allowlist bound to the same `AddFormPresenter` the field uses: media type (video or audio), then quality and format. Video: quality (Best/Worst/2160…360) and format (Auto/MP4/iOS-compatible container profile). Audio: format (M4A/MP3/Opus/WAV/FLAC) and quality (bitrate Auto/128/192/256/320, shown only for lossy containers). Captions, clips, cookies, destination, and the disabled custom yt-dlp JSON are not in the panel.
- Download (`preview-download`) submits one job through `addForm.submit()` with whatever the panel selected (allowlist defaults when it was never opened). `ChoiceRow`, the option lists, and the display names in `AddForm.kt` became internal so the panel shares the exact same choices.
- UI tests (`AddFormUiTest`): `editPanelCollapsesAndExpandsOnThePreview` (collapsed on open, expand shows the three controls, audio switch keeps them, out-of-scope labels absent, collapse hides them), `downloadUsesTheEditedAudioChoices` (Audio + M4A + bitrate 192 reach the job request), and the existing typing test now also asserts Download never opens the panel submits one job with VIDEO/Best/AUTO defaults.
- Verification: `./gradlew :shared:ui:jvmTest` — 71 tests, 0 failures; `:apps:desktop:compileKotlin`, `:apps:android:compileDebugKotlin`, `:shared:ui:compileKotlinIosSimulatorArm64`, `:shared:ui:compileKotlinWasmJs` all BUILD SUCCESSFUL.
