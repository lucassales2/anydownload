import { createRequire } from 'node:module';
import test from 'node:test';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const { createBridge, checkUrl, fileNameFromUrl } = require('../background.js');

function textResponse(body, { status = 200, headers = {}, url = '' } = {}) {
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(body));
      controller.close();
    },
  });
  const response = new Response(stream, { status, headers: new Headers(headers) });
  if (url) Object.defineProperty(response, 'url', { value: url });
  return response;
}

function makeBridge({ responses, fetchMaxBytes, offscreen, runtime }) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, method: init?.method || 'GET', headers: init?.headers || {}, body: init?.body ?? null });
    const found = responses.find((r) => r.url === url && (r.method === (init?.method || 'GET') || !r.method));
    if (!found) throw new Error(`no fixture for ${url} ${init && init.method}`);
    return textResponse(found.body, { status: found.status, headers: found.headers, url: found.url });
  };
  const downloads = { download: async (opts) => { calls.push({ download: opts }); return true; } };
  const posted = [];
  const bridge = createBridge({ fetchImpl, downloads, post: (payload) => posted.push(payload), revokeDelayMs: 0, fetchMaxBytes, offscreen, runtime });
  return { bridge, calls, posted };
}

test('checkUrl allows public http(s) and blocks local destinations', () => {
  assert.equal(checkUrl('https://example.com/files/tiny.bin'), null);
  assert.equal(checkUrl('http://localhost/x'), 'blocked');
  assert.equal(checkUrl('http://127.0.0.1/x'), 'blocked');
  assert.equal(checkUrl('http://10.1.2.3/x'), 'blocked');
  assert.equal(checkUrl('http://192.168.0.5/x'), 'blocked');
  assert.equal(checkUrl('http://[::1]/x'), 'blocked');
  assert.equal(checkUrl('ftp://example.com/x'), 'blocked');
});

test('fileNameFromUrl uses a sanitized last path segment', () => {
  assert.equal(fileNameFromUrl('https://example.com/files/tiny.bin'), 'tiny.bin');
  assert.equal(fileNameFromUrl('https://example.com/a/b/video.mp4'), 'video.mp4');
  assert.equal(fileNameFromUrl('https://example.com/noext'), 'noext');
  assert.equal(fileNameFromUrl('https://example.com/danger-<>:-name.txt'), 'danger-___-name.txt');
  assert.equal(fileNameFromUrl('https://example.com/a b/<ok>.mp4'), '_ok_.mp4');
  assert.equal(fileNameFromUrl('https://example.com/..hidden.bin'), 'hidden.bin');
});

test('probe returns a final classification for a direct file', async () => {
  const { bridge, calls } = makeBridge({
    responses: [
      { url: 'https://example.com/files/tiny.bin', method: 'HEAD', status: 200, headers: { 'content-type': 'application/octet-stream', 'content-length': '1024' }, body: '' },
    ],
  });
  const reply = await bridge.handleMessage({ type: 'probe', requestId: 'r1', url: 'https://example.com/files/tiny.bin' });
  assert.equal(reply.type, 'probe-reply');
  assert.equal(reply.kind, 'final');
  assert.equal(reply.status, 200);
  assert.equal(reply.contentType, 'application/octet-stream');
  assert.equal(reply.totalBytes, 1024);
  assert.equal(calls[0].method, 'HEAD');
});

test('probe blocks a loopback URL without fetching', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({ type: 'probe', requestId: 'r2', url: 'http://127.0.0.1/evil' });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'blocked');
  assert.equal(calls.length, 0);
});

test('download hands the URL to chrome.downloads and completes', async () => {
  const { bridge, calls, posted } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({
    type: 'download',
    requestId: 'r3',
    jobId: 'job-1',
    url: 'https://example.com/files/tiny.bin',
  });
  assert.equal(reply.kind, 'completed');
  assert.equal(reply.fileName, 'tiny.bin');
  // The browser's downloader saves; the SW never reports byte counts, so the
  // page shows unknown progress and only then completion.
  const download = calls.find((c) => c.download);
  assert.equal(download.download.url, 'https://example.com/files/tiny.bin');
  assert.equal(download.download.filename, 'tiny.bin');
  assert.equal(posted[0].type, 'progress');
  assert.equal(posted[0].downloadedBytes, null);
});

