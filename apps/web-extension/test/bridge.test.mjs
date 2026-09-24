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

function makeBridge({ responses }) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, method: init?.method || 'GET' });
    const found = responses.find((r) => r.url === url && (r.method === (init?.method || 'GET') || !r.method));
    if (!found) throw new Error(`no fixture for ${url} ${init && init.method}`);
    return textResponse(found.body, { status: found.status, headers: found.headers, url: found.url });
  };
  const downloads = { download: async (opts) => { calls.push({ download: opts }); return true; } };
  const posted = [];
  const bridge = createBridge({ fetchImpl, downloads, post: (payload) => posted.push(payload), revokeDelayMs: 0 });
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