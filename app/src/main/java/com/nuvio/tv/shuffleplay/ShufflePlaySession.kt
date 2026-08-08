package com.nuvio.tv.shuffleplay

import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.screens.player.PlayerNextEpisodeRules

/**
 * Fork addition. Remembers that the owner started watching a show by pressing shuffle, so
 * that when an episode ends the player advances to another RANDOM episode instead of the
 * next one in order.
 *
 * A plain object rather than an injected singleton because the one place that has to read
 * it - the player's next-episode resolution - is an extension function on the runtime
 * controller with no dependency graph of its own. There is exactly one player at a time,
 * so there is no state to keep separate.
 *
 * Keyed by content id: pressing play normally on anything, or opening another show, ends
 * the session. Otherwise a shuffle started weeks ago would quietly scramble the next
 * series the owner sat down to watch in order.
 */
object ShufflePlaySession {

    private var activeContentId: String? = null

    /** Recently played episode ids, newest first, so a short run does not repeat itself. */
    private val recent = ArrayDeque<String>()

    fun start(contentId: String) {
        if (activeContentId != contentId) {
            recent.clear()
        }
        activeContentId = contentId
    }

    fun stop() {
        activeContentId = null
        recent.clear()
    }

    fun isActiveFor(contentId: String?): Boolean =
        contentId != null && contentId == activeContentId

    fun remember(videoId: String) {
        recent.remove(videoId)
        recent.addFirst(videoId)
    }

    /**
     * A random episode to follow the current one.
     *
     * Unaired episodes are excluded because they have no streams: the addons would search,
     * find nothing, and the owner would sit through the wait for a failure. Specials are
     * excluded for the same reason the shuffle button excludes them - they are rarely what
     * someone means by "another episode".
     */
    fun pickNext(
        videos: List<Video>,
        currentSeason: Int?,
        currentEpisode: Int?
    ): Video? {
        val pool = videos.filter { video ->
            video.season != null && video.season != 0 &&
                video.episode != null &&
                video.available != false &&
                PlayerNextEpisodeRules.hasEpisodeAired(video.released) &&
                !(video.season == currentSeason && video.episode == currentEpisode)
        }
        if (pool.isEmpty()) return null

        // Avoid the recent run, but never at the cost of returning nothing: on a short
        // series the history is simply ignored once it covers most of the episodes.
        val avoid = recent.take((pool.size - 1).coerceAtMost(RECENT_MEMORY)).toSet()
        val fresh = pool.filterNot { it.id in avoid }
        return (fresh.ifEmpty { pool }).random()
    }

    private const val RECENT_MEMORY = 12
}
