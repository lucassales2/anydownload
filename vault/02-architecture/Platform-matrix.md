---
type: platform-matrix
status: proposed
tags: [architecture, platforms]
---

# Platform feasibility and sharing matrix

[Home](../Home.md) · [Architecture](Architecture.md) · [Target spike](../06-tasks/T-004-Validate-KMP-targets.md) · [Upstream review](../05-research/Upstream-review.md) · [Client yt-dlp options](../05-research/Client-yt-dlp-options.md)

KMP shares code; it does not erase operating-system, runtime, browser, or app-store restrictions. Every entry below describes a **plan to validate**, not current app support.

| Concern | Android | iOS | Desktop: Windows/macOS/Linux | Web |
| --- | --- | --- | --- | --- |
| Domain/networking | Shared Kotlin with platform HTTP engine. | Shared Kotlin/Native with platform HTTP engine. | Shared Kotlin/JVM. | Kotlin/Wasm or Kotlin/JS; verify library compatibility. |
| UI candidate | Compose Multiplatform/Android Compose. | Compose Multiplatform with native host/adapters. | Compose/JVM. | Compose/Wasm pending spike; Kotlin/JS + web-specific UI fallback. |
| Extraction | In-app Kotlin engine. Android HTTP and storage adapters are required. Feasibility is not yet shown. | In-app Kotlin engine. No desktop subprocess. Sandbox, suspension, and file export are the open risks. | In-app Kotlin engine on the JVM. First place to prove one extractor end to end. | In-app Kotlin engine on Compose/Wasm. No subprocess. Cross-origin fetches and media processing are the open risks. |
| Device saving | MediaStore/Storage Access Framework as appropriate; streaming transfer. | App sandbox then document/share export; background URLSession feasibility for artifact transfer. | File chooser and streaming filesystem writes; reveal/open integration. | Browser-managed download or supported file API after user gesture; no arbitrary destination path or universal silent save. |
| Incoming URL | Android share intent / app link. | Share extension or shortcut/deep link; separate extension/lifecycle constraints. | Clipboard/deep link; optional companion integrations. | Paste; browser extension/bookmarklet/PWA share support varies and needs testing. |
| Work while UI closed | The download stops when the process is killed. Background execution needs a platform mechanism and is not guaranteed. | The download runs only while the app is allowed to run. iOS suspends apps. | The download runs with the desktop process. Closing the app stops it unless a later background mode is added. | Closing or suspending the tab stops the download. A service worker is not a download daemon. |
| Secrets | Keystore-backed storage design. | Keychain-backed storage design. | OS credential store adapter. | Prefer same-origin secure HttpOnly sessions; decide CSRF/auth model, avoid long-lived localStorage tokens. |
| Cookies for extraction | Explicit exported file to trusted engine; no assumption of access to other apps' stores. | Explicit permitted import; cannot read other apps' Safari cookies freely. | Explicit file import; browser-store access only with informed consent in a separately approved local mode. | User-supplied file; page cannot read arbitrary other-domain cookies. |
| Release | APK/AAB and signing; store policy review. | Xcode/macOS build, signing/provisioning and store policy review. | Per-OS packages, signing/notarization where required. | HTTPS hosting, CSP/CORS/auth, browser compatibility, bundle and accessibility budget. |

## Current upstream stability context

The official [KMP platform stability page](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), checked 2026-09-16, lists Android/iOS/JVM as Stable and Kotlin/Wasm plus Compose/Wasm as Beta; Kotlin/JS is Stable. These are **upstream framework labels**, not proof our dependency combination or app works. Recheck at implementation time rather than locking to a stale version assumption.

## M0 evidence required

- Record Kotlin, Compose, Gradle, Android toolchain, JDK, Xcode, OS/browser versions and required CPU architectures.
- Compile/run a minimal shared state/networking flow on Android, iOS, desktop, web; explicitly record which desktop OSes were actually exercised.
- Demonstrate auth, reconnect, file export and large-file streaming without whole-file buffering; test OS/browser denial/cancel behavior.
- Test browser loading, supported features, accessibility, keyboard, Safari/WebKit behavior and fallback costs.
- Test iOS background transfer boundaries and share extension path; do not infer App Store approval from a simulator build.
- Choose minimum versions and release coverage from results, not from this proposal.

There is no server package. Desktop coverage still has to name which of Windows, macOS, and Linux were actually run.
