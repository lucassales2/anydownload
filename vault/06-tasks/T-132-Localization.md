---
id: T-132
type: task
priority: P2
milestone: unscheduled
tags: [task, i18n, ux]
---

# T-132 — Localize the UI for 50 locales

[Home](../Home.md) · [Kanban](../Kanban.md)

## Outcome

Every user-facing string in the shared Compose UI resolves for the 50 App Store languages below. The app follows the system locale on Android, iOS, desktop, and web. English (U.S.) stays the source language. Store listing copy stays out of scope; [Home](../Home.md) already keeps store publication out of scope.

The catalog today is `shared/ui/src/commonMain/composeResources/values/strings.xml` (about 100 strings and 3 plurals) plus a matching `values-pt-rBR/strings.xml`. Screens already read those resources through `stringResource` and [UiText](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/i18n/UiText.kt). JVM UI tests pin `user.language=en` and `user.country=US` so a host locale cannot change assertions.

## Dependencies

None. This does not wait on Metro or the engine schedule, and it does not jump ahead of an in-progress D9 card. New UI strings landed by other tasks must follow the catalog rule in this note once the parity test exists.

## Locale map

Compose Multiplatform 1.12 resolves `composeResources` with Android-style qualifiers. `values/` is the default and remains English (U.S.), so there is no `values-en-rUS` copy.

| # | Language | Directory | Notes |
| --- | --- | --- | --- |
| 1 | Arabic | `values-ar` | RTL. Plurals: zero, one, two, few, many, other. |
| 2 | Bangla | `values-bn` | Confirm desktop and web font fallback. |
| 3 | Catalan | `values-ca` | |
| 4 | Chinese (Simplified) | `values-zh-rCN` and `values-b+zh+Hans` | Same text in both. Script folder covers Hans with a non-CN region. |
| 5 | Chinese (Traditional) | `values-zh-rTW`, `values-zh-rHK`, and `values-b+zh+Hant` | Same text in all three. HK is Traditional for users whose region is Hong Kong. |
| 6 | Croatian | `values-hr` | Plurals: one, few, other. |
| 7 | Czech | `values-cs` | Plurals: one, few, many, other. |
| 8 | Danish | `values-da` | |
| 9 | Dutch | `values-nl` | |
| 10 | English (Australia) | `values-en-rAU` | Spelling overrides only where they differ from `values/`. |
| 11 | English (Canada) | `values-en-rCA` | Same as Australia. |
| 12 | English (U.K.) | `values-en-rGB` | Same as Australia. |
| 13 | English (U.S.) | `values/` | Source of truth. |
| 14 | Finnish | `values-fi` | |
| 15 | French | `values-fr` | |
| 16 | French (Canada) | `values-fr-rCA` | Falls back to `values-fr` for any key left out. |
| 17 | German | `values-de` | |
| 18 | Greek | `values-el` | |
| 19 | Gujarati | `values-gu` | Font fallback. |
| 20 | Hebrew | `values-he` and `values-iw` | RTL. Same text in both. `iw` is the legacy Android code still seen on some API 26 devices. Plurals: one, two, many, other. |
| 21 | Hindi | `values-hi` | Font fallback. |
| 22 | Hungarian | `values-hu` | |
| 23 | Indonesian | `values-id` and `values-in` | Same text in both. `in` is the legacy Android code. |
| 24 | Italian | `values-it` | |
| 25 | Japanese | `values-ja` | Plurals: other only. |
| 26 | Kannada | `values-kn` | Font fallback. |
| 27 | Korean | `values-ko` | Plurals: other only. |
| 28 | Malay | `values-ms` | |
| 29 | Malayalam | `values-ml` | Font fallback. |
| 30 | Marathi | `values-mr` | Font fallback. |
| 31 | Norwegian | `values-nb` and `values-no` | Bokmål. Same text in both. Apple’s “Norwegian” is `nb`; some Android devices still report `no`. |
| 32 | Odia | `values-or` | Font fallback. |
| 33 | Polish | `values-pl` | Plurals: one, few, many, other. |
| 34 | Portuguese (Brazil) | `values-pt-rBR` and `values-pt` | `values-pt` matches Brazil so a device set to Portuguese with no region does not fall through to English. |
| 35 | Portuguese (Portugal) | `values-pt-rPT` | Does not inherit Brazil. |
| 36 | Punjabi | `values-pa` | Gurmukhi, left to right. Font fallback. |
| 37 | Romanian | `values-ro` | Plurals: one, few, other. |
| 38 | Russian | `values-ru` | Plurals: one, few, many, other. |
| 39 | Slovak | `values-sk` | Plurals: one, few, many, other. |
| 40 | Slovenian | `values-sl` | Plurals: one, two, few, other. |
| 41 | Spanish (Mexico) | `values-es-rMX` | |
| 42 | Spanish (Spain) | `values-es` | Also covers a device set to Spanish with no region. No separate `values-es-rES`. |
| 43 | Swedish | `values-sv` | |
| 44 | Tamil | `values-ta` | Font fallback. |
| 45 | Telugu | `values-te` | Font fallback. |
| 46 | Thai | `values-th` | Font fallback. No space between words; watch line breaks. |
| 47 | Turkish | `values-tr` | |
| 48 | Ukrainian | `values-uk` | Plurals: one, few, many, other. |
| 49 | Urdu | `values-ur` | RTL. Font fallback. |
| 50 | Vietnamese | `values-vi` | |

Languages not listed above use `one` and `other` unless the table says otherwise. Every plural must include `other`.

First implementation step is a spike that a throwaway `values-b+zh+Hans` resource resolves under Compose 1.12 on Android and desktop. If the `b+` qualifier is ignored, drop the script directories and record Simplified as `zh-rCN` only and Traditional as `zh-rTW` plus `zh-rHK`.

