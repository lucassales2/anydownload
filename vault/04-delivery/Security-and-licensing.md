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

## License review inventory

Observed upstream metadata/source on **2026-09-16**; verify full license text and the actual shipped artifacts before adoption. This table is engineering due diligence, not legal advice.

| Component | Observed license / important distinction | Planning implication |
| --- | --- | --- |
| AnyDownload | **Not selected.** No project LICENSE has been added. | Public visibility alone is not an open-source permission grant. Owner chooses license before code reuse/distribution. |
| yt-dlp source/PyPI source and wheel | Unlicense according to upstream. | Check exact dependency graph and notices; do not assume all distributed executables have the same license. |
| yt-dlp bundled release executables | Upstream states PyInstaller bundles include GPLv3+ code; other artifacts also bundle MIT/ISC components. | Review chosen binary individually; preserve notices and satisfy applicable redistribution/source obligations. |
| YtDlp-kt | GPL-3.0 metadata; archived JVM wrapper. | Review full obligations if adopted/forked; do not copy code on the assumption that “Kotlin wrapper” means permissive/KMP. |
| MeTube | AGPL-3.0 metadata. | Feature inspiration is not code reuse. Copying/modifying/distributing or operating modified AGPL code requires an explicit review of obligations, including network-source provisions where applicable. |
| FFmpeg / ffprobe | Build-dependent LGPL/GPL and codec/patent considerations. | Record configure flags, codecs, license notices, source requirements and store/distribution compatibility for each build. |
| yt-dlp-ejs / JS runtime | EJS has Unlicense code and MIT/ISC bundled components; runtime has its own terms. | Include EJS/runtime in SBOM, version/update policy and artifact review; do not assume Python + FFmpeg alone is enough for current YouTube behavior. |
| Kotlin/Compose/Ktor and other libraries | Exact coordinates/versions not selected. | Audit transitive dependencies once chosen; do not invent a final dependency inventory now. |
| Obsidian Kanban | GPL-3.0 upstream plugin; separate documentation tool. | Install locally via registry/release; plugin code is not vendored in this repository or proposed app. |

Sources and pinned revisions: [upstream review](../05-research/Upstream-review.md). A process boundary or separate repository is not, by itself, a conclusion about license compatibility.

## Platform distribution and authorized use

- Download only media owned by the user or explicitly permitted to be downloaded, subject to applicable law and site terms. No DRM/paywall bypass is planned.
- Store publication is out of scope. Portfolio builds do not include App Store or Play submission. Local signing needed to run on a device is a build detail, not a release channel.
- Apple's [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) **5.2.3** and **2.5.2**, and [Google Play policies](https://play.google.com/about/developer-content-policy/), stay relevant only if distribution is reconsidered later.

## Approval gate

T-006 records the threat model for a local engine, the cookie-storage decision, and the license inventory before extractor source is copied. Store review is not part of that gate.
