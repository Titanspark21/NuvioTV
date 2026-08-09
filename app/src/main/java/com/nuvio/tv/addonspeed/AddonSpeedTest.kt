package com.nuvio.tv.addonspeed

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.StreamRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One title the test asks every addon about.
 *
 * [id] is a Stremio id: an IMDb id for a film, and `imdbId:season:episode` for an episode.
 */
data class SpeedTestItem(
    val label: String,
    val id: String,
    val type: String
) {
    companion object {
        /**
         * Defaults chosen to cover the three cases that behave differently: a long-running
         * series episode, a recent release, and an older catalogue title.
         *
         * These are editable because an id that does not exist tests nothing while looking
         * like it worked - the request still returns, just with an empty answer.
         */
        val DEFAULTS = listOf(
            SpeedTestItem("Brooklyn Nine-Nine S3E5", "tt2467372:3:5", "series"),
            SpeedTestItem("Lost S2E3", "tt0411008:2:3", "series"),
            SpeedTestItem("The Matrix", "tt0133093", "movie")
        )
    }
}

/** How one addon did on one title. */
data class SpeedTestRow(
    val addonName: String,
    val item: SpeedTestItem,
    val kind: AddonCallKind,
    val durationMs: Long,
    val outcome: Outcome
) {
    enum class Outcome { ANSWERED, EMPTY, FAILED, TIMED_OUT }
}

/**
 * Fork addition. Asks every installed addon the same questions and times the answers.
 *
 * Deliberately sequential per addon: running them all at once would have them competing
 * for the same connection pool and bandwidth, and the resulting numbers would measure the
 * contention rather than the addons.
 */
@Singleton
class AddonSpeedTest @Inject constructor(
    private val addonRepository: AddonRepository,
    private val metaRepository: MetaRepository,
    private val streamRepository: StreamRepository
) {

    suspend fun metaAddons(): List<Addon> = addonRepository.getInstalledAddons().first()
        .filter { addon -> addon.resources.any { it.name == "meta" } }

    suspend fun streamAddons(): List<Addon> = addonRepository.getInstalledAddons().first()
        .filter { addon -> addon.resources.any { it.name == "stream" } }

    /**
     * Times one metadata addon on one title.
     *
     * The addon is asked directly by its own base URL rather than through the normal
     * priority order, which is the only way to time an addon that would otherwise never
     * be reached because a higher-priority one always answers first.
     */
    suspend fun timeMeta(addon: Addon, item: SpeedTestItem): SpeedTestRow {
        val startedAt = System.currentTimeMillis()
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            metaRepository.getMeta(
                addonBaseUrl = addon.baseUrl,
                type = item.type,
                id = item.id
            ).first { it !is NetworkResult.Loading }
        }
        val elapsed = System.currentTimeMillis() - startedAt

        val outcome = when {
            result == null -> SpeedTestRow.Outcome.TIMED_OUT
            result is NetworkResult.Success -> SpeedTestRow.Outcome.ANSWERED
            else -> SpeedTestRow.Outcome.FAILED
        }
        return SpeedTestRow(addon.displayName, item, AddonCallKind.META, elapsed, outcome)
    }

    /**
     * Times the stream search for one title across all stream addons at once.
     *
     * Unlike metadata, this deliberately measures the real thing: the app always asks
     * every stream addon together, so timing them in isolation would report a number the
     * owner never actually experiences.
     */
    suspend fun timeStreams(item: SpeedTestItem): SpeedTestRow {
        val season = item.id.split(":").getOrNull(1)?.toIntOrNull()
        val episode = item.id.split(":").getOrNull(2)?.toIntOrNull()
        val baseId = item.id.substringBefore(":")

        val startedAt = System.currentTimeMillis()
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            streamRepository.getStreamsFromAllAddons(
                type = item.type,
                videoId = if (season != null && episode != null) item.id else baseId,
                season = season,
                episode = episode
            ).first { it !is NetworkResult.Loading }
        }
        val elapsed = System.currentTimeMillis() - startedAt

        val outcome = when {
            result == null -> SpeedTestRow.Outcome.TIMED_OUT
            result is NetworkResult.Success && result.data.any { it.streams.isNotEmpty() } ->
                SpeedTestRow.Outcome.ANSWERED
            result is NetworkResult.Success -> SpeedTestRow.Outcome.EMPTY
            else -> SpeedTestRow.Outcome.FAILED
        }
        return SpeedTestRow(ALL_STREAM_ADDONS, item, AddonCallKind.STREAM, elapsed, outcome)
    }

    companion object {
        const val ALL_STREAM_ADDONS = "All stream addons"
        private const val TIMEOUT_MS = 45_000L
    }
}
