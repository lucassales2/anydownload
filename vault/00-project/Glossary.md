---
type: reference
tags: [project, glossary]
---

# Glossary

[Home](../Home.md) · [Architecture](../02-architecture/Architecture.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

| Term | Meaning in this project |
| --- | --- |
| KMP | Kotlin Multiplatform: shared Kotlin code compiled for multiple targets. It does not make native executables portable to every target. |
| Compose Multiplatform | Proposed shared UI framework; platform support and browser behavior must be validated separately from core KMP. |
| Engine | Component that resolves and downloads source media, proposed to use yt-dlp plus required runtimes/postprocessors. |
| Extractor | yt-dlp's site-specific or generic implementation for discovering metadata/media URLs. Support varies by engine version and site behavior. |
| Remote mode | A trusted server performs extraction, download, and conversion. Clients control jobs and fetch finished files. |
| Local mode | A device executes a local engine. Optional feasibility work for desktop/Android, not assumed for iOS/web. |
| Job | Durable server-side work request, distinct from a client's connection or a transfer to the device. |
| Artifact | Output file: video, audio, captions, thumbnail, chapter, or metadata sidecar. One job may produce many artifacts. |
| Device transfer | Copy/export of an artifact from the server to local device storage; can fail independently of a successful job. |
| Postprocessing | Muxing, conversion, embedding metadata/subtitles/thumbnails, clipping, or splitting chapters after extraction. |
| Preset | Named bundle of approved engine options, layered over global defaults. |
| Subscription | Durable periodic scan of a channel/playlist that queues eligible unseen items. Not a paid membership or bypass mechanism. |
| Parity | Evidence that a reviewed MeTube workflow has an equivalent outcome; platform/security differences are explicit. Not a claim of identical API or source code. |
| ADR | Architecture Decision Record: a proposal or accepted decision with context, alternatives, and consequences. |
| M0–M5 | Roadmap milestone identifiers. |
| T-NNN / F-NN | Stable task / feature-parity identifiers. |
