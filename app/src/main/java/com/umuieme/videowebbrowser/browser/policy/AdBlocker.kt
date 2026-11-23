package com.umuieme.videowebbrowser.browser.policy

import java.io.BufferedReader

/**
 * Blocks requests to known ad and tracking domains, so the page never downloads them.
 *
 * The list comes from assets/blocklist/ad-domains.txt (see scripts/update-blocklist.sh). It is
 * parsed once, off the main thread, by [AdBlockerHolder]; until then nothing is blocked.
 *
 * Thread safety: WebView calls shouldInterceptRequest on several background threads at once.
 * [domains] is built before the AdBlocker is published and never modified afterwards, and
 * concurrent *reads* of an unmodified HashSet are safe, so no locking is needed.
 */
class AdBlocker(private val domains: Set<String>) {

    val size: Int get() = domains.size

    /**
     * True when [host] or one of its parent domains is listed, so `ads.example.com` also
     * blocks `a.ads.example.com`. Never true when the host's registrable domain is in
     * [exemptDomains] (home site and allowlist): a list entry such as `cdn.videosite.com` must
     * not break the very site the user chose.
     */
    fun isBlocked(host: String?, exemptDomains: Set<String>): Boolean {
        val normalized = DomainUtils.normalizeHost(host) ?: return false
        if (DomainUtils.registrableDomain(normalized) in exemptDomains) return false
        return DomainUtils.hostAndParents(normalized).any { it in domains }
    }

    companion object {
        /**
         * Parses the list format: one domain per line; blank lines and `#` comments ignored.
         * The HashSet is pre-sized from a rough line estimate to avoid rehashing ~100k entries.
         */
        fun parse(reader: BufferedReader, expectedSize: Int = 16): AdBlocker {
            val set = HashSet<String>(expectedSize * 4 / 3 + 1)
            reader.useLines { lines ->
                for (line in lines) {
                    val domain = line.substringBefore('#').trim().lowercase()
                    if (domain.isNotEmpty()) set += domain
                }
            }
            return AdBlocker(set)
        }
    }
}

/**
 * Process-wide slot for the loaded [AdBlocker]. Written once from a background thread at app
 * start, read from WebView's request threads. `@Volatile` guarantees that a reader sees
 * either null or a fully constructed AdBlocker, never a half-built one.
 */
class AdBlockerHolder {
    @Volatile
    var adBlocker: AdBlocker? = null
}
