package com.umuieme.videowebbrowser.ui.video

/** Formats media times for the OSD. Pure; unit-tested. */
object MediaTimeFormat {

    /** 0:05, 12:34, 1:02:03. Negative or non-finite input shows as 0:00. */
    fun format(seconds: Double): String {
        val total = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
