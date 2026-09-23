---
type: research
reviewed: 2026-09-23
tags: [research, engine, platforms]
---

# Client yt-dlp options

[Home](../Home.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [Platform matrix](../02-architecture/Platform-matrix.md) · [Upstream review](Upstream-review.md)

**Method:** public documentation and project pages inspected on 2026-09-23. No Android, iOS, or web build was run. No Python dependency was added to this repository. Upstream source was not copied here.

This note records how an installed or embedded yt-dlp could run on web, iOS, and Android, and what a Kotlin port still has to carry. It does not change [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) or [ADR-005](../03-decisions/ADR-005-Desktop-metube-phase.md). Desktop keeps calling the yt-dlp already on `PATH`.

## Question

Which of Briefcase, Buildozer, Chaquopy, pyqtdeploy, Termux, or a Kotlin port can perform downloads on web, iOS, and Android inside this Compose Multiplatform app?

## Sources

Checked 2026-09-23. Versions below are the pages inspected, not artifacts built in this repo.

- [Chaquopy Gradle plugin, 17.0](https://chaquo.com/chaquopy/doc/current/android.html) and [Chaquopy license](https://chaquo.com/chaquopy/license/): MIT since 12.0.1, embeds CPython in one existing Android application or library module.
- [Briefcase](https://briefcase.beeware.org/) 0.4.4 (2026-07-08) and the [BeeWare 2026 Q3 roadmap](https://beeware.org/news/buzz/2026/2026q3-roadmap/): whole-app packaging for Android, iOS, desktop, and a PyScript website. Official CPython iOS binaries are part of the Python 3.15 line.
- [yt-dlp EJS wiki](https://github.com/yt-dlp/yt-dlp/wiki/EJS): YouTube challenge solving needs yt-dlp-ejs plus Deno 2.3.0 or newer (default), Node 22 or newer, QuickJS dated 2023-12-09 or newer, or a deprecated Bun runtime.
- [yt-dlp-android](https://github.com/ffmpegkit-maintained/yt-dlp-android): Chaquopy example that calls `yt_dlp.YoutubeDL` in-process. The project describes a Python 3.13 runtime, an AAR on the order of 60–80 MB before FFmpeg, and a yt-dlp version frozen at library build time.
- [yt-dlp-apple-webkit-jsi](https://pypi.org/project/yt-dlp-apple-webkit-jsi/): a Python plugin that runs YouTube challenge scripts in Apple JavaScriptCore on iOS and macOS.
- License facts for yt-dlp source (Unlicense), yt-dlp release binaries (GPLv3+ pieces inside PyInstaller bundles), YtDlp-kt (GPL-3.0), and FFmpeg remain those recorded in the [upstream review](Upstream-review.md) on 2026-09-16. NewPipe Extractor is GPL-3.0; that license text was not re-audited here. [T-006](../06-tasks/T-006-Review-security-licensing.md) still owns the inventory before any extractor source is copied.

## Findings

A download needs three runtimes:

- **Extractor.** yt-dlp's Python catalog: YouTube plus a large set of other sites. It changes when a site changes.
- **JavaScript challenge solver.** Current YouTube support runs yt-dlp-ejs in Deno, Node, or QuickJS. Packaging Python does not include that runtime.
- **Media toolkit.** Merge, audio extract, clips, and many containers need FFmpeg, or a smaller platform substitute (Android `MediaMuxer`, iOS `AVFoundation`) that covers less.

Rewriting extractors in Kotlin does not supply the challenge scripts or FFmpeg. The browser adds a fourth limit: a page cannot fetch arbitrary YouTube or CDN origins, whether the extractor is Python or Kotlin.

### Briefcase (BeeWare)

Briefcase produces the application shell: a Gradle project on Android, an Xcode project on iOS, desktop installers, and a static PyScript site. It does not embed into `apps/android` or the existing Kotlin/Native iOS host beside Compose.

Android and iOS would be a second app, with Toga or another Python UI. The CPython iOS interpreter is no longer the main blocker. FFmpeg, background execution, and a JavaScript runtime remain. The web target runs in the page and cannot download from YouTube because of cross-origin fetches.

**Reject as the AnyDownload shell.** The iOS CPython linking work is a useful reference if an in-process interpreter is ever spiked inside the existing host.

### Buildozer (Kivy)

Buildozer packages a Kivy app with python-for-android into an APK. The UI would be Kivy. It has no browser target. iOS is kivy-ios, a separate toolchain. **Reject.**

### Chaquopy

Chaquopy is the only tool on this list that embeds CPython inside an existing Android app and lets Kotlin call Python. One module per app. The yt-dlp-android project shows the shape: in-process `YoutubeDL.download`, no yt-dlp subprocess, a large AAR, and no in-app `yt-dlp -U`.

On Android this can sit behind `DownloadEngine` and live only under `apps/android`. It still needs bundled QuickJS (Deno and Node are the wrong size) and a media toolkit. TLS stays Python's stack, so sites that require browser impersonation can fail. iOS and web are unsupported.

**Android-only adapter.** It is a way to get upstream site coverage on Android before a port. It is not the shared engine in ADR-004. Shared modules stay free of Python and `ProcessBuilder`.

### pyqtdeploy

pyqtdeploy freezes a PyQt or PySide application. The UI stack is Qt. Mobile support means Qt for Android or iOS, not a library inside this Compose or Kotlin/Native host. There is no web target. **Reject.**

### Termux

Termux is a user-installed Linux environment on Android, shipped from F-Droid or GitHub. A person can install yt-dlp there by hand. This app cannot bundle Termux, cannot require it, and cannot use it on iOS or the web. **Reject as an engine.** It remains a manual workaround on a developer handset.

### Kotlin port

This is the option that can live in `shared/core` and run on Android, iOS, desktop, and Compose/Wasm without a Python runtime. [ADR-002](../03-decisions/ADR-002-Kotlin-wrapper.md) still holds: YtDlp-kt is an archived GPL JVM wrapper around the Python CLI. NewPipe shows that an independent YouTube extractor can be maintained, and that YouTube alone (player clients, poTokens, SABR) is a continuing chase. NewPipe's license excludes copying it into this MIT tree. yt-dlp source may be ported only after T-006. Do not vendor the PyInstaller binaries.

The port does not remove these limits:

- **YouTube.** Challenge scripts stay JavaScript. Translating yt-dlp-ejs into Kotlin means repeating that work on every upstream break. Execute the scripts instead: desktop keeps Deno on `PATH`; Android embeds QuickJS; iOS uses JavaScriptCore, as yt-dlp-apple-webkit-jsi does; the web page can run the scripts and still cannot fetch the player.
- **Web.** Kotlin/Wasm hits the same cross-origin fetches a Pyodide or PyScript build would. A service worker is not a download daemon. A browser extension with host permissions is a different product, already listed as later work in [Phase 1](../00-project/Phase-1-Desktop-MeTube.md).
- **FFmpeg behavior.** Merge and audio extract need a per-target toolkit. iOS and web have no subprocess.
- **Site list.** Matching every site yt-dlp supports is a permanent upstream race. A portfolio port is one authorized public URL, then YouTube, then further sites one at a time.
- **iOS lifecycle.** A correct extractor runs only while iOS allows the process to run. Suspension ends the job. Finished files leave the sandbox through the share sheet.

Prove the port on the JVM first. Desktop keeps the CLI until the Kotlin extractor matches it on the same fixtures. The same common code then runs on the other hosts through platform HTTP and file adapters. That split is already the [platform matrix](../02-architecture/Platform-matrix.md).

## Limitations

No device build, package-size measurement, or fixture download was run for Chaquopy, Briefcase, QuickJS, JavaScriptCore, or a Kotlin extractor. AAR size and Python version are taken from the yt-dlp-android project's own description. YouTube's challenge requirements move; re-read the EJS wiki at implementation time. Store review stays out of scope. If distribution is reconsidered later, bundled interpreters become a review topic (Apple guideline 2.5.2 for downloaded code, and Play policy for downloader apps). Runtime self-update of yt-dlp or EJS scripts is the risky part of that review.

## Recommendation

Do not adopt Briefcase, Buildozer, pyqtdeploy, or Termux.

Keep `DownloadEngine` as the shared seam. Engines stay platform adapters:

- **Desktop:** installed yt-dlp, FFmpeg, and Deno. Already implemented under `apps/desktop`.
- **Android, if upstream coverage is needed before a port:** Chaquopy, a pinned yt-dlp, and bundled QuickJS, only under `apps/android`.
- **iOS:** do not start a Briefcase app. Linking CPython's iOS build into the existing host is a custom spike, the same idea as Chaquopy, and the route to the full catalog without a port. A small Kotlin extractor plus JavaScriptCore matches ADR-004.
- **Web:** leave the host on the in-memory fake. An in-page engine cannot pull media from arbitrary origins.
- **Kotlin port, when started:** HTTP download plus one non-YouTube extractor on the JVM, then the same code on Android and iOS. Add YouTube by embedding yt-dlp-ejs. Do not schedule a bulk extractor translation.

[T-004](../06-tasks/T-004-Validate-KMP-targets.md) still has to show a local download on each target before that target is treated as feasible. [T-006](../06-tasks/T-006-Review-security-licensing.md) still records licenses before extractor source is copied.
