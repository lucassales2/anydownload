# AnyDownload page bridge (Manifest V3)

The Compose/Wasm page (`apps/web`) never fetches a third-party origin. This
extension holds the `host_permissions` and the `downloads` permission; it
fetches a single already-classified direct-file URL and saves it with
`chrome.downloads`.

## Load the unpacked extension (Chromium)

1. Build the web app:

   ```sh
   ./gradlew :apps:web:wasmJsBrowserDistribution
   ```

2. Serve the output (from `apps/web/build/dist/wasmJs/...`):

   ```sh
   python3 -m http.server 8099 --directory apps/web/build/dist/wasmJs/developmentExecutable
   ```

   Open `http://127.0.0.1:8099/` to see the page.

3. Open `chrome://extensions` (or `brave://extensions`), enable **Developer
   mode**, press **Load unpacked**, and select the `apps/web-extension`
   folder (the one containing `manifest.json`).

4. Reload the page. The Settings/Add state becomes live only when the content
   script has marked the page (`window.__anydownloadExtension === true`).
   Without the extension, Add reports “extension required” and the page
   performs **no** cross-origin fetch.

## Manual click-through

Paste a direct-file URL (for example `https://example.com/files/tiny.bin`,
or a local fixture served from a non-loopback address) into Add, choose
Automatic, and submit. Progress appears in the queue; completion registers an
artifact, and the browser downloads the file to its default downloads folder.
A site/HTML URL fails with the same “extractor not implemented” error the iOS
host reports.

Notes:

- The blocklist on the page AND in the extension refuses loopback/private
  destinations (`127.0.0.1`, `localhost`, RFC1918, link-local, IPv6 loopback)
  before any fetch.
- The browser follows redirects transparently; the page re-validates the
  **final** URL. Intermediate redirect hops are not individually observable
  by web code — this is a recorded web limitation in the T-043 evidence.
- `host_permissions` are currently static `http://*/* https://*/*` (silent,
  no per-site prompt). Finer per-site optional permission prompting is a
  documented improvement, not a D2 blocker.
- Only 64-bit Chromium shells were exercised locally (Brave). Safari and
  Firefox MV2/MV3 differences are best-effort/out of scope for D2.

## Messages (page bridge)

The content script relays page messages to the service worker and back:

- `probe` — classify a URL and report the final response.
- `fetch-page` — bounded HTML read for the Kotlin extractor.
- `download` — hand a validated URL to `chrome.downloads`; the browser streams it.
- `fetch-request` (T-056) — one extractor request with method, allowlisted
  headers, an optional base64 body, and a byte range. The extension applies
  `checkUrl` before fetching and returns the response status, allowlisted
  response headers, bounded base64 body, and the effective request header set
  it actually sent. `Cookie`, `Authorization`, and `X-Goog-*` (except the
  visitor id) are refused before transport, and MV3 `fetch` itself refuses
  `Origin`, `Referer`, `User-Agent`, `Host`, `Content-Length`,
  `Accept-Encoding`, `Sec-*`, and `Proxy-*`, so a request that declares them
  reports them as not sent instead of pretending.
- `download` carries the selected format URL and its allowlisted headers to
  `chrome.downloads.download`; the browser's downloader owns the transfer and
  the progress, so the page shows unknown progress until completion.
- Matched formats (`saveViaBlob`) use the **offscreen saver** instead: MV3
  service workers cannot create Blob URLs, and a direct `chrome.downloads`
  fetch of a signed `googlevideo` URL is not reliable everywhere. `offscreen.html`
  fetches the media with the extension's host permissions, builds a `File`, and
  returns an extension-origin Blob URL; the service worker hands that URL to
  `chrome.downloads` (the downloads API is not exposed in offscreen documents).
  The page still never fetches. The browser names a blob-URL save with the blob
  UUID, so the saved file name can differ from the job's suggested name.

## YouTube innertube headers (declarativeNetRequest)

MV3 `fetch` always sets `Origin` to the extension origin for a cross-origin
request, and YouTube answers 403 to any origin that is not
`https://www.youtube.com` (verified with curl: `chrome-extension://…` and
`http://127.0.0.1:…` both 403; `https://www.youtube.com` returns 200).
`rules.json` therefore rewrites `Origin` and `User-Agent` on
`youtube.com/youtubei/*` XMLHttpRequest traffic through the
`declarativeNetRequest` permission. The rule is static, applies only to that
path, and never touches cookies or signed media URLs.
- `cancel` — cancel the browser download for a job.

## Tests (node, no browser needed)

```sh
node --test apps/web-extension/test/bridge.test.mjs
```

Covers the blocklist, filename sanitization, probe classification, streamed
download via a mock `chrome.downloads`, HTML refusal, cancel, and the T-056
`fetch-request` contract (method, allowlisted headers, base64 body, range,
effective header set, response allowlist, and the body cap).

## Store publication

Out of scope for D2. Sideload / unpacked load is the supported path.