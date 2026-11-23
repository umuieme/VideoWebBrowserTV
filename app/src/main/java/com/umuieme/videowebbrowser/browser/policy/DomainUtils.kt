package com.umuieme.videowebbrowser.browser.policy

/**
 * Host-name helpers for the navigation policy and the ad blocker. Pure Kotlin, so it is
 * unit-tested on the JVM.
 *
 * "Site" here means *registrable domain*: the part of a host name someone can register, e.g.
 * `example.com` for `www.example.com` and `video.example.com`, or `bbc.co.uk` for
 * `www.bbc.co.uk`. Browsers use the same idea ("same-site") for cookies.
 *
 * Limitation: the real list of public suffixes (publicsuffix.org) has ~10 000 entries, including
 * private ones such as `github.io` or `blogspot.com`. We only know the common multi-label
 * country suffixes in [MULTI_LABEL_SUFFIXES]. Consequences:
 * - `alice.github.io` and `bob.github.io` count as the same site (too permissive).
 * - A rarer two-label suffix such as `gov.pl` makes every `*.gov.pl` host one site.
 * Both are acceptable for keeping a TV on one chosen video site; neither affects security
 * boundaries, which WebView enforces itself.
 */
object DomainUtils {

    /**
     * Common public suffixes made of two labels. The last two labels of a host match one of
     * these, so the registrable domain needs three.
     */
    private val MULTI_LABEL_SUFFIXES = setOf(
        // United Kingdom
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk",
        // Australia, New Zealand
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.nz", "org.nz", "net.nz",
        // Asia
        "co.in", "net.in", "org.in", "co.jp", "ne.jp", "or.jp", "ac.jp", "co.kr", "or.kr",
        "com.cn", "net.cn", "org.cn", "com.hk", "com.tw", "com.sg", "com.my", "com.ph",
        "co.id", "co.th", "com.vn", "com.np", "com.pk", "com.bd",
        // Americas
        "com.br", "net.br", "org.br", "com.mx", "com.ar", "com.co", "com.pe", "com.ve",
        // Europe, Middle East, Africa
        "com.tr", "co.il", "com.ua", "com.pl", "co.za", "com.eg", "com.ng", "co.ke", "com.sa",
    )

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * Registrable domain of [host], lowercased, or null if [host] is null or blank.
     *
     * - IP addresses (v4 or v6, with or without brackets) and `localhost` are returned as they
     *   are: they have no parent domain, so they only ever match themselves.
     * - Single-label hosts (e.g. an intranet name) are returned as they are.
     */
    fun registrableDomain(host: String?): String? {
        val normalized = normalizeHost(host) ?: return null
        if (isIpAddress(normalized) || normalized == "localhost") return normalized

        val labels = normalized.split('.')
        if (labels.size <= 2) return normalized
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in MULTI_LABEL_SUFFIXES) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    /** True when both hosts have the same registrable domain. Null/blank never matches. */
    fun isSameSite(a: String?, b: String?): Boolean {
        val domainA = registrableDomain(a) ?: return false
        return domainA == registrableDomain(b)
    }

    /**
     * Lowercase, without brackets (IPv6 literals in URLs are written `[::1]`) or a trailing
     * dot (`example.com.` is a valid, fully-qualified spelling of `example.com`).
     */
    fun normalizeHost(host: String?): String? {
        val trimmed = host?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.')
        return trimmed?.takeIf { it.isNotEmpty() }
    }

    /**
     * [host] followed by each parent domain, e.g. `a.b.example.com`, `b.example.com`,
     * `example.com`, `com`. Used by the ad blocker to match a listed parent domain. Hosts are
     * expected already normalized; IP addresses yield only themselves.
     */
    fun hostAndParents(host: String): Sequence<String> {
        if (isIpAddress(host)) return sequenceOf(host)
        return generateSequence(host) { current ->
            val dot = current.indexOf('.')
            if (dot < 0) null else current.substring(dot + 1)
        }
    }

    // IPv6 literals always contain ':'; host names never do.
    private fun isIpAddress(host: String): Boolean = ':' in host || IPV4.matches(host)
}
