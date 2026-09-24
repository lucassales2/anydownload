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

## Tests (node, no browser needed)

```sh
node --test apps/web-extension/test/bridge.test.mjs
```

Covers the blocklist, filename sanitization, probe classification, streamed
download via a mock `chrome.downloads`, HTML refusal, and cancel.

## Store publication

Out of scope for D2. Sideload / unpacked load is the supported path.