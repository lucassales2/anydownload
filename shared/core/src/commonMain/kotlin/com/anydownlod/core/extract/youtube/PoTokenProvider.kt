/*
 * PO token provider — AnyDownload (T-124, E-13)
 *
 * Translation of the request model in
 * `yt_dlp/extractor/youtube/pot/provider.py` and the `fetch_po_token` call
 * sites in `yt_dlp/extractor/youtube/_video.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30. Unlicense; see shared/core/NOTICE.md.
 *
 * Formats whose client policy requires a GVS token are dropped unless a
 * provider is configured and returns one. The default provider returns null,
 * so nothing is invented, no token is logged, and no token is persisted.
 */
package com.anydownlod.core.extract.youtube

/** What a PO token is minted for (upstream `_PoTokenContext`). */
enum class PoTokenContext {
    PLAYER,
    GVS,
    SUBS,
}

/**
 * One PO-token request. A provider sees the video id, the innertube client
 * name, the context, and the session values the port already holds; nothing
 * else is passed.
 */
data class PoTokenRequest(
    val videoId: String,
    val client: String,
    val context: PoTokenContext,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
)

/**
 * A configured PO-token provider. Returns one token for [request], or null
 * when it cannot mint one. A null result never fails extraction; it only
 * keeps token-requiring formats dropped. The token is never logged or stored.
 */
fun interface PoTokenProvider {
    suspend fun tokenFor(request: PoTokenRequest): String?
}

/** The default: no provider configured, so token-gated formats stay dropped. */
object NoPoTokenProvider : PoTokenProvider {
    override suspend fun tokenFor(request: PoTokenRequest): String? = null
}
