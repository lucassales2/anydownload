---
id: T-056
type: task
priority: P0
milestone: D4
tags: [task, engine, kmp, platforms]
---

# T-056 — HTTP request port: method, headers, body, range

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

`HttpTransfer` can send what an extractor needs: `GET` and `POST`, declared request headers, a request body, and a byte range. Every host adapter and the browser extension honor it. Existing direct-file and generic-page behavior is unchanged.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md) — the per-host adapters this extends.
- [T-050](T-050-Web-extension-generic.md) — the extension bridge this extends.

## Context the next session needs

Today `HttpTransfer.execute(url)` performs one `GET` with no headers. YouTube's innertube `player` endpoint is a `POST` with a JSON body and client headers (`X-YouTube-Client-Name`, `X-YouTube-Client-Version`, `Content-Type`, `User-Agent`, `Origin`). Downloading with ranges needs `Range` and reading `Content-Range`. Cookies are not part of this task ([T-018](T-018-Cookie-lifecycle.md)).

## Work

- Add `HttpRequest(url, method = GET, headers = emptyMap(), body: ByteArray? = null, range: LongRange? = null)` and `HttpTransfer.execute(request)`. Keep a `execute(url)` convenience that builds a plain `GET`.
- `HttpResponse.Final` exposes `statusCode`, `contentType`, `totalBytes`, `contentRange`, `headers` (lowercased names, allowlisted: `content-type`, `content-length`, `content-range`, `accept-ranges`, `location`, `etag`, `last-modified`), and the streamed body.
- Request header names are an allowlist in `com.anydownlod.core.platform.HttpHeaders`: `accept`, `accept-language`, `content-type`, `origin`, `referer`, `user-agent`, `range`, `x-youtube-client-name`, `x-youtube-client-version`, `x-goog-visitor-id`, `x-origin`. Anything else is dropped and reported once in the engine's redacted diagnostics. `Cookie`, `Authorization`, and `X-Goog-*` auth headers are refused here until T-018.
- JVM and Android (`JavaNetHttpTransfer`): set method, headers, body, and `Range`; keep manual redirect handling. iOS (`IosHttpTransfer`): `NSMutableURLRequest` with the same fields. Web (`WebExtensionTransfer` + `background.js`): a `fetch-request` message carrying method, headers, base64 body, and range; the extension applies `checkUrl` before the request as today. Record which headers MV3 `fetch` refuses (for example `User-Agent`, `Origin`) and return the effective header set in the reply so the engine can log the difference redacted.
- Redirects: the engine still owns the budget and policy on every hop; a `POST` that redirects is re-sent as `GET` only for 303, otherwise fails typed, as upstream's stack does.
- Unit tests on JVM with a local `HttpServer`: method and headers arrive, body arrives, `Range: bytes=0-1023` yields 206 with `Content-Range`, disallowed header dropped, redirect after `POST`. Extension tests (`node --test`) for the new message. iOS test in `IosEngineTest` behind the existing opt-in fixture port.

## Acceptance criteria

- [x] All four host adapters and the extension pass the shared request contract tests.
- [x] Direct-file and generic-page D2/D3 tests still pass unchanged.
- [x] Refused headers never leave the device; the allowlist is unit-tested.
- [x] Extension reply reports the effective header set; the README notes which headers MV3 cannot set.

## Evidence / notes

Done 2026-09-24.

- Request contract in `com.anydownlod.core.platform`: `HttpRequest(url, method, headers, body, range)`, `HttpTransfer.execute(request)` plus the `execute(url)` convenience, `HttpHeaders` request/response allowlists, `HttpRedirects`, `ByteArrayHttpBody`, `ContentRange`. `HttpResponse.Final` now exposes `contentRange` and allowlisted `headers`; a typed `HttpResponse.Failed(reason, message)` covers permission/blocked/timeout/network.
- JVM + Android: `JavaNetHttpTransfer` moved to the shared `jvmAndroidMain` source set (default-hierarchy group) so both hosts compile one implementation. Method, allowlisted headers, fixed-length body, and `Range` are set; `Content-Range` supplies the full total; response headers are allowlist-filtered. A refused request header never leaves the device and is reported once by name.
- iOS: `IosHttpTransfer` sets method/headers/body/range on `NSMutableURLRequest`, returns filtered response headers, and takes the total from `Content-Range`.
- Web: `WebExtensionTransfer` (common, bridge-backed) plus `WebExtensionBridge.fetch`. The extension's `fetch-request` message carries method, allowlisted headers, base64 body, and range, applies `checkUrl`, filters request and response headers, returns the effective `sentHeaders`, and fails typed when the body exceeds the 8 MiB port cap. `WebExtensionTransfer` reports allowlist-refused and MV3-refused names through `onDroppedHeaders` (names only).
- Redirects: `HttpRedirects.afterRedirect` — 303 becomes a body-less `GET`, a `GET` keeps its headers, and a `POST` on any other 3xx fails typed.
- Tests: `HttpRequestTest` (11, common) covers the allowlist, range, redirects, body streaming, and web mapping; `JavaNetRequestContractTest` (5, JVM, local `HttpServer`) covers method/headers/body/range, the response allowlist, and the POST-redirect refusal; `HttpDownloadEngineTest` gains the typed failed-transfer case; the extension adds 5 `fetch-request` tests (17 total); `IosEngineTest.realNSURLSessionCarriesTheExtractorRequestContract` runs behind the existing opt-in fixture port.
- Verification: `./gradlew :shared:core:jvmTest` → 144 tests, 0 failures; `node --test apps/web-extension/test/bridge.test.mjs` → 17 pass; `./gradlew :apps:desktop:test :apps:android-engine-tests:test :shared:core:iosSimulatorArm64Test :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` → green. The D2/D3 suites are unchanged and green (`JavaNetPlatformTest` 6, `GenericExtractorTest` 15, `HttpDownloadEngineTest` 27).
- Live iOS run (opt-in): started `node tools/fixture-server/server.mjs --port 8123` (new test tooling; it serves POST `/echo`, ranged files, and the D3 HTML routes), pointed `/tmp/anydownlod-ios-live.txt` at `127.0.0.1:8123`, and ran `:shared:core:iosSimulatorArm64Test` — `IosEngineTest` 7/7 passed; the server log recorded `POST /echo` and `GET /files/tiny.bin range=bytes=0-15`. Server and endpoint file were removed afterwards.
- README and `apps/web-extension/README.md` record the message and the MV3-refused headers (`Origin`, `Referer`, `User-Agent`, `Cookie`, `Host`, `Content-Length`, `Accept-Encoding`, `Sec-*`, `Proxy-*`). No upstream module was translated, so `port/manifest.json` is unchanged.
