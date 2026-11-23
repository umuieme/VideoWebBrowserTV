package com.umuieme.videowebbrowser.browser.media

/**
 * The latest known state of one frame's video, as kept by [MediaController].
 *
 * @param lastInteractionMs when this video was last played, paused, seeked or touched, stamped
 *   with *our* clock on receipt (frames' own clocks aren't comparable), 0 if never.
 */
data class VideoState(
    val playing: Boolean,
    val currentTimeSec: Double,
    val durationSec: Double?,
    val inFullscreen: Boolean,
    val visibleArea: Double,
    val lastInteractionMs: Long,
)

/**
 * Picks the video the remote controls. Pure, so the rules are unit-tested.
 *
 * Order: the video in full screen, then a playing one, then the largest visible one, then the
 * one most recently interacted with. A video that is invisible, paused and not in full screen
 * is never picked: that is typically a preloaded ad or a hidden player, and Play would start it
 * where nobody can see it.
 */
object ActiveVideoSelector {

    private val priority: Comparator<VideoState> = compareBy(
        { it.inFullscreen },
        { it.playing },
        { it.visibleArea },
        { it.lastInteractionMs },
    )

    fun isCandidate(state: VideoState): Boolean =
        state.inFullscreen || state.playing || state.visibleArea > 0.0

    /** Returns the key (frame) of the best video, or null when no frame has a usable one. */
    fun <K> select(frames: Map<K, VideoState>): K? =
        frames.entries
            .filter { isCandidate(it.value) }
            .maxWithOrNull { a, b -> priority.compare(a.value, b.value) }
            ?.key
}
