---
type: security-plan
status: proposed
tags: [delivery, security, licensing]
---

# Security, privacy, licensing and distribution

[Home](../Home.md) · [Risk register](Risk-register.md) · [Architecture](../02-architecture/Architecture.md) · [Review task](../06-tasks/T-006-Review-security-licensing.md)

Planning requirements, not a completed security or legal audit. The app is local and has no login. These controls still apply to the first on-device download.

## Trust boundaries

1. Untrusted pasted/shared URL and user options → in-app engine.
2. Engine → on-device queue, cookie store, and output files.
3. Engine → third-party websites, manifests, redirects, and media URLs.
4. Dependency source → code and any media toolkit linked into the app.

There is no server to authenticate. A public GitHub repository does not grant a license to copy upstream code.

## Required controls

### App access

- The app does not require an account. Jobs, files, and cookies are local to the device.
- Do not put cookies, signed media URLs, or raw extractor diagnostics in logs, history exports, or bug reports.

### SSRF and worker egress

- Accept approved HTTP(S) source URLs only; reject userinfo, unsupported schemes and malformed/ambiguous URLs.
- Reject private/loopback/link-local/reserved destinations across IPv4/IPv6, DNS and every redirect, not only the initial string.
- Extraction discovers additional manifests/CDNs/media URLs. Input validation alone cannot constrain those requests; enforce worker egress/network isolation or a controlled outbound proxy that also blocks internal/metadata endpoints and DNS-rebinding paths.
- Proxies and internal-address exceptions are operator-owned, explicit and audited. Per-download options must not bypass the network boundary.
- Test metadata endpoints, DNS changes, redirects, IPv6 variants and malicious extracted URLs. Keep any local fixture exception strictly test-only.

### Process, options and filesystem safety

- Use typed requests and argument arrays or a private worker protocol, never shell-string concatenation.
- Treat arbitrary yt-dlp options as executable configuration. Block execution hooks, plugins, arbitrary postprocessors/external downloaders, config loading, untrusted proxy overrides and paths that escape approved roots. Use an allowlist, not only a denylist.
- Apply precedence for allowed global/preset/override values, then validate and enforce non-overridable safety constraints; `null` cannot clear security controls.
- Run workers with least privilege, isolated writable directories, bounded CPU/memory/time/network and no host/Docker socket mounts.
- Normalize paths and filenames; reject traversal/absolute paths, symlink escapes and unsafe templates. Artifact lookup uses authorized IDs, not arbitrary request paths.
- Stream only finalized registered artifacts. Exclude credentials/state/temp files from directory serving; listing/public links are opt-in and scoped.

### Cookies, private source URLs and logs

- Cookies are bearer credentials and can expose an account. Import only with explicit consent, and keep the file on this device.
- Validate size/format, scope to owner/provider where feasible, restrict worker file permissions, keep values outside job/event payloads and remove transient copies after use.
- Decide encryption-at-rest and key storage/backups; encryption without protected keys is not a complete control. Define replacement/deletion/expiry and treatment of jobs already using a credential snapshot.
- Do not return raw cookies, Authorization headers, signed media URLs or unredacted engine diagnostics to clients/logs/support bundles.
- Provide log/URL retention and redacted export policies. Synthetic examples only in this public vault.

### Abuse, recovery and updates

Bound playlist expansion, concurrent work, retries, media duration/size, disk usage, regex evaluation and subscription frequency. Persist state and test restart/cancellation races. Pin the full engine runtime set, verify trusted release artifacts where available, test updates and support rollback; do not let client-supplied download options select arbitrary executable updates.

## Local engine threat notes (D2)

Recorded at [T-006](../06-tasks/T-006-Review-security-licensing.md) on 2026-09-23 for the D2 slice: one direct HTTP(S) file download per host, no backend, no login. The server-era controls above describe later architecture; this section is the local-engine record and the controls the D2 implementation must honor.

| Threat | Local record and control |
| --- | --- |
| Pasted/shared untrusted URL | Validate before any network call: http(s) schemes only, reject userinfo, malformed or ambiguous URLs, and across **every** redirect reject private/loopback/link-local/reserved IPv4/IPv6 destinations and DNS-rebinding paths. A direct-file URL is fetched by the platform HTTP adapter directly; it is never handed to a shell or a `ProcessBuilder` in common code. |
| Option injection | Options are an **allowlist** (Q-09, closed on [T-003](../06-tasks/T-003-Approve-product-scope.md)). Free-form yt-dlp JSON stays disabled. Argument arrays, never concatenated command strings. Non-overridable safety constraints cannot be cleared by per-job values; `null` cannot clear a control. |
| Filesystem paths | Output names derive from validated inputs, are normalized, and reject traversal, absolute paths, symlink escapes and unsafe templates. Every host writes inside its own app-private sandbox; artifact lookup uses registered IDs, not arbitrary request paths. |
| Local cookies | Import only with explicit user consent; scope to owner/provider where feasible; restrict file permissions; keep values out of job/event payloads and remove transient copies after use. There is no app login. See the on-device cookie decision below. |
| Logs, history, toasts, exports | Cookie contents, signed media URLs, Authorization headers and raw process/extension stderr never enter logs, history, toasts, exports, or this vault. Unknown progress stays unknown (no invented percent, speed, or ETA). Tests and evidence use fixture hosts only. |