test('cancel cancels the browser download and acks', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  let cancelledId;
  calls.length = 0;
  // Recreate with a downloads.cancel that records
  const downloads = {
    download: async (opts) => {
      calls.push({ download: opts });
      return 7;
    },
    cancel: async (id) => { cancelledId = id; },
  };
  const bridge2 = createBridge({ fetchImpl: async () => { throw new Error('unused'); }, downloads, post: () => {} });
  await bridge2.handleMessage({ type: 'download', requestId: 'r3', jobId: 'job-7', url: 'https://example.com/files/tiny.bin' });
  const reply = await bridge2.handleMessage({ type: 'cancel', jobId: 'job-7' });
  assert.equal(reply.type, 'ack');
  assert.equal(cancelledId, 7);
});

test('download blocks a loopback URL without saving', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({ type: 'download', requestId: 'r4', jobId: 'job-2', url: 'http://127.0.0.1/x.bin' });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'blocked');
  assert.ok(!calls.some((c) => c.download), 'a private address must never reach chrome.downloads');
});

test('cancel acknowledges without fetching', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({ type: 'cancel', jobId: 'job-9' });
  assert.equal(reply.type, 'ack');
  assert.equal(calls.length, 0);
});

test('fetch-page returns a bounded redacted page for the Kotlin extractor', async () => {
  const html =
    '<html><body><video src="https://cdn.fixtures.example.net/clip.bin"></video></body></html>';
  const { bridge, calls } = makeBridge({
    responses: [
      { url: 'https://example.com/watch', method: 'GET', status: 200, headers: { 'content-type': 'text/html; charset=utf-8' }, body: html },
    ],
  });
  const reply = await bridge.handleMessage({ type: 'fetch-page', requestId: 'r9', url: 'https://example.com/watch' });
  assert.equal(reply.type, 'fetch-page-reply');
  assert.equal(reply.kind, 'final');
  assert.equal(reply.finalUrl, 'https://example.com/watch');
  assert.equal(reply.html, html);
  assert.equal(calls[0].method, 'GET');
});

test('fetch-page caps the page bytes and never holds a huge page', async () => {
  const huge = 'x'.repeat(2 * 512 * 1024);
  const { bridge } = makeBridge({
    responses: [
      { url: 'https://example.com/huge', method: 'GET', status: 200, headers: { 'content-type': 'text/html' }, body: huge },
    ],
  });
  const reply = await bridge.handleMessage({ type: 'fetch-page', requestId: 'r10', url: 'https://example.com/huge' });
  assert.equal(reply.kind, 'final');
  assert.equal(reply.bounded, true);
  assert.equal(reply.html.length, 512 * 1024);
});

test('fetch-page blocks a loopback URL without fetching', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({ type: 'fetch-page', requestId: 'r11', url: 'http://127.0.0.1/watch' });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'blocked');
  assert.equal(calls.length, 0);
});

test('fetch-page reports a non-ok page as failed without bytes', async () => {
  const { bridge } = makeBridge({
    responses: [
      { url: 'https://example.com/gone', method: 'GET', status: 404, body: '' },
    ],
  });
  const reply = await bridge.handleMessage({ type: 'fetch-page', requestId: 'r12', url: 'https://example.com/gone' });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'network');
  assert.equal(reply.html, undefined);
});

// ------------------------------------------------------------- T-056 requests

