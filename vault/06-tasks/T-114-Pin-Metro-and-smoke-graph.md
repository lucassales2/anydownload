---
id: T-114
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-114 — Pin Metro 1.4.5 and prove the plugin

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

The Metro Gradle plugin is pinned at the newest stable release whose compatibility page lists Kotlin 2.4.20, declared with `apply false` on the root, and applied on `shared/core`, `shared/ui`, `apps/android`, `apps/desktop`, and `apps/web`. A smoke graph in `shared/core` commonMain compiles for JVM, Wasm, and iOS, and the Android app still assembles. Kotlin stays 2.4.20.

## Dependencies

- [T-113](T-113-Metro-migration-plan.md).

## Context the next session needs

- Checked 2026-09-29: the Metro compatibility page lists Kotlin `2.4.20` for Metro `1.2.0` and newer; the newest stable release then is `1.4.5` (2026-09-24). Re-check the page and the releases API on the day the task runs and pin the newest stable that lists `2.4.20`. Record the page URL, version, and date in Evidence.
- The plugin id is `dev.zacsweers.metro`. The Metro Gradle plugin adds the runtime dependencies to each Metro-enabled compilation; do not add `dev.zacsweers.metro:runtime` by hand unless the task Evidence says otherwise.
- The rule “`@DependencyGraph` only in the host source set” applies to the production graphs (T-116–T-119). This task's smoke graph is a temporary plugin proof in `shared/core` commonMain; T-115 deletes it when `SharedEngineBindings` lands.
- If the plugin fails on Kotlin `2.4.20`, move the card to Blocked with the failure output and stop. Do not downgrade Kotlin.

## Work

- Add `metro = "1.4.5"` to `gradle/libs.versions.toml` and the `dev.zacsweers.metro` plugin alias.
- Declare the plugin `apply false` in the root `build.gradle.kts`, then apply it in `shared/core`, `shared/ui`, `apps/android`, `apps/desktop`, and `apps/web`. Do not apply it in `shared/network`, `tools/port-manifest`, or `apps/android-engine-tests`.
- Add the smoke graph in `shared/core` commonMain: a small `@DependencyGraph` with one `@Provides` accessor, plus a `jvmTest` that creates it with `createGraph` and reads the value. Keep it out of the production package surface.
- Change nothing else: no Kotlin, AGP, Compose, coroutines, or behavior change.

## Acceptance criteria

- [x] Metro is pinned at the newest stable whose compatibility page lists Kotlin 2.4.20; page URL, version, and date are in Evidence.
- [x] The plugin is declared `apply false` on the root and applied on exactly `shared/core`, `shared/ui`, `apps/android`, `apps/desktop`, and `apps/web`.
- [x] The smoke graph compiles in `shared/core` commonMain and a test creates it.
- [x] `./gradlew :shared:core:jvmTest :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :apps:android:assembleDebug` passes.
- [x] Kotlin stays `2.4.20`.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Pin:** Metro `1.4.5` (latest stable, published 2026-09-24 per `https://api.github.com/repos/ZacSweers/metro/releases`). The compatibility page `https://zacsweers.github.io/metro/latest/compatibility/` (checked 2026-09-29) lists `2.4.20 | 1.2.0 -`, so every stable from `1.2.0` on supports Kotlin 2.4.20 and `1.4.5` is the newest. Version catalog: `metro = "1.4.5"`, plugin alias `metro = { id = "dev.zacsweers.metro", version.ref = "metro" }`.
- **Placement:** `alias(libs.plugins.metro) apply false` on the root; applied in `shared/core/build.gradle.kts`, `shared/ui/build.gradle.kts`, `apps/android/build.gradle.kts`, `apps/desktop/build.gradle.kts`, and `apps/web/build.gradle.kts`. Not present in `shared/network`, `tools/port-manifest`, or `apps/android-engine-tests`. `grep -rn "libs.plugins.metro" --include=build.gradle.kts` shows exactly those six lines.
- **Smoke graph:** `anydownload` (`@DependencyGraph` with one `@Provides` `String` accessor) and `anydownload` (`createGraph<MetroSmokeGraph>()`). Temporary; T-115 deletes both when `SharedEngineBindings` replaces the copied lists.
- **Verification:** `./gradlew --console=plain :shared:core:jvmTest` → BUILD SUCCESSFUL in 1m 26s; the `MetroSmokeGraphTest[jvm]` result XML records 1 test, 0 failures. Then `./gradlew --console=plain :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :apps:android:assembleDebug` → BUILD SUCCESSFUL in 1m 21s (Android packaging logged the usual “Unable to strip libquickjs.so” note).
- **Kotlin unchanged:** `gradle/libs.versions.toml` still has `kotlin = "2.4.20"`. No function-typed `@Provides` input was needed in this task, so no wrapper class was introduced.
- No commit was made.