### On-device cookie decision (T-006)

- **Consent:** cookie import is the user's explicit action only; the UI states the cookie file stays on this device. There is no app login.
- **Storage:** on-device only, outside git and outside the vault, with scoped file permissions.
- **Redaction:** cookie values and signed media URLs never appear in job payloads, logs, exports, history, or paste text. Synthetic examples only in this public vault.
- **Deletion / expiry:** user deletion removes the file and its store references; expired or superseded snapshots are pruned. Jobs already using a credential snapshot keep the snapshot they started with.
- **Encryption-at-rest:** deferred to M3 ([T-018](../06-tasks/T-018-Cookie-lifecycle.md)). Until then, device OS protection plus app-private storage apply; T-018 must revisit key storage and backups before cookie persistence ships.

## License review inventory

Observed upstream metadata/source on **2026-09-16**; the Chaquopy/CPython and spotDL rows were inspected again on **2026-09-23** for T-006. Verify full license text and the actual shipped artifacts before adoption. This table is engineering due diligence, not legal advice.

| Component | Observed license / important distinction | Planning implication |
| --- | --- | --- |
| AnyDownload | **MIT**, chosen by the owner on 2026-09-21. See the repository `LICENSE`. | MIT applies to this project's own code and docs. It does not relicense copied upstream extractors, MeTube, or bundled media toolkits. |
| yt-dlp source/PyPI source and wheel | Unlicense according to upstream. | Check exact dependency graph and notices; do not assume all distributed executables have the same license. |
| yt-dlp bundled release executables | Upstream states PyInstaller bundles include GPLv3+ code; other artifacts also bundle MIT/ISC components. | Review chosen binary individually; preserve notices and satisfy applicable redistribution/source obligations. |
| YtDlp-kt | GPL-3.0 metadata; archived JVM wrapper. | Review full obligations if adopted/forked; do not copy code on the assumption that “Kotlin wrapper” means permissive/KMP. |
| MeTube | AGPL-3.0 metadata. | Feature inspiration is not code reuse. Copying/modifying/distributing or operating modified AGPL code requires an explicit review of obligations, including network-source provisions where applicable. |
| spotDL | MIT on the README, inspected 2026-09-23. | D2 reimplements the match-on-YouTube workflow and copies no matcher source. The MIT row is now recorded at T-006; any later copy must keep the notice and still not vendor the package or its release executable without reviewing what that build bundles. |
| FFmpeg / ffprobe | Build-dependent LGPL/GPL and codec/patent considerations. | Record configure flags, codecs, license notices, source requirements and store/distribution compatibility for each build. |
| yt-dlp-ejs / JS runtime | EJS has Unlicense code and MIT/ISC bundled components; runtime has its own terms. | Include EJS/runtime in SBOM, version/update policy and artifact review; do not assume Python + FFmpeg alone is enough for current YouTube behavior. |
| Chaquopy + embedded CPython (Android-only) | Repository `LICENSE.txt` is **MIT** — “Copyright (c) 2017-2025 Chaquo Ltd and contributors”; GitHub API reports SPDX `MIT`; inspected 2026-09-23, `master` VERSION 17.1.0, latest release tag 17.0.0. Chaquopy embeds CPython (PSF License) and prebuilt Python packages inside the APK, each under its own terms (pinned yt-dlp would be Unlicense). | Adapter lives only under `apps/android`, never in shared code. At [T-041](../06-tasks/T-041-Android-http-and-chaquopy.md), freeze and record the exact Chaquopy/CPython/yt-dlp wheel versions, preserve the MIT/PSF/Unlicense notices, and verify release artifacts before enabling the adapter. |
| Kotlin/Compose/Ktor and other libraries | Exact coordinates/versions not selected. | Audit transitive dependencies once chosen; do not invent a final dependency inventory now. |
| Obsidian Kanban | GPL-3.0 upstream plugin; separate documentation tool. | Install locally via registry/release; plugin code is not vendored in this repository or proposed app. |

Sources and pinned revisions: [upstream review](../05-research/Upstream-review.md). A process boundary or separate repository is not, by itself, a conclusion about license compatibility.

## Platform distribution and authorized use

- Download only media owned by the user or explicitly permitted to be downloaded, subject to applicable law and site terms. No DRM/paywall bypass is planned.
- Store publication is out of scope. Portfolio builds do not include App Store or Play submission. Local signing needed to run on a device is a build detail, not a release channel.
- Apple's [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) **5.2.3** and **2.5.2**, and [Google Play policies](https://play.google.com/about/developer-content-policy/), stay relevant only if distribution is reconsidered later.

## Approval gate

T-006 records the threat model for a local engine, the cookie-storage decision, and the license inventory before extractor source is copied. Store review is not part of that gate.

Recorded on 2026-09-23: local-engine threat notes and the on-device cookie decision above, plus the Chaquopy/CPython row and spotDL confirmation in the inventory, all precede any extractor-source copy or Chaquopy adoption.
