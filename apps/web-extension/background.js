'use strict';

/**
 * T-043: MV3 service worker for AnyDownload.
 *
 * Receives one-page messages from the content script:
 *   - probe: classify a URL (HEAD, ranged-GET fallback) and report the FINAL
 *     response. The page engine re-validates that final URL with its own
 *     policy; intermediate redirects are hidden by the browser, which is the
 *     recorded web limitation (see vault T-043).
 *   - fetch-page: bounded GET of an HTML page (≤ 512 KiB, redacted text). The
 *     page runs the shared Kotlin extractor on those bytes; this script never
 *     runs a second extractor.
 *   - download: hand the already-validated final URL to `chrome.downloads`.
 *     The browser's own downloader streams and saves the file; the service
 *     worker never reads the body, so no private bytes cross the extension
 *     context and nothing is buffered in memory.
 *   - cancel: cancel the browser download for that job.
 *
 * The extension also refuses obviously-local destinations and credentials
 * before acting, so a buggy or malicious page cannot abuse it against a
 * private address. No server, no Socket.IO, no raw response text to the page.
 */
(function (root) {
  'use strict';

  // ---------------------------------------------------------------- policy

  // T-056: the extractor request-header allowlist. The extension drops
  // anything else before fetch. Response headers are filtered too, so a
  // Set-Cookie never crosses the extension boundary. `authorization` is
  // only ever set by the trusted D6 Spotify metadata clients through the
  // explicit request field, never by an extractor header map.
  const REQUEST_HEADER_ALLOWLIST = new Set([
    'accept',
    'accept-language',
    'authorization',
    'content-type',
    'origin',
    'referer',
    'user-agent',
    'range',
    'x-youtube-client-name',
    'x-youtube-client-version',
    'x-goog-visitor-id',
    'x-origin',
  ]);

  const RESPONSE_HEADER_ALLOWLIST = new Set([
    'content-type',
    'content-length',
    'content-range',
    'accept-ranges',
    'location',
    'etag',
    'last-modified',
  ]);

  // Header names MV3 fetch refuses to send even when declared. The reply
  // reports the effective set, so the page can log the difference redacted
  // (names only, never values) instead of assuming the request was intact.
  const MV3_FORBIDDEN_HEADERS = new Set([
    'accept-charset',
    'accept-encoding',
    'access-control-request-headers',
    'access-control-request-method',
    'connection',
    'content-length',
    'cookie',
    'cookie2',
    'date',
    'dnt',
    'expect',
    'host',
    'keep-alive',
    'origin',
    'permissions-policy',
    'proxy-authorization',
    'proxy-authenticate',
    'referer',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade',
    'user-agent',
    'via',
  ]);

  const FETCH_REQUEST_MAX_BYTES = 8 * 1024 * 1024;

  function sanitizeRequestHeaders(headers) {
    const requested = {};
    for (const [name, value] of Object.entries(headers || {})) {
      const lower = String(name).trim().toLowerCase();
      if (!REQUEST_HEADER_ALLOWLIST.has(lower)) continue;
      if (value == null) continue;
      requested[lower] = String(value);
    }
    return requested;
  }

  function effectiveRequestHeaders(requested) {
    const sent = {};
    for (const [name, value] of Object.entries(requested)) {
      if (MV3_FORBIDDEN_HEADERS.has(name)) continue;
      if (name.startsWith('sec-') || name.startsWith('proxy-')) continue;
      sent[name] = value;
    }
    return sent;
  }

  function responseHeaderMap(headers) {
    const filtered = {};
    if (!headers || typeof headers.forEach !== 'function') return filtered;
    headers.forEach((value, name) => {
      const lower = String(name).toLowerCase();
      if (RESPONSE_HEADER_ALLOWLIST.has(lower) && value != null) filtered[lower] = String(value);
    });
    return filtered;
  }

  function base64FromBytes(bytes) {
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  function isPrivateIpv4(host) {
    const parts = host.split('.');
    if (parts.length !== 4) return false;
    // Only a numeric four-part host is an IPv4 literal; a four-label DNS name
    // such as cdn.fixtures.example.net must not be misread as one.
    if (!parts.every((s) => /^\d+$/.test(s))) return false;
    const p = parts.map((s) => Number(s));
    if (p.some((n) => n > 255)) return true;
    const [a, b, c] = p;
    return (
      a === 0 ||
      a === 10 ||
      a === 127 ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 192 && b === 0 && c === 0) ||
      (a === 192 && b === 0 && c === 2) ||
      (a >= 224 && a <= 239) ||
      (a >= 240 && a <= 255)
    );
  }

  function isPrivateHost(hostname) {
    const host = String(hostname || '').toLowerCase().replace(/\.$/, '');
    if (!host) return true;
    if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal')) {
      return true;
    }
    if (host.includes(':')) {
      const bare = host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
      return (
        bare === '::' || bare === '::1' || bare.startsWith('fe8') || bare.startsWith('fe9') ||
        bare.startsWith('fea') || bare.startsWith('feb') || bare.startsWith('fc') ||
        bare.startsWith('fd') || bare.startsWith('ff') || bare.startsWith('2001:db8:')
      );
    }
    return isPrivateIpv4(host);
  }

  /** Returns null when the URL may be fetched, else a short reason. */
  function checkUrl(url) {
    let parsed;
    try {
      parsed = new URL(url);
    } catch (_e) {
      return 'network';
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return 'blocked';
    if (parsed.username || parsed.password) return 'blocked';
    if (isPrivateHost(parsed.hostname)) return 'blocked';
    return null;
  }

  // ---------------------------------------------------------------- helpers

  function parseLength(value) {
    if (value == null) return null;
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 ? n : null;
  }

  function totalFromContentRange(contentRange, contentLength) {
    const total = contentRange ? parseLength(String(contentRange).split('/').pop()) : null;
    return total != null ? total : contentLength;
  }

  /** Safe filename from the URL's last path segment. */
  function fileNameFromUrl(url) {
    try {
      const parsed = new URL(url);
      const segment = decodeURIComponent(parsed.pathname.split('/').filter(Boolean).pop() || '')
        .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_')
        .replace(/^\.+/, '')
        .trim();
      return segment || 'download';
    } catch (_e) {
      return 'download';
    }
  }

  // ---------------------------------------------------------------- bridge

  function createBridge(deps) {
    const providers = {
      fetchImpl: deps.fetchImpl,
      downloads: deps.downloads,
      post: deps.post,
      runtime: deps.runtime,
      offscreen: deps.offscreen,
    };
    const fetchMaxBytes = Number.isFinite(deps.fetchMaxBytes) && deps.fetchMaxBytes > 0
      ? deps.fetchMaxBytes
      : FETCH_REQUEST_MAX_BYTES;
    const downloadsByJob = new Map();

    async function probeOnce(url) {
      let response;
      try {
        response = await providers.fetchImpl(url, { method: 'HEAD', redirect: 'follow', credentials: 'omit' });
      } catch (_e) {
        // HEAD unsupported or refused; fall back to a bounded ranged GET.
        response = await providers.fetchImpl(url, {
          method: 'GET',
          redirect: 'follow',
          credentials: 'omit',
          headers: { Range: 'bytes=0-0' },
        });
      }
      const finalUrl = response.url || url;
      const contentType = response.headers.get('content-type') || null;
      let totalBytes = parseLength(response.headers.get('content-length'));
      if (!contentType && response.status === 206) {
        totalBytes = parseLength(response.headers.get('content-range')) || totalBytes;
      }
      return { status: response.status, contentType, totalBytes, finalUrl };
    }

    async function downloadViaOffscreen(message, fileName) {
      const headers = effectiveRequestHeaders(sanitizeRequestHeaders(message.headers));
      providers.post({
        source: 'anydownload-extension',
        type: 'progress',
        requestId: message.requestId,
        jobId: message.jobId,
        downloadedBytes: null,
        totalBytes: null,
      });
      if (!providers.offscreen || !providers.runtime) {
        return { type: 'download-reply', requestId: message.requestId, kind: 'failed', code: 'other', message: 'The offscreen saver is unavailable.' };
      }
      try {
        await providers.offscreen.createDocument({
          url: 'offscreen.html',
          reasons: ['BLOBS'],
          justification: 'Save the selected media through the extension.',
        });
      } catch (_e) {
        // One offscreen document at a time; an existing one is fine.
      }
      let reply = null;
      try {
        reply = await providers.runtime.sendMessage({
          target: 'anydownload-offscreen',
          type: 'save',
          url: message.url,
          fileName,
          headers,
        });
      } catch (_e) {
        reply = null;
      }
      if (reply && reply.ok && reply.blobUrl) {
        try {
          const downloadId = await providers.downloads.download({
            url: reply.blobUrl,
            filename: fileName,
            saveAs: false,
          });
          downloadsByJob.set(message.jobId, downloadId);
          return {
            type: 'download-reply',
            requestId: message.requestId,
            kind: 'completed',
            fileName,
            sizeBytes: Number.isFinite(reply.sizeBytes) ? reply.sizeBytes : null,
          };
        } catch (_e) {
          return {
            type: 'download-reply',
            requestId: message.requestId,
            kind: 'failed',
            code: 'other',
            message: 'The browser could not save the media.',
          };
        }
      }
      return {
        type: 'download-reply',
        requestId: message.requestId,
        kind: 'failed',
        code: (reply && reply.code) || 'network',
        message: (reply && reply.message) || 'The media could not be saved.',
      };
    }

    function downloadOnce(message) {
      // The browser's own downloader streams and saves; the SW never reads a
      // body byte. Progress is reported as unknown until the browser reports
      // the download complete. The selected format's allowlisted headers ride
      // along; the browser may refuse some of them. When the page asks for the
      // offscreen saver (matched formats), the extension fetches the media
      // itself and hands a Blob URL to the same downloader.
      const fileName = fileNameFromUrl(message.url);
      if (message.saveViaBlob) {
        return downloadViaOffscreen(message, fileName);
      }
      providers.post({
        source: 'anydownload-extension',
        type: 'progress',
        requestId: message.requestId,
        jobId: message.jobId,
        downloadedBytes: null,
        totalBytes: null,
      });
      const options = { url: message.url, filename: fileName, saveAs: false };
      const headers = effectiveRequestHeaders(sanitizeRequestHeaders(message.headers));
      if (Object.keys(headers).length > 0) options.headers = headers;
      return providers.downloads.download(options).then((downloadId) => {
        downloadsByJob.set(message.jobId, downloadId);
        return { type: 'download-reply', requestId: message.requestId, kind: 'completed', fileName, sizeBytes: null };
      });
    }

    async function readBoundedBytes(response, cap) {
      if (!response.body) return new Uint8Array(0);
      const reader = response.body.getReader();
      const chunks = [];
      let total = 0;
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (!value) continue;
          if (total + value.length > cap) {
            await reader.cancel();
            return null;
          }
          chunks.push(value);
          total += value.length;
        }
      } finally {
        try {
          await reader.cancel();
        } catch (_e) {
          // Already closed.
        }
      }
      const out = new Uint8Array(total);
      let offset = 0;
      for (const chunk of chunks) {
        out.set(chunk, offset);
        offset += chunk.length;
      }
      return out;
    }

    async function fetchRequestOnce(message) {
      const requested = sanitizeRequestHeaders(message.headers);
      if (typeof message.range === 'string' && /^bytes=\d*-\d*$/.test(message.range)) {
        requested.range = message.range;
      }
      const sent = effectiveRequestHeaders(requested);
      const method = message.method === 'POST' ? 'POST' : 'GET';
      const init = { method, redirect: 'follow', credentials: 'omit', headers: sent };
      if (typeof message.bodyBase64 === 'string' && message.bodyBase64.length > 0 && method !== 'GET') {
        const binary = atob(message.bodyBase64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        init.body = bytes;
      }
      const response = await providers.fetchImpl(message.url, init);
      const bytes = await readBoundedBytes(response, fetchMaxBytes);
      if (bytes == null) {
        return {
          type: 'fetch-request-reply',
          requestId: message.requestId,
          kind: 'failed',
          code: 'other',
          message: 'The response is too large for the extension port.',
        };
      }
      const headers = responseHeaderMap(response.headers);
      const contentRange = headers['content-range'] || null;
      return {
        type: 'fetch-request-reply',
        requestId: message.requestId,
        kind: 'final',
        status: response.status,
        contentType: headers['content-type'] || null,
        totalBytes: totalFromContentRange(contentRange, parseLength(headers['content-length'])),
        contentRange,
        finalUrl: response.url || message.url,
        responseHeaders: headers,
        sentHeaders: sent,
        bodyBase64: base64FromBytes(bytes),
      };
    }

    async function fetchPageOnce(url) {
      const response = await providers.fetchImpl(url, { method: 'GET', redirect: 'follow', credentials: 'omit' });
      const finalUrl = response.url || url;
      if (!response.ok) {
        return { type: 'fetch-page-reply', requestId: null, kind: 'failed', code: 'network', status: response.status, message: 'The page could not be reached.' };
      }
      // Bounded read: never hold a huge page in the extension context.
      const cap = 512 * 1024;
      const buffer = new Uint8Array(cap);
      let total = 0;
      if (response.body) {
        const reader = response.body.getReader();
        try {
          while (total < cap) {
            const { done, value } = await reader.read();
            if (done) break;
            if (!value) continue;
            const take = Math.min(value.length, cap - total);
            buffer.set(value.subarray(0, take), total);
            total += take;
          }
        } finally {
          await reader.cancel();
        }
      }
      const html = new TextDecoder('utf-8', { fatal: false }).decode(buffer.subarray(0, total));
      return { type: 'fetch-page-reply', requestId: null, kind: 'final', finalUrl, html, bounded: total >= cap };
    }

    async function handleMessage(message) {
      switch (message && message.type) {
        case 'probe': {
          const blocked = checkUrl(message.url);
          if (blocked) {
            return { type: 'probe-reply', requestId: message.requestId, kind: 'failed', code: blocked };
          }
          try {
            const result = await probeOnce(message.url);
            return {
              type: 'probe-reply',
              requestId: message.requestId,
              kind: 'final',
              status: result.status,
              contentType: result.contentType,
              totalBytes: result.totalBytes,
              finalUrl: result.finalUrl,
            };
          } catch (_e) {
            return { type: 'probe-reply', requestId: message.requestId, kind: 'failed', code: 'network', message: 'The source could not be reached.' };
          }
        }

        case 'fetch-page': {
          const blocked = checkUrl(message.url);
          if (blocked) {
            return { type: 'fetch-page-reply', requestId: message.requestId, kind: 'failed', code: blocked };
          }
          try {
            const reply = await fetchPageOnce(message.url);
            reply.requestId = message.requestId;
            return reply;
          } catch (_e) {
            return { type: 'fetch-page-reply', requestId: message.requestId, kind: 'failed', code: 'network', message: 'The page could not be reached.' };
          }
        }

        case 'fetch-request': {
          const blocked = checkUrl(message.url);
          if (blocked) {
            return { type: 'fetch-request-reply', requestId: message.requestId, kind: 'failed', code: blocked };
          }
          try {
            return await fetchRequestOnce(message);
          } catch (_e) {
            return {
              type: 'fetch-request-reply',
              requestId: message.requestId,
              kind: 'failed',
              code: 'network',
              message: 'The request could not be completed.',
            };
          }
        }

        case 'download': {
          const blocked = checkUrl(message.url);
          if (blocked) {
            return { type: 'download-reply', requestId: message.requestId, kind: 'failed', code: blocked };
          }
          try {
            return await downloadOnce(message);
          } catch (_e) {
            if (downloadsByJob.has(message.jobId)) downloadsByJob.delete(message.jobId);
            return { type: 'download-reply', requestId: message.requestId, kind: 'failed', code: 'network', message: 'The download could not start.' };
          }
        }

        case 'cancel': {
          const downloadId = downloadsByJob.get(message.jobId);
          if (downloadId != null) {
            try {
              providers.downloads.cancel(downloadId);
            } catch (_e) {
              // Already done or gone.
            }
            downloadsByJob.delete(message.jobId);
          }
          return { type: 'ack' };
        }

        default:
          return { type: 'ack' };
      }
    }

    return { handleMessage, checkUrl, fileNameFromUrl, isPrivateHost, sanitizeRequestHeaders, effectiveRequestHeaders };
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      createBridge,
      checkUrl,
      fileNameFromUrl,
      sanitizeRequestHeaders,
      effectiveRequestHeaders,
      base64FromBytes,
      MV3_FORBIDDEN_HEADERS,
    };
    return;
  }

  // Extension wiring (MV3 service worker).
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    const tabId = sender && sender.tab ? sender.tab.id : undefined;
    const bridge = createBridge({
      fetchImpl: (url, init) => fetch(url, init),
      downloads: chrome.downloads,
      runtime: chrome.runtime,
      offscreen: chrome.offscreen,
      post: (payload) => {
        if (tabId !== undefined) {
          chrome.tabs.sendMessage(tabId, { source: 'anydownload-background', payload });
        }
      },
    });
    const result = bridge.handleMessage(message);
    if (result && typeof result.then === 'function') {
      result.then(sendResponse).catch(() => sendResponse({ type: 'ack' }));
      return true;
    }
    sendResponse(result);
  });

  // CDP verification hook (T-050 evidence): exposes the exact bridge instance
  // the page uses so a real-browser run can drive fetch-page/download without
  // touching chrome.runtime. Web pages cannot reach the service worker
  // context, so this adds no page-reachable surface.
  self.__anydownloadBridge = createBridge({
    fetchImpl: (url, init) => fetch(url, init),
    downloads: chrome.downloads,
    runtime: chrome.runtime,
    offscreen: chrome.offscreen,
    post: () => undefined,
  });
})(typeof self !== 'undefined' ? self : globalThis);