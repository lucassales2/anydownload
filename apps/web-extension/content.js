'use strict';

// T-043: the page bridge. This content script marks the page for the Wasm
// bridge (window.__anydownloadExtension), relays page -> background and
// background -> page. It never performs network fetches itself.

// Mark the page so WindowExtensionBridge.available() turns true.
window.__anydownloadExtension = true;

function postToPage(payload) {
  try {
    window.postMessage({ source: 'anydownload-extension', ...payload }, '*');
  } catch (_e) {
    // Never fatal; the page just goes back to "extension required".
  }
}

window.addEventListener('message', (event) => {
  if (event.source !== window) return;
  const data = event.data;
  if (!data || data.source !== 'anydownload-page') return;
  try {
    chrome.runtime.sendMessage(data, (response) => {
      if (chrome.runtime.lastError) {
        postToPage({
          type: `${data.type || 'probe'}-reply`,
          requestId: data.requestId,
          kind: 'failed',
          code: 'network',
          message: 'Extension context is not ready.',
          jobId: data.jobId,
        });
        return;
      }
      if (response && response.requestId) {
        postToPage(response);
      }
    });
  } catch (_e) {
    postToPage({
      type: `${data.type || 'probe'}-reply`,
      requestId: data.requestId,
      kind: 'failed',
      code: 'network',
      message: 'Extension context is not ready.',
      jobId: data.jobId,
    });
  }
});

// Background -> page relay (progress and download outcomes).
chrome.runtime.onMessage.addListener((message) => {
  if (!message || message.source !== 'anydownload-background') return;
  postToPage(message.payload);
});