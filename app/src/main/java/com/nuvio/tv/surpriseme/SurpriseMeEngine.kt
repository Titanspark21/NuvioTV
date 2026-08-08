package com.nuvio.tv.surpriseme

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Which pool the owner is asking to be surprised from. */
enum class SurpriseKind { SHOW, MOVIE }

sealed interface RollResult {
    data class Picked(val item: MetaPreview, val sourceName: String) : RollResult
    data object NoSources : RollResult
    data object NothingLeft : RollResult
    data class Failed(val message: String) : RollResult
}

/**
 * Fork addition. Holds one "surprise me" sitting.
 *
 * A singleton rather than a view model because the sitting outlives the picker screen: the
 * owner is sent to the chosen title's own detail page, and the "Roll again" button there
 * has to continue the same rotation and keep the same already-offered list. Passing that
 * through navigation arguments would mean threading state through upstream's detail route
 * for no benefit.
 */
@Singleton
class SurpriseMeEngine @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository
) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var pressIndex = 0
    private var kind: SurpriseKind = SurpriseKind.SHOW
    private var includeWatched: Boolean = false

    /**
     * Ids already offered this sitting. A small recommendations catalogue otherwise hands
     * back the same title several presses running, which reads as a broken button.
     */
    private val alreadyOffered = mutableSetOf<String>()

    private val _lastPickId = MutableStateFlow<String?>(null)

    /**
     * The title the last roll landed on. The detail screen compares this against what it
     * is showing to decide whether to offer "Roll again" - which is what confines the
     * button to titles the owner actually arrived at by rolling.
     */
    val lastPickId: StateFlow<String?> = _lastPickId.asStateFlow()

    /**
     * The pick for the NEXT press, worked out in the background while the owner is
     * looking at the current one.
     *
     * A roll costs several catalogue round trips, which is where the wait comes from.
     * Doing that work during the seconds someone spends reading a synopsis turns the
     * following press into an instant one. It is only ever a saved result, so a stale
     * preload can do nothing worse than be discarded.
     */
    private var preloaded: RollResult.Picked? = null
    private var preloadJob: Job? = null

    fun beginSession(kind: SurpriseKind, includeWatched: Boolean) {
        this.kind = kind
        this.includeWatched = includeWatched
        alreadyOffered.clear()
        discardPreload()
        _lastPickId.value = null
    }

    fun endSession() {
        discardPreload()
        _lastPickId.value = null
    }

    private fun discardPreload() {
        preloadJob?.cancel()
        preloadJob = null
        // A preloaded pick was already counted as offered; putting it back keeps it
        // eligible rather than silently burning a title the owner never saw.
        preloaded?.let { alreadyOffered -= it.item.id }
        preloaded = null
    }

    suspend fun roll(): RollResult {
        preloaded?.let { ready ->
            preloaded = null
            _lastPickId.value = ready.item.id
            schedulePreload()
            return ready
        }

        val outcome = lock.withLock { rollLocked() }
        if (outcome is RollResult.Picked) {
            _lastPickId.value = outcome.item.id
            schedulePreload()
        }
        return outcome
    }

    private suspend fun rollLocked(): RollResult {
        val outcome = runCatching { rollOnce() }
            .getOrElse { RollResult.Failed(it.message ?: "Could not reach your recommendation addons.") }

        if (outcome is RollResult.Picked) {
            alreadyOffered += outcome.item.id
            pressIndex++
        }
        return outcome
    }

    private fun schedulePreload() {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val next = lock.withLock { rollLocked() }
            // Only a successful pick is worth keeping; a failure should be retried live
            // so the owner sees a current error rather than an old one.
            preloaded = next as? RollResult.Picked
        }
    }

    private suspend fun rollOnce(): RollResult {
        val contentType = when (kind) {
            SurpriseKind.SHOW -> ContentType.SERIES
            SurpriseKind.MOVIE -> ContentType.MOVIE
        }

        // Installed, not necessarily enabled: a recommendation addon kept off the home
        // screen should still feed this.
        val installed = addonRepository.getInstalledAddons().first()
        val bySource: Map<SurpriseSource, List<Addon>> = installed
            .mapNotNull { addon ->
                val source = sourceOf(addon) ?: return@mapNotNull null
                if (usableCatalogs(addon, contentType).isEmpty()) return@mapNotNull null
                source to addon
            }
            .groupBy({ it.first }, { it.second })

        if (bySource.isEmpty()) return RollResult.NoSources

        // Walk the rotation from where the sitting left off. If whoever's turn it is has
        // nothing usable left, fall through rather than wasting the press.
        val order = bySource.keys
        repeat(order.size) { attempt ->
            val source = sourceForPress(pressIndex + attempt, order) ?: return RollResult.NoSources
            val candidates = collectCandidates(bySource[source].orEmpty(), contentType)
            val filtered = filterCandidates(candidates)
            if (filtered.isNotEmpty()) {
                return RollResult.Picked(filtered.random(), source.label)
            }
        }
        return RollResult.NothingLeft
    }

    /** A handful of posters for the picker's cover art, drawn from the owner's own catalogues. */
    suspend fun samplePosters(kind: SurpriseKind, limit: Int = 12): List<String> {
        val contentType = when (kind) {
            SurpriseKind.SHOW -> ContentType.SERIES
            SurpriseKind.MOVIE -> ContentType.MOVIE
        }
        val installed = runCatching { addonRepository.getInstalledAddons().first() }.getOrNull().orEmpty()

        // Any addon will do here - this is decoration, so the first catalogue that answers
        // is enough and there is no reason to pay for more round trips.
        for (addon in installed) {
            val catalog = usableCatalogs(addon, contentType).firstOrNull() ?: continue
            val result = runCatching {
                catalogRepository.getCatalog(
                    addonBaseUrl = addon.baseUrl,
                    addonId = addon.id,
                    addonName = addon.name,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    type = catalog.apiType
                ).first { it !is NetworkResult.Loading }
            }.getOrNull()

            if (result is NetworkResult.Success) {
                val posters = result.data.items.mapNotNull { it.poster }.filter { it.isNotBlank() }
                if (posters.size >= 4) return posters.take(limit)
            }
        }
        return emptyList()
    }

    private suspend fun collectCandidates(
        addons: List<Addon>,
        contentType: ContentType
    ): List<MetaPreview> {
        val out = mutableListOf<MetaPreview>()
        for (addon in addons) {
            for (catalog in usableCatalogs(addon, contentType)) {
                val result = catalogRepository.getCatalog(
                    addonBaseUrl = addon.baseUrl,
                    addonId = addon.id,
                    addonName = addon.name,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    type = catalog.apiType
                ).first { it !is NetworkResult.Loading }

                if (result is NetworkResult.Success) {
                    out += result.data.items
                }
            }
        }
        return out.distinctBy { it.id }
    }

    private suspend fun filterCandidates(candidates: List<MetaPreview>): List<MetaPreview> {
        val notRepeated = candidates.filterNot { it.id in alreadyOffered }
        if (includeWatched) return notRepeated

        // "Only new" means not already in the library - the signal this app actually holds
        // for a title as a whole.
        return notRepeated.filterNot { item ->
            libraryRepository.isInLibrary(item.id, item.rawType).first()
        }
    }
}
