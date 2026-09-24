---
id: T-053
type: task
priority: P0
milestone: D3
tags: [task, ui]
---

# T-053 — Validate, then metadata preview

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

Submitting the link field checks that the text is one compatible HTTP(S) URL. A bad value stays on the field with an error. A compatible URL opens the existing metadata preview. Nothing downloads yet.

## Dependencies

- [T-052](T-052-Link-only-home.md).

## Context the next session needs

Reuse `SourceUrlValidator` and `ClipboardLink.compatibleUrl`. Do not add a second URL type. [PreviewScreen.kt](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/preview/PreviewScreen.kt) already loads title, thumbnail, and metadata; open that screen. Do not start a job from the field.

## Work

- Submit accepts one HTTP(S) URL. Reject blank, non-HTTP(S), and userinfo URLs with the existing validator messages.
- A compatible URL sets the preview target and shows `PreviewScreen`.
- Pasting or typing alone does not submit and does not start a download.
- A failed preview load stays on the preview with a redacted error and a way back to the field. Do not pretend a job exists.

## Acceptance criteria

- [x] UI tests: an invalid string stays on the field with an error; `https://example.com/watch` opens the preview; the job list is unchanged.
- [x] No clipboard read was added back.
- [x] `./gradlew :shared:ui:jvmTest` passes.

## Evidence / notes

Done 2026-09-24.

- `SourceUrlValidator` now rejects userinfo in the authority (`SourceUrlError.Userinfo`), matching what `UrlPolicy` already refuses at engine level. Added resource strings `url_error_userinfo` ("This URL embeds credentials and was refused.") and `url_error_one_only` ("Enter one source URL.") in EN and pt-BR, mapped in `StringMappings.toUiText`.
- New `AddFormPresenter.validateForPreview()`: one compatible HTTP(S) URL returns it (caller opens `PreviewScreen`); blank, whitespace, non-HTTP(S), missing-host, userinfo, and multi-line inputs keep the field as it is and show the existing validator message on the status line. It never starts a job and never clears the field. `HomeScreen` routes the Download button through it, so the field no longer feeds the batch path; pasting or typing alone still does not submit.
- Failed preview stays on the preview with the redacted error and Back (existing `PreviewScreen` failure phase), and never claims a job exists.
- Tests: `SourceUrlValidatorTest.rejectsUserinfoCredentialsInTheAuthority` (plus path `@` allowed), `AddFormPresenterTest.validateForPreviewAcceptsOneCompatibleUrlAndNeverStartsAJob` and `...RejectsBlankSchemeUserinfoAndBatches`, `AddFormUiTest.invalidInputStaysOnTheFieldWithoutJobsOrPreview`, `...MoreThanOneLineStaysOnTheFieldWithoutJobs`, `...TypingAUrlAndClickingDownloadOpensThePreview` (preview opens, job list unchanged until Download is clicked).
- Verification: `./gradlew :shared:core:jvmTest :shared:ui:jvmTest` — 102 + 69 tests, 0 failures; `:apps:desktop:compileKotlin`, `:apps:android:compileDebugKotlin`, `:shared:ui:compileKotlinIosSimulatorArm64`, `:shared:ui:compileKotlinWasmJs` all BUILD SUCCESSFUL. `:shared:core:wasmJsBrowserTest` cannot launch on this machine (no `CHROME_BIN`); not a code regression.
