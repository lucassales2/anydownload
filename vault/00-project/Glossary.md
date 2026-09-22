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
| Engine | In-app Kotlin port of yt-dlp. It resolves and downloads media. It is not a call to the Python CLI. |
| Extractor | yt-dlp's site-specific or generic implementation for discovering metadata/media URLs. Support varies by engine version and site behavior. |
| Remote mode | Withdrawn product shape. A server performed extraction and clients fetched finished files. See ADR-001. |
| Local mode | The app executes the Kotlin engine and stores files on the device. This is the product on every target. |
| Job | On-device work request: a URL, options, progress, and output files. |
| Artifact | Output file: video, audio, captions, thumbnail, chapter, or metadata sidecar. One job may produce many artifacts. |
| Device transfer | Withdrawn as a separate server-to-device step. The download writes the artifact on the device. Sharing or exporting that file is a platform action. |
| Postprocessing | Muxing, conversion, embedding metadata/subtitles/thumbnails, clipping, or splitting chapters after extraction. |
| Preset | Named bundle of approved engine options, layered over global defaults. |
| Subscription | Durable periodic scan of a channel/playlist that queues eligible unseen items. Not a paid membership or bypass mechanism. |
| Parity | Evidence that a reviewed MeTube workflow has an equivalent outcome; platform/security differences are explicit. Not a claim of identical API or source code. |
| ADR | Architecture Decision Record: a proposal or accepted decision with context, alternatives, and consequences. |
| M0–M5 | Roadmap milestone identifiers. |
| T-NNN / F-NN | Stable task / feature-parity identifiers. |
