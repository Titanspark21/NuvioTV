package com.nuvio.tv.surpriseme

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType

/**
 * Fork addition. Pure logic behind the "Surprise me" buttons, kept free of Android and of
 * the repositories so it can be reasoned about (and tested) on its own.
 */

/** A recommendation provider, in the order the owner asked to see them. */
enum class SurpriseSource(val key: String, val label: String, val picksPerCycle: Int) {
    BINGE_CAT("bingecat", "BingeCat", 2),
    SIMKL("simkl", "Simkl", 2),
    TRAKT("trakt", "Trakt", 1)
}

/**
 * The rotation the owner asked for: two BingeCat, two Simkl, one Trakt, repeating.
 *
 * Built as an explicit repeating cycle rather than a weighted random draw, because "2 2 1"
 * is a promise about what the next five presses do. Random weighting would deliver those
 * proportions only on average, and the first five presses are the ones that get noticed.
 */
val SURPRISE_ROTATION: List<SurpriseSource> = SurpriseSource.entries
    .flatMap { source -> List(source.picksPerCycle) { source } }

/**
 * Which source a given press should draw from, given how many presses have already
 * happened. Sources with no usable catalogue installed are skipped rather than wasting a
 * turn, so with only BingeCat installed every press still returns something.
 */
fun sourceForPress(pressIndex: Int, available: Set<SurpriseSource>): SurpriseSource? {
    if (available.isEmpty()) return null
    val usable = SURPRISE_ROTATION.filter { it in available }
    if (usable.isEmpty()) return null
    return usable[((pressIndex % usable.size) + usable.size) % usable.size]
}

/**
 * Matches an installed addon to a source by looking at its id, name and base URL.
 *
 * Deliberately a substring match on all three: an addon's id is author-chosen and varies
 * between community builds and forks of the same addon, so keying on one exact id would
 * silently stop working the day the owner reinstalls it from a different mirror.
 */
fun sourceOf(addon: Addon): SurpriseSource? {
    val haystack = listOf(addon.id, addon.name, addon.displayName, addon.baseUrl)
        .joinToString(" ") { it.lowercase() }
    return SurpriseSource.entries.firstOrNull { source ->
        haystack.contains(source.key)
    }
}

/**
 * The catalogues on an addon worth drawing from for [type].
 *
 * Catalogues that demand a search term or another required argument are excluded: they
 * cannot be fetched blind, so asking for one would just return an error.
 */
fun usableCatalogs(addon: Addon, type: ContentType): List<CatalogDescriptor> =
    addon.catalogs.filter { catalog ->
        catalog.type == type && catalog.extraRequired.isEmpty()
    }