Alias directories (`values-iw`, `values-in`, `values-no`, `values-pt`, the Chinese duplicates) must contain the same strings as their source. The parity test treats a mismatch as a failure.

## Work

### 1. Fix the English catalog before translating

Translate from a catalog that already uses real plurals. These strings still encode English number grammar and must become `<plurals>` (and their call sites must use `pluralStringResource` / `UiText.quantity`):

- `copied_urls` — `Copied %1$d URL(s)`
- `preview_views` — `%1$s views`
- `preview_playlist_count` — `Playlist · %1$s items`
- `preview_formats_needing_js` — `%1$s more formats…`
- `remove_many_title` — `Remove %1$s entries from history?`

Update `values-pt-rBR` in the same change. Leave placeholders (`%1$s`, `%1$d`, `%2$s`) in the same order in every translation. Word order changes around the placeholder; the index does not.

Do not translate:

- `app_name` (`AnyDownload`)
- Product and tool names: Spotify, YouTube, yt-dlp, ffmpeg, JavaScript
- Format tokens such as `%(title)s`, schemes `http://` and `https://`, and the `HH:MM:SS` example
- File extensions, codecs, and container names when they appear as values rather than sentences

Durations and counters that Kotlin already formats with `padStart` stay locale-neutral. This task does not introduce a date or number formatter.

### 2. Add the resource directories

Add one `strings.xml` per directory in the map. English (Australia, Canada, U.K.) may omit keys that match `values/`; every other locale gets every string and every plural. French (Canada) and Spanish (Mexico) may omit a key only when the parent language folder (`values-fr`, `values-es`) has the same translation.

Keep Android `apps/android/src/main/res/values/strings.xml` as the launcher label `AnyDownload` only. Compose resources do not feed the manifest label, and the name is not translated.

### 3. Follow the system locale on each host

Compose already picks `composeResources` from the host locale. There is no in-app language picker in this task. `App` does not set `LayoutDirection`, so Arabic, Hebrew, and Urdu follow the locale’s direction once those resources exist.

- **Android.** Add `res/xml/locales_config.xml` and `android:localeConfig` so the Android 13 per-app language list matches the map. Compose still reads the configuration locale; this file only publishes the list.
- **iOS.** Add `CFBundleLocalizations` to `apps/ios/AnyDownload/Info.plist` with the language codes from the map (`zh-Hans`, `zh-Hant`, `en-GB`, `pt-BR`, `pt-PT`, `es-MX`, `es-ES`, `fr-CA`, `nb`, and the rest). Development region stays English.
- **Desktop.** The existing `user.language` / `user.country` test pins stay. Packaging `Info.plist` keeps the display name `AnyDownload`.
- **Web.** `apps/web/src/wasmJsMain/resources/index.html` is hardcoded `lang="en"`. Set `documentElement.lang` and `dir` from the resolved locale when the app starts. `dir` is `rtl` for `ar`, `he`/`iw`, and `ur`.

### 4. Guard the catalog

Add a JVM test that parses the XML and does not start Compose:

- Every non-English directory contains the same string and plural names as `values/`, except the allowed English-region and parent-language omissions above.
- Format specifiers on each key match the source in index and type.
- Each plural includes the CLDR quantities for that language, always including `other`.
- Alias directories match their source byte for byte on the resource body.
- `app_name` is `AnyDownload` in every file.

Existing UI tests stay on `en` / `US`. Add one Compose smoke test that resolves a known Portuguese (Brazil) string and one that resolves a known Arabic string with right-to-left layout. That is enough to prove qualifier selection; the XML test covers the other locales.

### 5. Check fonts and layout

On desktop and web, open one screen in Bangla, Gujarati, Hindi, Kannada, Malayalam, Marathi, Odia, Punjabi, Tamil, Telugu, Thai, Chinese, Japanese, Korean, Arabic, Hebrew, and Urdu. System fallback is the expected font source. Bundle a Noto subset only for a script that renders as boxes, and only that script.

Walk the home, queue, history, and settings screens in Arabic. Fix any `Alignment.Start`/`End` misuse or hardcoded left/right that pins the layout to English. Longer German, Finnish, and Russian labels must wrap inside the existing buttons and tiles rather than ellipsize a whole action.

### 6. Fill translations

English regions are edited by hand from `values/`. Brazilian Portuguese already exists and is updated in step 1.

The other locales are machine-translated into the XML files and checked in. A one-line XML comment at the top of each generated file says `machine-translated`. Native review is a follow-up, not a gate for this task. Short UI strings and Arabic/Slavic plurals are the likely review targets.

After this task, a new string lands in `values/strings.xml` first. The parity test fails until the other complete locales have it. English-region files pick it up only when the wording differs.

## Acceptance criteria

- [ ] The five English count strings are plurals, and `values-pt-rBR` matches the new keys.
- [ ] The locale map is implemented, including the Chinese qualifier result from the spike.
- [ ] The XML parity test passes, and the Portuguese and Arabic Compose smokes pass.
- [ ] Android locale config, iOS `CFBundleLocalizations`, and the web `lang`/`dir` update are in place.
- [ ] Font and Arabic layout notes are recorded in Evidence, including any Noto subset that was added.
- [ ] Kanban stays the status source. This card does not move ahead of in-progress D9 work unless that work is finished.

## Evidence / notes

Not started. Planned 2026-09-29 from the current catalog: shared Compose strings, `values` plus `values-pt-rBR`, UI tests pinned to `en-US`, no `LayoutDirection` override, web `index.html` locked to `lang="en"`, iOS `Info.plist` without `CFBundleLocalizations`.
