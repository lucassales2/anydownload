---
id: T-006
type: task
priority: P0
milestone: M0
tags: [task, security, licensing]
---

# T-006 — Review security, licensing and distribution

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security plan](../04-delivery/Security-and-licensing.md) · [Risk register](../04-delivery/Risk-register.md)

## Outcome

An approved trust/auth/credential model, dependency/license decision process and viable platform-distribution strategy before production scaffolding.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [ ] Review threats for URL extraction, SSRF/egress, options/process execution, filesystem paths, artifacts, events and updates.
- [ ] Decide native/browser authentication, owner authorization, cookie consent/storage/deletion, redaction and retention requirements.
- [ ] Review exact candidate dependency/binary licenses with T-005; obtain owner decision on project license before adding a LICENSE or copying code.
- [ ] Assess Apple 5.2.3/2.5.2, Play policies, third-party content permissions, signing and viable distribution channels; record unresolved blockers honestly.
- [ ] Approve safe-option parity differences or revise scope; record approval and required security tests/ADRs.

## Evidence / notes

Not started. Existing security/license documentation is a preliminary inventory, not a legal opinion or completed audit. Public visibility does not select a license.
