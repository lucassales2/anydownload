'use strict';

/**
 * T-067 offscreen saver: MV3 service workers cannot create Blob URLs, and in
 * some environments `chrome.downloads` cannot fetch a signed media URL
 * directly. This document fetches the already-validated media URL with the
 * extension's host permissions, builds a Blob, and hands the Blob URL to
 * `chrome.downloads`. The page never fetches an origin; only the extension
 * does, exactly as with `fetch-request`.
 */
(function (root) {
  'use strict';

  const MAX_BYTES = 512 * 1024 * 1024;

  async function save(message) {
    let response;
    try {
      response = await fetch(message.url, {
        method: 'GET',
        credentials: 'omit',
        redirect: 'follow',
        headers: message.headers || {},
      });
    } catch (_e) {
      return { ok: false, code: 'network', message: 'The media could not be reached.' };
    }
    if (!response.ok) {
      return { ok: false, code: 'network', status: response.status, message: 'The media request failed.' };
    }
    const chunks = [];
    let total = 0;
    const reader = response.body && response.body.getReader ? response.body.getReader() : null;
    if (reader) {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!value) continue;
        total += value.length;
        if (total > MAX_BYTES) {
          await reader.cancel();
          return { ok: false, code: 'other', message: 'The media is too large for the extension port.' };
        }
        chunks.push(value);
      }
    }
    // A File (not a plain Blob) keeps the requested name for blob downloads.
    const file = new File(
      chunks,
      message.fileName || 'download',
      { type: response.headers.get('content-type') || 'application/octet-stream' },
    );
    // The downloads API is not exposed in offscreen documents, so the service
    // worker downloads this extension-origin Blob URL instead. The URL stays
    // alive while this document does.
    const blobUrl = URL.createObjectURL(file);
    return { ok: true, blobUrl, sizeBytes: file.size };
  }

  if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.onMessage) {
    chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
      if (!message || message.target !== 'anydownload-offscreen' || message.type !== 'save') return false;
      save(message)
        .then(sendResponse)
        .catch(() => sendResponse({ ok: false, code: 'other', message: 'The save failed.' }));
      return true;
    });
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { save };
  }
})(typeof self !== 'undefined' ? self : globalThis);
