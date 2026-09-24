package com.anydownlod.core.engine

/**
 * Engine-side destination rules for one direct-file download.
 *
 * [com.anydownlod.core.validation.SourceUrlValidator] already rejects blanks,
 * whitespace, non-http(s) schemes and empty hosts at the UI. This policy
 * repeats those checks and adds the T-006 threat controls: no userinfo, no
 * malformed authority, and no address that is local or reserved on this
 * device. The policy is evaluated on the submitted URL and again on every
 * redirect hop.
 *
 * Hosts that resolve to a private address are refused a second time by the
 * platform adapter after DNS (T-039 actuals). This common check blocks the
 * obvious IP literals and well-known loopback/link-local names without a
 * resolver; it is a string-level pre-filter, not a substitute for the
 * platform's dial-time check.
 */
enum class UrlRejectReason {
    UnsupportedScheme,
    MissingHost,
    UserInfo,
    BlockedDestination,
    Malformed,
}

sealed interface UrlCheck {
    data class Allowed(val url: String) : UrlCheck
    data class Rejected(val reason: UrlRejectReason) : UrlCheck
}

object UrlPolicy {
    /** Redirect budget the engine allows before calling it a network failure. */
    const val MAX_REDIRECTS = 10

    fun check(requestUrl: String): UrlCheck {
        val candidate = requestUrl.trim()
        val lower = candidate.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return UrlCheck.Rejected(UrlRejectReason.UnsupportedScheme)
        }
        val afterScheme = candidate.substringAfter("://")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return UrlCheck.Rejected(UrlRejectReason.MissingHost)
        // userinfo would carry credentials in a URL the app renders; reject it.
        if (authority.indexOf('@') >= 0) return UrlCheck.Rejected(UrlRejectReason.UserInfo)

        val hostPart = when {
            authority.startsWith("[") && authority.contains("]") ->
                authority.substringAfter('[').substringBefore(']')
            else -> authority
        }
        if (hostPart.isEmpty()) return UrlCheck.Rejected(UrlRejectReason.MissingHost)

        val (host, port) = splitHostPort(hostPart)
        if (host.isEmpty()) return UrlCheck.Rejected(UrlRejectReason.MissingHost)
        val portOk = port == null ||
            (port.isNotEmpty() && port.all { it.isDigit() } && (port.toLongOrNull() ?: 0L) <= 65_535L)
        if (!portOk) return UrlCheck.Rejected(UrlRejectReason.Malformed)
        if (blockedHost(host)) return UrlCheck.Rejected(UrlRejectReason.BlockedDestination)
        return UrlCheck.Allowed(candidate)
    }

    /** The host part of a URL for display and sourceHost columns. */
    fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
    }

    private fun splitHostPort(hostPart: String): Pair<String, String?> {
        // More than one colon means an exposed IPv6 literal; do not read its
        // last hextet as a port. (Bracketed forms already carry `]:port` off
        // during unwrapping.)
        if (hostPart.count { it == ':' } > 1) return hostPart to null
        val colon = hostPart.indexOf(':')
        if (colon >= 0) {
            // Zero or one colon: try `host:port`. A non-numeric port is kept
            // so the caller can reject it as malformed.
            return hostPart.substring(0, colon) to hostPart.substring(colon + 1)
        }
        return hostPart to null
    }

    private fun blockedHost(host: String): Boolean {
        val lower = host.trim().removeSuffix(".").lowercase()
        if (lower.isEmpty()) return true

        val octets = lower.split('.')
        if (octets.size == 4 && octets.all { it.isNotEmpty() && it.all { c -> c.isDigit() } }) {
            val parts = octets.mapNotNull { it.toIntOrNull() }
            if (parts.size == 4 && parts.all { it in 0..255 }) {
                return isBlockedIpv4(parts)
            }
        }

        if (lower.contains(':')) return isBlockedIpv6(lower)

        return lower == "localhost" ||
            lower.endsWith(".localhost") ||
            lower.endsWith(".local") ||
            lower.endsWith(".localdomain") ||
            lower.endsWith(".internal")
    }

    private fun isBlockedIpv4(p: List<Int>): Boolean = when {
        p[0] == 0 -> true // "this network"
        p[0] == 10 -> true // 10.0.0.0/8 private
        p[0] == 127 -> true // loopback
        p[0] == 169 && p[1] == 254 -> true // 169.254.0.0/16 link-local
        p[0] == 172 && p[1] in 16..31 -> true // 172.16.0.0/12 private
        p[0] == 192 && p[1] == 168 -> true // 192.168.0.0/16 private
        p[0] == 100 && p[1] in 64..127 -> true // 100.64.0.0/10 CGNAT
        p[0] == 192 && p[1] == 0 && p[2] == 0 -> true // 192.0.0.0/24 IETF
        p[0] == 192 && p[1] == 0 && p[2] == 2 -> true // 192.0.2.0/24 documentation
        p[0] == 198 && p[1] in 18..19 -> true // 198.18.0.0/15 benchmarking
        p[0] == 198 && p[1] == 51 && p[2] == 100 -> true // 198.51.100.0/24 documentation
        p[0] == 203 && p[1] == 0 && p[2] == 113 -> true // 203.0.113.0/24 documentation
        p[0] in 224..239 -> true // multicast
        p[0] in 240..255 -> true // reserved
        else -> false
    }

    private fun isBlockedIpv6(host: String): Boolean {
        val h = host.removePrefix("[").removeSuffix("]").lowercase()
        return when {
            h == "::" || h == "::1" -> true // unspecified / loopback
            h == "0:0:0:0:0:0:0:0" || h == "0:0:0:0:0:0:0:1" -> true // full loopback forms
            h.startsWith("2001:db8:") -> true // documentation range
            h.startsWith("::ffff:") -> {
                // IPv4-mapped address: refuse when the embedded address is local.
                val mapped = h.removePrefix("::ffff:")
                splitIpv4InPrefix(mapped)?.let { isBlockedIpv4(it) } ?: true
            }
            // fc00::/7 unique-local, in either canonical first-hextet form.
            h.startsWith("fc") || h.startsWith("fd") -> true
            // fe80::/10 link-local (first hextet fe80..febf).
            h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") -> true
            // ff00::/8 multicast (first hextet ff00..ffff).
            h.startsWith("ff") -> true
            else -> false
        }
    }

    private fun splitIpv4InPrefix(prefix: String): List<Int>? {
        val octets = prefix.split('.')
        if (octets.size != 4) return null
        val parts = octets.mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return null
        return parts
    }
}