test('fetch-request carries method, allowlisted headers, range, and base64 body', async () => {
  const requestBody = '{"videoId":"fixture"}';
  const { bridge, calls } = makeBridge({
    responses: [
      {
        url: 'https://www.youtube.com/youtubei/v1/player',
        method: 'POST',
        status: 200,
        headers: { 'content-type': 'application/json' },
        body: '{"ok":true}',
      },
    ],
  });
  const reply = await bridge.handleMessage({
    type: 'fetch-request',
    requestId: 'f1',
    url: 'https://www.youtube.com/youtubei/v1/player',
    method: 'POST',
    headers: {
      'X-YouTube-Client-Name': '101',
      'x-youtube-client-version': '1.02',
      cookie: 'session=secret',
      'user-agent': 'fixture-agent',
      origin: 'https://www.youtube.com',
      'x-custom': 'value',
    },
    range: 'bytes=0-1023',
    bodyBase64: Buffer.from(requestBody, 'utf8').toString('base64'),
  });

  assert.equal(reply.type, 'fetch-request-reply');
  assert.equal(reply.kind, 'final');
  assert.equal(reply.status, 200);
  assert.equal(calls[0].method, 'POST');
  assert.equal(calls[0].headers['x-youtube-client-name'], '101');
  assert.equal(calls[0].headers['x-youtube-client-version'], '1.02');
  assert.equal(calls[0].headers.range, 'bytes=0-1023');
  assert.equal(calls[0].headers.cookie, undefined, 'a refused header must never leave the extension');
  assert.equal(calls[0].headers['x-custom'], undefined);
  assert.equal(calls[0].headers['user-agent'], undefined, 'MV3 refuses User-Agent');
  assert.equal(calls[0].headers.origin, undefined, 'MV3 refuses Origin');
  assert.equal(Buffer.from(calls[0].body).toString('utf8'), requestBody);
  assert.equal(reply.sentHeaders['x-youtube-client-name'], '101');
  assert.equal(reply.sentHeaders['user-agent'], undefined);
  assert.equal(reply.sentHeaders.origin, undefined);
  assert.equal(reply.bodyBase64, Buffer.from('{"ok":true}').toString('base64'));
  assert.equal(reply.finalUrl, 'https://www.youtube.com/youtubei/v1/player');
});

test('fetch-request filters response headers and reports a ranged total', async () => {
  const { bridge } = makeBridge({
    responses: [
      {
        url: 'https://example.com/files/ranged.bin',
        method: 'GET',
        status: 206,
        headers: {
          'content-type': 'application/octet-stream',
          'content-range': 'bytes 0-1/4096',
          'set-cookie': 'session=secret',
          'x-custom': 'value',
        },
        body: 'ok',
      },
    ],
  });
  const reply = await bridge.handleMessage({
    type: 'fetch-request',
    requestId: 'f2',
    url: 'https://example.com/files/ranged.bin',
    headers: { Range: 'bytes=0-1' },
  });
  assert.equal(reply.kind, 'final');
  assert.equal(reply.status, 206);
  assert.equal(reply.contentRange, 'bytes 0-1/4096');
  assert.equal(reply.totalBytes, 4096);
  assert.equal(reply.responseHeaders['content-range'], 'bytes 0-1/4096');
  assert.equal(reply.responseHeaders['set-cookie'], undefined);
  assert.equal(reply.responseHeaders['x-custom'], undefined);
});

test('fetch-request blocks a loopback URL without fetching', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  const reply = await bridge.handleMessage({
    type: 'fetch-request',
    requestId: 'f3',
    url: 'http://127.0.0.1/api',
    method: 'POST',
  });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'blocked');
  assert.equal(calls.length, 0);
});

test('fetch-request fails typed when the response exceeds the port cap', async () => {
  const { bridge } = makeBridge({
    fetchMaxBytes: 4,
    responses: [
      { url: 'https://example.com/big', method: 'GET', status: 200, headers: { 'content-type': 'application/json' }, body: 'x'.repeat(10) },
    ],
  });
  const reply = await bridge.handleMessage({ type: 'fetch-request', requestId: 'f4', url: 'https://example.com/big' });
  assert.equal(reply.kind, 'failed');
  assert.equal(reply.code, 'other');
  assert.equal(reply.bodyBase64, undefined);
});

