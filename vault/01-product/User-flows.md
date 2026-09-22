---
type: ux-plan
status: accepted-direction
tags: [product, ux]
---

# User flows

[Home](../Home.md) · [Product brief](Product-brief.md) · [API outline](../02-architecture/API-outline.md)

These are interaction requirements for the local app, not finished wireframes. Screen designs belong to [T-007](../06-tasks/T-007-Define-UX-and-contract.md). There is no server connection step.

## Information architecture

- **Add:** URL/paste/share input, media type/quality, expandable advanced options.
- **Queue:** pending, extracting, queued, downloading, postprocessing, waiting; single/bulk controls.
- **History:** completed/failed items, errors, artifacts, retry, export, clear/delete distinction.
- **Subscriptions:** source, interval, filters, status, manual check, pause/resume, rename/delete.
- **Settings:** local preferences, storage folders, cookies/presets, diagnostics/version. No account and no server endpoint.

Use bottom navigation or tabs on small displays and a sidebar on larger displays. Final UI is subject to the platform spike.

## 1. Open the app

The app is ready without login. The first screen is Add. Offline means this device cannot reach the source site.

## 2. Download one video or audio track

1. Paste or share a URL; validate syntax without claiming site support prematurely.
2. Resolve metadata with bounded extraction. Show title/source/thumbnail when available and a cancel action.
3. Choose video/audio/captions/thumbnail, profile and destination. Show unavailable combinations and fallback behavior clearly.
4. Confirm a job; repeated taps/network retries use an idempotency key.
5. Display progress by phase. Unknown size/ETA stays unknown rather than showing invented percentages.
6. The file is written on this device. Offer open or share according to platform capabilities.
7. Closing the app stops in-progress work. A finished file remains where it was saved.

## 3. Playlist, channel, or batch

- Explain single-video versus playlist interpretation and item limits before potentially large expansion.
- Display expansion/import progress, skipped/unavailable entries, and cancellation; preserve successfully created child jobs.
- Auto-start or leave items pending. Allow individual/bulk start and cancel.
- Copy/export URLs only on explicit action; they may reveal private source information.

## 4. Recover from failure or disconnection

- Closing the app interrupts an active download. Reopening restores the on-device queue and marks interrupted work for retry.
- Show a concise error category and optional **redacted** details; allow single/bulk retries.
- Retry re-extracts expiring media URLs and preserves approved options. Byte-level resumption is best effort, not guaranteed.
- Restart leaves no permanently stuck “downloading” rows.

## 5. Subscribe to new uploads

1. Add a channel/playlist and choose media/options, interval, title filter and initial back-catalog policy.
2. Explain that checks run while the app is open. iOS and the browser do not keep scans running after suspension.
3. Show last/next check, errors, queued unseen items; allow check now/all/selected, pause/resume, rename/filter edit, delete.
4. Deleting a subscription stops future checks; deleting its history or artifacts is a separate explicit choice.

## 6. Authorized cookie workflow

- Explain that a cookie file stays on this device and can expose an account.
- Validate exported cookie file format/size, show configured/not-configured status without displaying values, allow replace/delete.
- Do not promise direct access to Safari/other apps' cookie stores on iOS or arbitrary browser cookies on web.
- Explain expired/insufficient cookies and provide a safe recovery path; never encourage posting them in an issue.

## 7. History and destructive actions

Distinguish **remove history entry** from **delete the file on this device**. Confirm destructive scope and report partial failures. Removing a queue item cancels work; it is not necessarily deletion of an existing artifact. A browser download cannot generally be deleted later by the web app.

## Accessibility and edge states

Plan keyboard-only navigation, focus after dialogs, labeled controls, progress announcements without event spam, screen-reader checks, large text, theme contrast, narrow windows, empty states, slow metadata, disk full, and expired cookies. No essential action should depend only on color, hover, or drag-and-drop.
