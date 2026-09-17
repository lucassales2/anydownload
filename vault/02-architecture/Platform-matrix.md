---
type: platform-matrix
status: proposed
tags: [architecture, platforms]
---

# Platform feasibility and sharing matrix

[Home](../Home.md) · [Architecture](Architecture.md) · [Target spike](../06-tasks/T-004-Validate-KMP-targets.md) · [Upstream review](../05-research/Upstream-review.md)

KMP shares code; it does not erase operating-system, runtime, browser, or app-store restrictions. Every entry below describes a **plan to validate**, not current app support.

| Concern | Android | iOS | Desktop: Windows/macOS/Linux | Web |
| --- | --- | --- | --- | --- |
| Domain/networking | Shared Kotlin with platform HTTP engine. | Shared Kotlin/Native with platform HTTP engine. | Shared Kotlin/JVM. | Kotlin/Wasm or Kotlin/JS; verify library compatibility. |
| UI candidate | Compose Multiplatform/Android Compose. | Compose Multiplatform with native host/adapters. | Compose/JVM. | Compose/Wasm pending spike; Kotlin/JS + web-specific UI fallback. |
| Initial extraction | Remote trusted engine. | Remote trusted engine. | Remote trusted engine. | Remote trusted engine. |
| Local yt-dlp | Optional spike; Python/native binaries, ABI, Android execution policy and packaging are not solved by a JVM wrapper. | Not a baseline: no normal desktop subprocess model; sandbox, executable/runtime distribution and store rules require a separate design. | Feasible candidate through managed CLI/process adapter; signing, packaging and licensing still required. | Native CLI/`ProcessBuilder` unavailable; Python/Wasm experiments do not provide full yt-dlp/FFmpeg/network parity. |
| Device saving | MediaStore/Storage Access Framework as appropriate; streaming transfer. | App sandbox then document/share export; background URLSession feasibility for artifact transfer. | File chooser and streaming filesystem writes; reveal/open integration. | Browser-managed download or supported file API after user gesture; no arbitrary destination path or universal silent save. |
| Incoming URL | Android share intent / app link. | Share extension or shortcut/deep link; separate extension/lifecycle constraints. | Clipboard/deep link; optional companion integrations. | Paste; browser extension/bookmarklet/PWA share support varies and needs testing. |
| Work while UI closed | Server job continues; local/device transfers use approved foreground/background mechanisms. | Server job continues; iOS does not guarantee continuous app execution. | Server job continues; local work needs explicit application/service lifecycle. | Server job continues; closing/suspending a tab stops ordinary app execution. Service workers are not unlimited download daemons. |
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

The server proposal can also target Linux amd64/arm64 to preserve MeTube-style self-hosting options. That is a packaging requirement to validate, not a deployment performed during planning.
