---
type: api-proposal
status: proposed
tags: [architecture, api]
---

# API outline

[Home](../Home.md) · [Architecture](Architecture.md) · [Domain](Download-lifecycle.md) · [Security](../04-delivery/Security-and-licensing.md)

**Design sketch only — no server or OpenAPI contract exists.** These paths apply if a new API is selected; they are not MeTube endpoints. [T-007](../06-tasks/T-007-Define-UX-and-contract.md) must resolve details after [T-005](../06-tasks/T-005-Choose-backend-engine.md).

## Proposed surface

| Operation | Candidate endpoint | Notes |
| --- | --- | --- |
| Engine/API capabilities | `GET /api/v1/capabilities` | Supported types/options, versions, quotas, deployment features; no secrets. |
| Resolve source | `POST /api/v1/resolutions` | Bounded asynchronous metadata/playlist work with an ID, polling/events and cancellation; not an unbounded HTTP request. |
| Cancel resolution | `POST /api/v1/resolutions/{id}/cancel` | Preserve any already acknowledged child work under documented semantics. |
| Create job/batch | `POST /api/v1/jobs` | Typed request, owner-scoped idempotency key, durable acceptance and job IDs. |
| List/get jobs | `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}` | Cursor pagination, filters, revisions; redact sensitive URLs/options as appropriate. |
| Start/cancel/retry | `POST /api/v1/jobs/{id}/start`, `/cancel`, `/retry` | Explicit state transitions; retry creates a new attempt. |
| Bulk actions | `POST /api/v1/jobs/actions` | Bounded ID lists, per-item outcome, no accidental cross-owner actions. |
| Delete history/files | `DELETE /api/v1/jobs/{id}` | Exact destructive scope must be explicit and confirmed; server-artifact deletion is not local-device deletion. |
| Artifacts | `GET /api/v1/jobs/{id}/artifacts`, `GET /api/v1/artifacts/{id}/content` | Authenticated metadata/streaming; no storage paths. Support Range/resume where meaningful. |
| Real-time state | `GET /api/v1/events` | SSE candidate, or authenticated WebSocket; select during spike. Sequence/cursor, snapshot recovery, backpressure. |
| Presets | `GET /api/v1/presets` | Ordered selection, versioning, approved options. Administrative mutation surface still to design. |
| Cookies | `POST /api/v1/credentials/cookies`, `DELETE /api/v1/credentials/{id}` | Bounded upload, opaque reference, safe configured/expiry status only; never a secret download endpoint. |
| Subscriptions | `/api/v1/subscriptions`, `/{id}`, `/{id}/check` | Create/list/update/delete, enable/disable, rename/filter/interval, manual check and bounded bulk operations. |
| Health/readiness | `/healthz`, `/readyz` | Minimal public health if needed; sensitive diagnostics require authorization. |

Administrative config, auth/session/pairing, update policy, retention and audit operations need separate authorization rules; this outline is not a complete contract.

## Example request shape — illustrative

```json
{
  "url": "https://media.example.org/authorized-video",
  "mediaType": "audio",
  "profile": "mp3",
  "quality": "192",
  "startPolicy": "automatic",
  "playlist": { "mode": "single", "itemLimit": 1 },
  "destination": { "folder": "audio" },
  "presetIds": [],
  "overrides": {},
  "credentialId": null
}
```

`example.org` is documentation-only. The client never submits an executable command or absolute server path. API quality/profile names are our proposed typed contract, not a promise of direct CLI/API flag equivalence. Source metadata resolution does not guarantee that a later download will still be available.

## Contract requirements

- **Auth:** server-owned identity; authorize jobs, artifacts, credentials, subscriptions and events individually. Decide native token/pairing versus browser secure session/CSRF behavior before implementation.
- **Idempotency:** define key scope, expiry, response replay and conflict behavior when a key is reused with different content.
- **Errors:** stable code, safe message, retryability, field errors/correlation ID. Redaction must precede serialization.
- **Events:** envelope with event ID, job/subscription ID, revision, type and timestamp; latest snapshot on reconnect, explicit cursor-expired behavior. Do not require clients to retain every progress event.
- **Files:** stream, sanitize `Content-Disposition`, use suitable MIME/nosniff/cache headers, avoid embedding a long-lived access token in a URL. Define authenticated browser download and short-lived scoped ticket alternatives during the spike.
- **Limits:** bounded payloads, upload size, metadata work, job/batch size, retries, regex time and event buffers; rate limits and per-owner quotas.
- **Compatibility:** version the contract and capability-gate unsupported options. Unknown field/enum strategy needs shared serialization tests.
- **Transport:** prefer same-origin browser hosting where practical. Explicit credential-aware CORS is not a replacement for authentication or CSRF protection.

## MeTube integration caveat

The reviewed MeTube handlers include `/add`, `/start`, `/delete`, `/retry`, cookie/subscription endpoints, and Socket.IO events. A plain Ktor WebSocket client is **not** automatically a Socket.IO client. If MeTube wins the spike, document a dedicated adapter and integration tests instead of pretending it implements this proposed API.
