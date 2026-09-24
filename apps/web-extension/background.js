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

  function isPrivateIpv4(host) {
    const parts = host.split('.');
    if (parts.length !== 4) return false;
    const p = parts.map((s) => (/^\d+$/.test(s) ? Number(s) : 255));
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
    };
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

    function downloadOnce(message) {
      // The browser's own downloader streams and saves; the SW never reads a
      // body byte. Progress is reported as unknown until the browser reports
      // the download complete.
      providers.post({
        source: 'anydownload-extension',
        type: 'progress',
        requestId: message.requestId,
        jobId: message.jobId,
        downloadedBytes: null,
        totalBytes: null,
      });
      const fileName = fileNameFromUrl(message.url);
      return providers.downloads.download({ url: message.url, filename: fileName, saveAs: false }).then((downloadId) => {
        downloadsByJob.set(message.jobId, downloadId);
        return { type: 'download-reply', requestId: message.requestId, kind: 'completed', fileName, sizeBytes: null };
      });
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

    return { handleMessage, checkUrl, fileNameFromUrl, isPrivateHost };
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { createBridge, checkUrl, fileNameFromUrl };
    return;
  }

  // Extension wiring (MV3 service worker).
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    const tabId = sender && sender.tab ? sender.tab.id : undefined;
    const bridge = createBridge({
      fetchImpl: (url, init) => fetch(url, init),
      downloads: chrome.downloads,
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
    post: () => undefined,
  });
})(typeof self !== 'undefined' ? self : globalThis);