---
type: prompt
milestone: D11
tags: [project, engine, cookies, handoff]
---

# Phase 11 loop prompt

[Phase 11](Phase-11-Cookies.md) · [Kanban](../Kanban.md) · [T-018](../06-tasks/T-018-Cookie-lifecycle.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

Paste the block below as the first message in a new agent session after D10 is done. It implements Phase D11, which is the single card [T-018](../06-tasks/T-018-Cookie-lifecycle.md), until that card is Done or blocked.

A session that continues through D16 uses [Phase-11-16-Loop-prompt.md](Phase-11-16-Loop-prompt.md) instead of this file. Do not paste both. Do not arm a short `/loop` interval against either prompt. A second wake will edit the same files.

## Owner decisions

Answered 2026-09-30. The prompt below is unblocked.

- Encryption-at-rest: app-private storage only. Do not encrypt `cookies.txt` with the Android Keystore, iOS Keychain, or a desktop cipher. Exclude the file from Android Auto Backup and from iOS backup where the platform API allows it. The settings copy says a manual copy of app data includes the file.
- Browser cookie databases: desktop only, opt-in, with its own consent screen. The user picks Chrome, Firefox, or Safari. The app copies that browser's cookies into the same `cookies.txt` snapshot and does not keep the browser database open or re-read it on each request. Other desktop browsers are a recorded gap. Android, iOS, and web stay file-import only. Decrypting the browser's own store may use that browser's OS secret; that is not encryption of AnyDownload's file. Tests use a synthetic store, never a real profile.

## Prompt

```text
Goal: Implement AnyDownload Phase D11 (on-device Netscape cookies), the single #D11 card T-018, until it is Done or blocked. Do not start T-124 or any later engine card. Do not arm a second short /loop against this prompt. D9 (every #metro card) and D10 (T-123 and T-133 through T-139) are already Done. The Kanban is the source of truth.

Gate, every wake: The owner decisions in vault/00-project/Phase-11-Loop-prompt.md are answered. Follow them. Do not reopen encryption or browser-store scope.

D11 attaches a Netscape cookie jar inside the shared HTTP helper. It does not add a site, a YouTube client, or PO tokens. Desktop already imports a cookie file for the yt-dlp CLI (DesktopCookieStore, T-035). This phase makes the same file available to Kotlin extractor and media requests, and gives Android, iOS, and web a store when the host can keep the bytes off the page.

Already decided. Do not reopen these:
- Netscape file only. Cap 1 MiB. Accept a leading "# Netscape HTTP Cookie File" or "# HTTP Cookie File", or a non-comment tab row with at least 7 fields. Reject anything else. No partial import. DesktopCookieStore is the reference.
- One file per device, fixed name cookies.txt, in that host's app-private state. Replace overwrites. Delete removes the file and clears the reference. Jobs store DownloadOptions.useCookies only. Never store cookie text, names, values, or the user's original path on a job, in settings JSON, in logs, in history, in fixtures, or in this vault.
- Consent is the Import action. The settings copy already says the file stays on this device and can expose an account. Do not promise that cookies grant access or authorize an unpermitted download.
- The add form keeps the use-cookies checkbox, default off, shown only when status is Configured.
- No sync between devices. No app login. No ProcessBuilder in common code. Do not vendor yt-dlp. Do not commit. Do not commit a media file. Pin stays 2026.08.19.
- Synthetic fixtures only. Use a fake name and a fake value such as fake_session / fake_value. Never a real account.
- Extractor-declared header maps still cannot set Cookie, Cookie2, Authorization, Proxy-Authorization, or X-Goog auth headers. The jar sets Cookie the way Spotify sets authorization: a trusted field on HttpRequest, added after HttpHeaders.sanitize, omitted from diagnostics. A blank field adds no header.
- URLs the registry does not match still use the desktop CLI or Android Chaquopy. When useCookies is set and a file exists, the CLI still receives --cookies and the stored path as two arguments. Do not log that path at info level. Do not remove either fallback.
- An in-flight request keeps the Cookie header it already built. A request started after delete sends none. Do not copy cookie bytes onto the job. Restart restores Configured from the file's presence, not from cookie text in the job document.
- When building a header, skip comments, blank lines, and rows whose expiry is in the past. A jar with no unexpired row is an error state, not Configured. The message says to replace the file and shows no names or values.
- Encryption-at-rest is app-private storage only. Do not add an Android Keystore, iOS Keychain, or desktop cipher around cookies.txt. Exclude that file from Android Auto Backup and from iOS backup where the platform API allows it. Say in the settings copy that a manual copy of app data includes the file. Record this sentence in T-018 Evidence and in the T-006 cookie section.
- Desktop browser databases are opt-in. A separate consent screen names Chrome, Firefox, or Safari and says the copy stays on this device and can expose an account. Copy the chosen browser's cookies into the same cookies.txt once. Do not hold the browser database open and do not re-read it per request. Other browsers are a gap, not a failed task. Android, iOS, and web never read a browser database. A synthetic store fixture is the test. A live read of this machine's browser is opt-in and must not put cookie values in Evidence. Using the browser's own OS secret to decrypt its database is allowed. That secret is not a cipher for cookies.txt.

Work T-018 in this order. Move the card to In progress before slice 1. Leave it there until slice 6 passes. Do not add new task ids.

1. Parser and jar in shared/core commonMain. Parse domain, include-subdomains, path, secure, expiry, name, and value. Match host and path. Build one Cookie header value. Tests use synthetic text. No file I/O in commonMain.
2. Attach that header on extractor, media, and fragment requests for a job whose useCookies is true and whose jar is usable. Prove the job document and any log or error string contain no cookie text. Keep the desktop CLI argument behavior for unmatched URLs.
3. Host file stores. Android and iOS implement CookieStore with the same validation as DesktopCookieStore, writing app-private storage, and implement pickCookieFile. Web: the extension holds the file; the page sees only status; the page does not fetch arbitrary origins and does not keep cookie bytes. If the extension cannot store the file, record that web gap in Evidence and do not put the bytes on the page. Apply the backup exclusions. No browser database on these hosts.
4. Desktop browser snapshot. After the extra consent screen, copy Chrome, Firefox, or Safari cookies into the same cookies.txt. Tests drive a synthetic store with fake_session / fake_value. Do not open the developer's real browser profile in the default test run.
5. Settings status is Not configured, Configured, or Error. Error covers unreadable and all-expired. No names, values, or paths on screen. Replace and delete work on every host that gained a store. The desktop browser action is separate from file import. A test covers deletion during active work and restart from a synthetic file.
6. Verification. Run the new tests plus :shared:core:jvmTest and the host tests you can run. Update E-16 in vault/01-product/Ytdlp-equivalence.md to the scope that landed: Netscape file on each host that stores one, desktop Chrome/Firefox/Safari snapshot, no browser database on mobile or web, no app-level cipher. Record platform limits in T-018 Evidence, including that cookies do not authorize an unpermitted download. Check T-018's boxes only where true. Set Phase 11 to done with the commands, counts, and limits. Move T-018 to Done.

Loop:
1. Read vault/Kanban.md. The only D11 card is T-018.
2. If T-018 is Done, stop. Summarize the slice results and the limits.
3. If T-018 is Blocked, stop. Do not start another card.
4. If T-018 is not In progress, move it to In progress.
5. Implement the next unfinished slice only. The slice list and T-018's acceptance checklist are the definition of done.
6. Run the verification that slice names. On failure, fix it before leaving the slice. If you cannot finish, move T-018 to Blocked, write the blocker and the next action under Evidence, and stop.
7. When a slice passes, note it under Evidence with the date and the commands you ran. When slice 6 passes, check the boxes that are true and move T-018 to Done.
8. Go back to step 1. Do not start T-124, T-015, or any other backlog card.

Work toward the goal. When T-018 is Done, call loop_control with status "done" and explain why. If a slice remains, call loop_control with status "next" and name the slice.
```

## Notes for the next session

- D9 (T-113–T-122) and D10 (T-123, T-133–T-139) are in Done. T-018 is the only Backlog card tagged `#D11`. [Home](../Home.md) and [Roadmap](Roadmap.md) point here.
- Desktop import, replace, delete, the 1 MiB Netscape check, and `--cookies` for the CLI already exist (`DesktopCookieStore`, settings Cookies section, `useCookies`). Android, iOS, and web still use `CookieStore.Unavailable`. `HttpHeaders` still refuses `Cookie` on the extractor header map.
- Owner decisions, 2026-09-30: app-private storage only, and a desktop Chrome/Firefox/Safari snapshot into `cookies.txt`. Mobile and web stay file-import only. The prompt is ready to paste.