test('fetch-request carries the X syndication lookup without a cookie or authorization', async () => {
  const lookupUrl = 'https://cdn.syndication.twimg.com/tweet-result?id=9999999999999999999&token=REDACTED';
  const { bridge, calls } = makeBridge({
    responses: [
      { url: lookupUrl, method: 'GET', status: 200, headers: { 'content-type': 'application/json' }, body: '{"__typename":"Tweet"}' },
    ],
  });
  const reply = await bridge.handleMessage({
    type: 'fetch-request',
    requestId: 'x1',
    url: lookupUrl,
    headers: { 'User-Agent': 'Googlebot', cookie: 'session=secret' },
  });
  assert.equal(reply.type, 'fetch-request-reply');
  assert.equal(reply.kind, 'final');
  assert.equal(reply.status, 200);
  assert.equal(calls[0].url, lookupUrl);
  assert.equal(calls[0].headers.cookie, undefined, 'a cookie must never ride the guest lookup');
  assert.equal(calls[0].headers['user-agent'], undefined, 'MV3 refuses User-Agent');
  assert.equal(reply.sentHeaders.cookie, undefined);
  assert.equal(reply.sentHeaders['user-agent'], undefined);
  assert.equal(reply.sentHeaders.authorization, undefined);
});

test('sanitizeRequestHeaders and effectiveRequestHeaders agree on the allowlist', () => {
  const { sanitizeRequestHeaders, effectiveRequestHeaders } = createRequire(import.meta.url)('../background.js');
  const requested = sanitizeRequestHeaders({
    Accept: '*/*',
    Authorization: 'Bearer fixture',
    Cookie: 'secret',
    'User-Agent': 'agent',
    'X-Goog-Api-Key': 'secret',
  });
  assert.deepEqual(requested, { accept: '*/*', authorization: 'Bearer fixture', 'user-agent': 'agent' });
  assert.deepEqual(effectiveRequestHeaders(requested), { accept: '*/*', authorization: 'Bearer fixture' });
});

test('download forwards allowlisted format headers and refuses the rest', async () => {
  const { bridge, calls } = makeBridge({ responses: [] });
  await bridge.handleMessage({
    type: 'download',
    requestId: 'r20',
    jobId: 'job-20',
    url: 'https://example.com/files/tiny.bin',
    headers: {
      Accept: '*/*',
      Referer: 'https://example.com/',
      Cookie: 'session=secret',
      'User-Agent': 'fixture-agent',
    },
  });
  const download = calls.find((c) => c.download);
  assert.equal(download.download.headers.accept, '*/*');
  assert.equal(download.download.headers.cookie, undefined);
  assert.equal(download.download.headers.referer, undefined, 'MV3 refuses Referer');
  assert.equal(download.download.headers['user-agent'], undefined, 'MV3 refuses User-Agent');
});

test('download with saveViaBlob uses the offscreen saver', async () => {
  const offscreenCalls = [];
  const runtimeCalls = [];
  const downloadCalls = [];
  const { createBridge } = createRequire(import.meta.url)('../background.js');
  const bridge = createBridge({
    fetchImpl: async () => { throw new Error('unused'); },
    downloads: { download: async (options) => { downloadCalls.push(options); return 1; } },
    post: () => {},
    offscreen: { createDocument: async (options) => { offscreenCalls.push(options); } },
    runtime: { sendMessage: async (message) => { runtimeCalls.push(message); return { ok: true, blobUrl: 'blob:chrome-extension://fixture/blob', sizeBytes: 4096 }; } },
  });
  const reply = await bridge.handleMessage({
    type: 'download',
    requestId: 'r21',
    jobId: 'job-21',
    url: 'https://cdn.fixtures.example.net/audio.m4a',
    headers: { Accept: '*/*', Cookie: 'secret' },
    saveViaBlob: true,
  });
  assert.equal(reply.kind, 'completed');
  assert.equal(reply.sizeBytes, 4096);
  assert.equal(downloadCalls[0].url, 'blob:chrome-extension://fixture/blob');
  assert.equal(offscreenCalls[0].url, 'offscreen.html');
  assert.equal(runtimeCalls[0].target, 'anydownload-offscreen');
  assert.equal(runtimeCalls[0].headers.accept, '*/*');
  assert.equal(runtimeCalls[0].headers.cookie, undefined);
});

