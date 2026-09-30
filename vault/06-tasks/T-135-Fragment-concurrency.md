---
id: T-135
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, downloads]
---

# T-135 — Fragment concurrency

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

`FragmentDownloader` fetches a bounded number of fragments at once and can skip an unavailable fragment. Cancellation still discards the file, and the existing AES-128 tests stay green. Covers E-10.

## Dependencies

- [T-134](T-134-Resume-attempt.md) — Done 2026-09-29.

## Context the next session needs

- `FragmentDownloader.download` is sequential today: the init segment, then each fragment in order, AES-128-CBC decryption per fragment, `onChunk` writes in order, `maxRetries = 2`.
- `HttpDownloadEngine.streamManifestToTemp` is the caller: `onChunk` writes straight into one temp, cancellation is checked between fragments, and a `Failed` outcome discards the temp in the engine's `finally`.
- Upstream `downloader/fragment.py` exposes `concurrent_fragment_downloads` (default 1) and `skip_unavailable_fragments` (default true for VOD, false for live).

Files: `shared/core/src/commonMain/kotlin/com/anydownlod/core/download/FragmentDownloader.kt`. Tests: `FragmentDownloaderTest`, `Aes128CbcTest`, `M3u8Test`, `MpdTest`, and `ManifestEngineDownloadTest`.

## Work

- Add a bounded concurrency parameter (default a small cap; `1` stays available so the sequential behavior is testable). At most N fragment requests are in flight, and results are written in fragment order so the output stays byte-exact. Keep memory bounded to the in-flight window.
- Decrypt AES-128 at the fragment's turn, keeping the explicit-IV and sequence-IV paths.
- Add a skip-unavailable option: an exhausted fragment is skipped when the option is on, and the outcome records what was skipped; when it is off the outcome stays `Failed`. The engine's default matches upstream and is pinned by a test.
- Cancellation stops new requests and the caller still discards the temp.
- Keep the existing AES-128 fixtures green.

## Acceptance criteria

- [x] A bounded-concurrency test with several fragments proves requests overlap and the artifact is byte-exact.
- [x] An unavailable fragment is skipped when the option is on, and fails typed when it is off; both paths are tested.
- [x] Cancel mid-playlist still leaves no file.
- [x] The NIST vector and the openssl AES-128 fragment vector stay green.
- [x] `:shared:core:jvmTest` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Implementation:** `FragmentDownloader` now takes `concurrency` (default `DEFAULT_CONCURRENCY = 4`; `1` restores strict sequence) and `skipUnavailableFragments` (default true, matching upstream `fragment.py`'s VOD default). The init segment is fetched first; the media fragments run in a sliding window of at most `concurrency` `async` fetches. Results are consumed in playlist order, so `onChunk` still sees byte-exact ordered bytes, and AES-128 decryption still happens at the fragment's turn (explicit IV or sequence IV). A fragment whose retries are exhausted is recorded in `FragmentOutcome.Completed.skipped` when the option is on and fails typed when it is off. The collector checks `isCancelled()` before each turn and before each launch, and throws an internal `Stop` when the caller cancels; the scope cancels in-flight fetches and the engine still discards the temp. The engine passes `skipUnavailableFragments = true` explicitly (upstream VOD).
- **Tests:** `FragmentDownloaderTest` gained `boundedConcurrencyOverlapsRequestsAndStaysInOrder` (three parked requests, `maxInFlight == 3`, output `AAABBBCCC` in order), `unavailableFragmentIsSkippedWhenTheOptionIsOn` (`Completed(skipped = [1])`, output `AAACCC`), and `unavailableFragmentFailsWhenTheOptionIsOff` (typed `Failed`); `cancelStopsBetweenFragments` now pins `concurrency = 1` for its sequential cancel point. `ManifestEngineDownloadTest.unavailableFragmentIsSkippedByTheEngineDefault` serves a playlist whose middle fragment 404s and asserts the engine still completes with `G0G2`, pinning the engine's upstream default. `cancelMidPlaylistLeavesNoFile` now parks its slow fragment on a `CountDownLatch` (released after `cancel`), so the cancel point is deterministic instead of a wall-clock race.
- **Commands:** `./gradlew --console=plain :shared:core:jvmTest` → BUILD SUCCESSFUL, 544 tests, 0 failures, 0 errors (`FragmentDownloaderTest` 9, `Aes128CbcTest` 3, `ManifestEngineDownloadTest` 5). `./gradlew --console=plain :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL. `./gradlew --console=plain :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL, 494 tests, 0 failures, 0 errors.
- NIST and openssl AES-128 vectors stay green (`Aes128CbcTest`); no commit.
