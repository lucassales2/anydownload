---
type: prompt
milestone: D11-D16
tags: [project, engine, handoff]
---

# Phase 11–16 loop prompt

[Schedule](Full-engine-schedule.md) · [Kanban](../Kanban.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

Paste the block below as the first message in a new agent session. It implements D11 through D16, one Kanban card at a time, and stops when [T-125](../06-tasks/T-125-Priority-sites.md) is Done or a card is blocked. It does not start D17.

Do not also paste [Phase-11-Loop-prompt.md](Phase-11-Loop-prompt.md) or arm a short `/loop` against this prompt. A second wake will edit the same files.

## Prompt

```text
Goal: Implement AnyDownload phases D11 through D16, one tagged Kanban card at a time, until T-125 is Done or a card is blocked. Do not start T-126 or any later engine card. Do not arm a second short /loop against this prompt. Do not also run vault/00-project/Phase-11-Loop-prompt.md. D9 and D10 are Done. The Kanban is the source of truth. The task note is the definition of done. The phase note is the scope boundary.

Order, and do not skip ahead:
- D11 T-018. Depends on T-123, which is Done.
- D12 T-124. Depends on T-018.
- D13 T-015, then T-016. T-016 depends on T-015. Both depend on T-124.
- D14 T-017. Depends on T-016.
- D15 T-019, then T-020. T-019 depends on T-017. T-020 depends on T-017.
- D16 T-125. Depends on T-019 and T-020. Inside T-125, one extractor file at a time, in the order below. Leave T-125 In progress until every named file is in the registry.

D11 slices, all inside T-018. Owner decisions of 2026-09-30 are closed:
- Netscape file only. Cap 1 MiB. Accept a leading "# Netscape HTTP Cookie File" or "# HTTP Cookie File", or a non-comment tab row with at least 7 fields. Reject anything else. No partial import. DesktopCookieStore is the reference.
- One file per device, cookies.txt, in app-private state. Replace overwrites. Delete removes the file. Jobs store DownloadOptions.useCookies only. Never store cookie text, names, values, or the user's original path on a job, in settings JSON, in logs, in history, in fixtures, or in this vault.
- No app-level cipher. Do not wrap cookies.txt with the Android Keystore, iOS Keychain, or a desktop cipher. Exclude the file from Android Auto Backup and iOS backup where the platform allows. Settings say a manual copy of app data includes the file. Record that sentence in T-018 Evidence and in the T-006 cookie section.
- Desktop may copy Chrome, Firefox, or Safari into that same file after a separate consent screen. The copy is one snapshot. Do not keep the browser database open and do not re-read it per request. Other desktop browsers are a recorded gap. Android, iOS, and web never read a browser database. Tests use a synthetic store with fake_session / fake_value, never a real profile. Decrypting the browser's own store may use that browser's OS secret. That is not a cipher for cookies.txt.
- Extractor header maps still cannot set Cookie, Authorization, or X-Goog auth headers. The jar sets Cookie through a trusted HttpRequest field added after HttpHeaders.sanitize, omitted from diagnostics.
- Unmatched URLs still use the desktop CLI or Android Chaquopy. When useCookies is set, the CLI still receives --cookies and the stored path as two arguments. Do not log that path. Do not remove either fallback.
- An in-flight request keeps the Cookie header it already built. Skip expired rows. A jar with no unexpired row is an error state. The message shows no names or values.

T-018 slices, in order:
1. Parser and jar in shared/core commonMain. Domain, include-subdomains, path, secure, expiry, name, value. Match host and path. Tests use synthetic text. No file I/O in commonMain.
2. Attach that header on extractor, media, and fragment requests when useCookies is true. Prove the job document and logs contain no cookie text. Keep the desktop CLI --cookies behavior for unmatched URLs.
3. Android and iOS CookieStore plus pickCookieFile, same validation as DesktopCookieStore, app-private storage, backup exclusions. Web: the extension holds the file; the page sees only status and does not fetch arbitrary origins. If the extension cannot store it, record the web gap and do not put the bytes on the page.
4. Desktop browser snapshot for Chrome, Firefox, and Safari, synthetic store only in the default tests.
5. Settings status Not configured, Configured, or Error, with no names, values, or paths. Replace, delete, deletion during active work, and restart from a synthetic file.
6. Verification. Run the new tests plus :shared:core:jvmTest and the host tests you can run. Update E-16 to the landed scope. Check T-018's boxes only where true. Set Phase 11 to done. Move T-018 to Done.

D12, T-124. Pin stays 2026.08.19. Account for the 21 Youtube* classes in the manifest. Translate the remaining innertube clients the pin uses, playlist continuations, channels, mixes, and --playlist-items, or mark a class Partial with the reason. Add a PO-token provider interface. Formats that need a token stay dropped until a provider is configured. Subtitle tracks and chapter metadata sit on the info dict. Do not write those files; that is D13. Live YouTube and live HLS are in. The playlist cap stays 50 unless the user sets an explicit higher limit. Harness cases cover the URL forms. The desktop oracle diffs normalized fields only. No cookie, token, or signed media URL is committed. iOS live network may stay a simulator gap; the fixture path is the evidence. :tools:port-manifest:check passes. Set Phase 12 to done when T-124 is Done.

D13. Web merge, embed, and clip fail typed. Desktop FFmpeg stays on PATH. Android MediaMuxer and iOS AVFoundation embed only what those APIs can do. Mobile MP3, WAV, and FLAC stay disabled. Do not look up Spotify. T-015: caption language and manual/auto/fallback, SRT, VTT, TTML, and TXT, JPG thumbnail sidecars, and embed or sidecar where the host toolkit can. T-016 depends on T-015: clip ranges, chapter split with names confined to the download root, and opt-in SponsorBlock. Unavailable markers and external calls are visible. Test cancellation and invalid ranges. Update E-17, E-18, E-19, and E-20 to the host table that landed. Set Phase 13 to done when T-015 and T-016 are Done.

D14, T-017. Typed presets overlay in order. The options for one attempt are immutable. Output templates stay inside the download root. The D8 safe-title name stays the default. A download-archive file sits beside app history. Proxy, rate limit, and sleep exist. A per-job free-form proxy string is rejected. Free-form yt-dlp JSON stays disabled. No shell, plugins, exec hooks, or path escape. Settings show the pinned tag and the Kotlin build. There is no self-update. An invalid reload keeps the last known-good configuration. Set Phase 14 to done when T-017 is Done.

D15. Subscriptions scan only while the app is open. The default first check marks current items seen and downloads nothing. Downloading the existing backlog requires an explicit choice on that subscription. Seen ids are capped. iOS and the web UI say that suspension stops a scan. T-019 is that scanner. T-020 is sharing: Android share, the iOS share sheet, desktop paste, and the existing web extension. No MeTube protocol client. No new companion integrations. F-24 and F-25 stay withdrawn. F-20 stays the allowlist difference. Set Phase 15 to done when T-019 and T-020 are Done.

D16, T-125. One file at a time, in this order. Each file gets harness cases from public or synthetic fixtures, a manifest row, and a registry entry before the next file starts. A login wall or DRM wall is Partial, with that sentence in the manifest, and it counts as finished. A matched URL on desktop and Android goes to Kotlin, not the CLI or Chaquopy. Unmatched URLs still fall through. No bearer token, guest token, cookie, or signed media URL in the repo.
1. Twitter remainder: cards, Amplify, broadcasts, Spaces, shortener.
2. Bilibili, Vimeo, PeerTube, BBC.
3. TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, archive.org.
When those thirteen files plus the Twitter remainder are in, :tools:port-manifest:check passes, Phase 16 is done, and T-125 moves to Done.

Rules for every card:
- No backend, app login, or MeTube protocol client. No ProcessBuilder or Python in common code.
- Do not vendor yt-dlp. Do not copy MeTube, NewPipe, or YtDlp-kt. A translation carries the Unlicense notice and the pin 2026.08.19. The pin does not move in this loop.
- Cookies, signed media URLs, bearer tokens, and private URLs stay out of logs, history, fixtures, and this vault. Synthetic fixtures only.
- Impersonation, jsinterp, plugins, exec hooks, and free-form options stay out.
- Do not commit a media file. Do not commit unless a task explicitly tells you to.
- Check acceptance boxes only where the statement is true. Write the date and the commands under Evidence. Move the card when its checklist passes.

Loop:
1. Read vault/Kanban.md. Consider only T-018, T-124, T-015, T-016, T-017, T-019, T-020, and T-125.
2. If any of those cards is In progress, resume it. Otherwise take the first card in the order above whose dependencies are Done.
3. If T-125 is Done, stop. List which of these eight cards are Done and which phase notes you marked done.
4. If the card you would take is Blocked, stop. Do not start the next card.
5. Move the chosen card to In progress before editing.
6. Read that task note and its phase note. Implement only that card. On T-018, implement only the next unfinished slice. On T-125, implement only the next unfinished file.
7. Run the verification that card names. On failure, fix it before leaving the card. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop.
8. When the card's criteria pass, check the boxes that are true, write Evidence, move the card to Done, and if it is the last card of its phase, set that phase note to done with the commands and the limits.
9. Go back to step 1. Do not start T-126, T-022, T-023, or T-132.

Work toward the goal. When T-125 is Done, call loop_control with status "done" and explain why. If a card or a T-125 file remains, call loop_control with status "next" and name it.
```

## Notes for the next session

- D9 and D10 are Done. The first eligible card is T-018, which is still Backlog.
- [Phase-11-Loop-prompt.md](Phase-11-Loop-prompt.md) is the D11-only prompt. This file replaces it for a session that continues through D16. Do not run both.
- Owner decisions for cookies, 2026-09-30, are copied into the prompt: app-private storage, and a desktop Chrome/Firefox/Safari snapshot. Mobile and web stay file-import only.
- D17 begins at [T-126](../06-tasks/T-126-Remaining-large-sites.md). This loop does not start it.