test('offscreen save fetches the media and returns an extension Blob URL', async () => {
  const { save } = createRequire(import.meta.url)('../offscreen.js');
  globalThis.fetch = async () => new Response(new Uint8Array([1, 2, 3, 4]), {
    status: 200,
    headers: { 'content-type': 'audio/mp4' },
  });
  const result = await save({ url: 'https://cdn.fixtures.example.net/audio.m4a', fileName: 'audio.m4a', headers: {} });
  assert.equal(result.ok, true);
  assert.equal(result.sizeBytes, 4);
  assert.ok(result.blobUrl.startsWith('blob:'), result.blobUrl);
});

test('the manifest rewrites the YouTube innertube origin and user agent', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const dir = path.dirname(new URL(import.meta.url).pathname);
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, '../manifest.json'), 'utf8'));
  assert.ok(manifest.permissions.includes('declarativeNetRequest'));
  const resource = manifest.declarative_net_request.rule_resources.find((r) => r.id === 'youtube_headers');
  assert.ok(resource && resource.enabled);
  const rules = JSON.parse(fs.readFileSync(path.join(dir, '../rules.json'), 'utf8'));
  assert.equal(rules[0].condition.urlFilter, '||youtube.com/youtubei/');
  const headers = rules[0].action.requestHeaders;
  assert.equal(headers.find((h) => h.header === 'origin').value, 'https://www.youtube.com');
  assert.ok(headers.find((h) => h.header === 'user-agent').value.includes('Macintosh'));
});

test('the Compose/Wasm page source performs no fetch call', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../..');
  const roots = [
    path.join(repoRoot, 'apps/web/src/wasmJsMain'),
    path.join(repoRoot, 'shared/ui/src'),
  ];
  const offenders = [];
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'build') continue;
        visit(full);
      } else if (entry.name.endsWith('.kt')) {
        const source = fs.readFileSync(full, 'utf8').replace(/fun\s+fetch\s*\(/g, '');
        if (/fetch\s*\(/.test(source)) offenders.push(full);
      }
    }
  };
  for (const root of roots) visit(root);
  assert.deepEqual(offenders, [], 'the page must never call fetch itself');
});

test('the page source never fetches an X or Twitter origin', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../..');
  const roots = [
    path.join(repoRoot, 'apps/web/src/wasmJsMain'),
    path.join(repoRoot, 'shared/ui/src'),
  ];
  const origin = /(?:x\.com|twitter\.com|syndication\.twimg\.com)/;
  const offenders = [];
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'build') continue;
        visit(full);
      } else if (entry.name.endsWith('.kt')) {
        const source = fs.readFileSync(full, 'utf8');
        for (const match of source.matchAll(/fetch\s*\([^)]*\)/g)) {
          if (origin.test(match[0])) offenders.push(full);
        }
      }
    }
  };
  for (const root of roots) visit(root);
  assert.deepEqual(offenders, [], 'the page must not fetch X or Twitter itself');
});

test('the web page ships no process or platform muxer', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../..');
  const roots = [
    path.join(repoRoot, 'apps/web/src/wasmJsMain'),
    path.join(repoRoot, 'shared/ui/src/commonMain'),
    path.join(repoRoot, 'shared/ui/src/wasmJsMain'),
    path.join(repoRoot, 'shared/core/src/commonMain'),
  ];
  const pattern = /\b(ProcessBuilder|MediaMuxer|MediaExtractor|AVFoundation)\b/;
  const offenders = [];
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'build') continue;
        visit(full);
      } else if (entry.name.endsWith('.kt')) {
        if (pattern.test(fs.readFileSync(full, 'utf8'))) offenders.push(full);
      }
    }
  };
  for (const root of roots) visit(root);
  assert.deepEqual(offenders, [], 'the web page must not ship a process or platform muxer');
});