package com.umuieme.videowebbrowser.browser.scroll

import org.json.JSONException
import org.json.JSONObject

/**
 * Where scroll-locator.js says one scroll gesture's wheel events should go.
 *
 * @param fx horizontal position as a fraction of the viewport width (0 = left edge, 1 = right).
 * @param fy vertical position as a fraction of the viewport height.
 */
data class ScrollTarget(val kind: Kind, val fx: Float, val fy: Float) {

    enum class Kind {
        /** Something scrollable (or an iframe) is under the pointer: scroll right there. */
        Point,

        /** An open site popup's scrollable part, wherever the pointer is. */
        Modal,

        /** Nothing more specific: the page, at the pointer. */
        Page,
    }

    companion object {
        /**
         * Parses the JSON that evaluateJavascript returns, e.g. `{"kind":"modal","fx":0.5,"fy":0.42}`.
         * Returns null for anything unexpected (script error → `null`, page without a document,
         * NaN coordinates); the caller then falls back to the pointer position.
         */
        fun parse(json: String?): ScrollTarget? {
            if (json.isNullOrBlank() || json == "null") return null
            return try {
                val obj = JSONObject(json)
                val kind = when (obj.optString("kind")) {
                    "point" -> Kind.Point
                    "modal" -> Kind.Modal
                    "page" -> Kind.Page
                    else -> return null
                }
                val fx = obj.optDouble("fx").toFloat()
                val fy = obj.optDouble("fy").toFloat()
                if (!fx.isFinite() || !fy.isFinite()) return null
                // A point a hair outside the viewport (rounding) still means its edge.
                ScrollTarget(kind, fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
            } catch (_: JSONException) {
                null
            }
        }
    }
}
