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

- [ ] All four host adapters and the extension pass the shared request contract tests.
- [ ] Direct-file and generic-page D2/D3 tests still pass unchanged.
- [ ] Refused headers never leave the device; the allowlist is unit-tested.
- [ ] Extension reply reports the effective header set; the README notes which headers MV3 cannot set.

## Evidence / notes

Not started.
