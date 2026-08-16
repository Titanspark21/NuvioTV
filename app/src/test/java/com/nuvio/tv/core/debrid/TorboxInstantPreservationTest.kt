package com.nuvio.tv.core.debrid

import com.nuvio.tv.core.cloud.TorboxCloudLibraryProviderApi
import com.nuvio.tv.data.remote.api.TorboxApi
import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.model.StreamDebridCacheState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork-only guard for the built-in "Torbox Instant" feature.
 *
 * Upstream (NuvioMedia/NuvioTV) has publicly signalled it wants the in-app debrid
 * integration gone for app-store-policy reasons. This fork keeps it. If a future
 * upstream merge deletes or guts any part of it, this test stops compiling or fails,
 * instead of the feature quietly disappearing from a build.
 *
 * Do not "fix" this test by deleting it. Restore the feature instead - see
 * preservation/TORBOX-INSTANT.md.
 */
class TorboxInstantPreservationTest {

    @Test
    fun `torbox is a visible provider with the full instant capability set`() {
        val torbox = DebridProviders.byId(DebridProviders.TORBOX_ID)
        requireNotNull(torbox) { "Torbox provider was removed from DebridProviders" }

        assertTrue("Torbox must stay visible in Settings", torbox.visibleInUi)
        assertTrue(
            "Torbox lost ClientResolve - it can no longer turn a magnet into a playable link",
            torbox.supports(DebridProviderCapability.ClientResolve)
        )
        assertTrue(
            "Torbox lost LocalTorrentCacheCheck - cached/not-cached badges will stop working",
            torbox.supports(DebridProviderCapability.LocalTorrentCacheCheck)
        )
        assertTrue(
            "Torbox lost LocalTorrentResolve - in-app instant playback will stop working",
            torbox.supports(DebridProviderCapability.LocalTorrentResolve)
        )
        assertTrue(
            "Torbox lost CloudLibrary - the Cloud tab in Library will stop working",
            torbox.supports(DebridProviderCapability.CloudLibrary)
        )
    }

    @Test
    fun `configuring a torbox key produces a Torbox Instant source`() {
        val settings = DebridSettings(enabled = true, torboxApiKey = "tb")

        assertEquals("Torbox Instant", DebridProviders.instantName(DebridProviders.TORBOX_ID))
        assertTrue(
            "Torbox Instant is no longer offered as a stream source",
            DebridProviders.configuredSourceNames(settings).contains("Torbox Instant")
        )
    }

    @Test
    fun `the classes that implement Torbox Instant still exist`() {
        // Referencing these keeps the test module from compiling if any are deleted.
        val required = listOf(
            LocalDebridService::class.java,
            LocalDebridAvailabilityService::class.java,
            TorboxDirectDebridResolver::class.java,
            TorboxFileSelector::class.java,
            DirectDebridStreamPreparer::class.java,
            DirectDebridStreamFilter::class.java,
            TorboxCloudLibraryProviderApi::class.java,
            TorboxApi::class.java
        )
        required.forEach { type ->
            assertTrue("${type.simpleName} disappeared", type.name.isNotBlank())
        }
    }

    @Test
    fun `the torbox cached-availability endpoint is still wired up`() {
        val hasCheckCached = TorboxApi::class.java.methods.any { it.name == "checkCached" }
        assertTrue(
            "TorboxApi.checkCached is gone - the app can no longer ask Torbox what is cached",
            hasCheckCached
        )

        // The cache states the badges and auto-play selection depend on.
        assertTrue(
            StreamDebridCacheState.values().map { it.name }
                .containsAll(listOf("CACHED", "NOT_CACHED", "CHECKING", "UNKNOWN"))
        )
    }
}
