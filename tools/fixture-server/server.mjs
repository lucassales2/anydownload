// Local fixture server for the opt-in live checks (desktop, Android, iOS).
//
// T-042/T-049 used `python3 -m http.server`, which only serves GET and
// ignores ranges. T-056 needs POST + request headers + byte ranges, so this
// small Node server serves the same paths plus:
//   - POST /echo                     echoes the body, reports the
//                                    Content-Type it received, and reflects
//                                    x-youtube-client-name in ETag (an
//                                    allowlisted response header, so the
//                                    live test can prove the declared header
//                                    arrived)
//   - any file path                  honors `Range: bytes=a-b` with 206 and
//                                    Content-Range
//
// Usage:
//   node tools/fixture-server/server.mjs --port 8123 [--bind 0.0.0.0]
//
// It prints one line per request (method, path, range) so a live run can be
// checked from the terminal. It is test tooling: no upstream code, no media,
// no cookies.

import http from 'node:http';

function arg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const port = Number(arg('port', '8123'));
const bind = arg('bind', '0.0.0.0');

const FILE_BYTES = 4096;
const WATCH_HTML = '<html><body><video src="/media/clip.bin"></video></body></html>';
const NO_MEDIA_HTML = '<html><body><p>No media here.</p></body></html>';

function fileBytes() {
  const buffer = Buffer.alloc(FILE_BYTES);
  for (let i = 0; i < FILE_BYTES; i++) buffer[i] = i % 251;
  return buffer;
}

function parseRange(headerValue, size) {
  if (typeof headerValue !== 'string') return null;
  const match = /^bytes=(\d*)-(\d*)$/.exec(headerValue.trim());
  if (!match) return null;
  const start = match[1] === '' ? 0 : Number(match[1]);
  const end = match[2] === '' ? size - 1 : Number(match[2]);
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end || start >= size) return null;
  return { start, end: Math.min(end, size - 1) };
}

function sendBytes(request, response, bytes, contentType) {
  const range = parseRange(request.headers.range, bytes.length);
  if (range) {
    const slice = bytes.subarray(range.start, range.end + 1);
    response.writeHead(206, {
      'Content-Type': contentType,
      'Accept-Ranges': 'bytes',
      'Content-Range': `bytes ${range.start}-${range.end}/${bytes.length}`,
      'Content-Length': slice.length,
    });
    response.end(slice);
    return;
  }
  response.writeHead(200, {
    'Content-Type': contentType,
    'Accept-Ranges': 'bytes',
    'Content-Length': bytes.length,
  });
  response.end(bytes);
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  const range = request.headers.range ? ` range=${request.headers.range}` : '';
  console.log(`${request.method} ${url.pathname}${range}`);

  if (request.method === 'HEAD') {
    response.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': FILE_BYTES });
    response.end();
    return;
  }

  if (request.method === 'POST' && url.pathname === '/echo') {
    const body = await readBody(request);
    const contentType = request.headers['content-type'] || 'application/octet-stream';
    response.writeHead(200, {
      'Content-Type': contentType,
      'ETag': `"${request.headers['x-youtube-client-name'] || 'none'}"`,
      'Content-Length': body.length,
    });
    response.end(body);
    return;
  }

  if (request.method !== 'GET') {
    response.writeHead(405, { 'Content-Type': 'text/plain' });
    response.end('method not allowed');
    return;
  }

  switch (url.pathname) {
    case '/files/tiny.bin':
      sendBytes(request, response, fileBytes(), 'application/octet-stream');
      return;
    case '/media/clip.bin':
      sendBytes(request, response, fileBytes(), 'application/octet-stream');
      return;
    case '/watch':
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      response.end(WATCH_HTML);
      return;
    case '/page-without-media':
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      response.end(NO_MEDIA_HTML);
      return;
    default:
      response.writeHead(404, { 'Content-Type': 'text/plain' });
      response.end('not found');
  }
});

server.listen(port, bind, () => {
  console.log(`fixture server on http://${bind}:${port}`);
});
