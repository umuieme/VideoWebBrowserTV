package com.umuieme.videowebbrowser.browser

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * Builds the on-demand scripts we run with `WebView.evaluateJavascript` (as opposed to the media
 * agent, which is installed once at document start). Each call is assembled as
 *
 * ```
 * (function () { <modals.js> <entry script> return entry(<args as JSON>); })();
 * ```
 *
 * - The wrapper function keeps every helper private to this one call: nothing is added to the
 *   page's globals, so a site can't see or break our helpers between calls.
 * - The arguments are a JSON object literal (JSON is valid JavaScript), so values are always
 *   properly quoted; nothing user- or page-controlled is spliced into the code as text.
 * - evaluateJavascript hands back the wrapper's return value JSON-encoded, which the parsers
 *   ([com.umuieme.videowebbrowser.browser.scroll.ScrollTarget],
 *   [com.umuieme.videowebbrowser.browser.back.CloseModalResult]) read.
 *
 * Assets are read on first use and cached (a few KB). App-wide; lives in AppContainer.
 */
class PageScripts(private val assets: AssetManager) {

    private val modals by lazy { read("js/modals.js") }
    private val scrollLocator by lazy { read("js/scroll-locator.js") }
    private val closeModal by lazy { read("js/close-modal.js") }

    /**
     * scroll-locator.js for a pointer at ([fx], [fy]) (fractions of the viewport) and a gesture
     * direction per axis ([dx], [dy] in -1..1, positive = right/down).
     */
    fun scrollLocator(fx: Float, fy: Float, dx: Int, dy: Int): String = wrap(
        modals,
        scrollLocator,
        entry = "vwbLocateScrollTarget",
        args = JSONObject().put("fx", fx.toDouble()).put("fy", fy.toDouble()).put("dx", dx).put("dy", dy),
    )

    /** close-modal.js; [stuckKey] is the popup tried less than the stuck window ago, if any. */
    fun closeModal(stuckKey: String?): String = wrap(
        modals,
        closeModal,
        entry = "vwbCloseTopModal",
        args = JSONObject().put("stuckKey", stuckKey ?: JSONObject.NULL),
    )

    private fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        /** Pure string assembly, separate so it can be unit-tested without assets. */
        fun wrap(helpers: String, script: String, entry: String, args: JSONObject): String =
            "(function () {\n$helpers\n$script\nreturn $entry($args);\n})();"
    }
}
