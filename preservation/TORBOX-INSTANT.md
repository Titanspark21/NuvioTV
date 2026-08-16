# Torbox Instant — fork-preserved feature

This fork deliberately keeps the **built-in debrid integration** that upstream
(`NuvioMedia/NuvioTV`) has said it wants to drop. Do not delete it. Do not accept an
upstream merge that removes it without restoring it afterwards.

## What "Torbox Instant" actually is

It is the app talking to Torbox **directly**, instead of going through a Stremio-style
addon. When a Torbox API key is set in Settings → Debrid, the app:

1. Collects the torrent hashes from every stream the addons returned.
2. Asks Torbox in one batch which of those are already sitting in its cache
   (`POST v1/api/torrents/checkcached`).
3. Marks each stream **Cached / Not cached / Checking / Unknown** in the stream list.
4. For a cached one, resolves it to a direct playable link itself and presents it as a
   source literally named **"Torbox Instant"**.
5. Can pre-resolve a link before you press play, and powers the **Cloud** tab in Library.

Without it you fall back to a debrid-enabled addon (AIOStreams, Torrentio with a key,
etc.), which means an extra server hop, slower first play, and no in-app cached badges.

## Why upstream is dropping it

App-store policy risk — bundling debrid resolution inside the app itself. The upstream
position was that they "don't want to take the risk of bundling a DB into the app" and
were looking at moving it outside the app instead.

**Status as of 2026-08-16:** the removal was announced around 2026-05-20, but it never
landed. Every file listed below is still present and still being actively developed on
`upstream/dev` (cloud library, stream badges, precache and timing logs were all added
*after* the announcement). So nothing has been lost yet — this document and the guard
test exist so that nothing is lost *quietly* later.

## Snapshot

A known-good snapshot is tagged and pushed:

    git tag torbox-instant-v1

To see the whole feature as it stood at that tag:

    git show torbox-instant-v1 --stat

To restore just this feature after a bad merge:

    git checkout torbox-instant-v1 -- app/src/main/java/com/nuvio/tv/core/debrid \
      app/src/main/java/com/nuvio/tv/core/cloud \
      app/src/main/java/com/nuvio/tv/data/remote/api/TorboxApi.kt \
      app/src/main/java/com/nuvio/tv/data/remote/dto/TorboxDto.kt \
      app/src/main/java/com/nuvio/tv/domain/model/DebridSettings.kt \
      app/src/main/java/com/nuvio/tv/data/local/DebridSettingsDataStore.kt \
      app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsScreen.kt \
      app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsViewModel.kt

...then rebuild and fix any call sites the merge changed around them.

## The guard

`app/src/test/java/com/nuvio/tv/core/debrid/TorboxInstantPreservationTest.kt` is
fork-only. It fails — or stops compiling — if the provider loses a capability, if the
"Torbox Instant" source name changes, if the cached-check endpoint is removed, or if any
of the implementing classes are deleted. Run it after every upstream merge:

    ./gradlew testDebugUnitTest --tests "*TorboxInstantPreservationTest*"

## Files that make it work

Core:
- `core/debrid/DebridProvider.kt` — provider registry, capabilities, the `"… Instant"` name
- `core/debrid/LocalDebridService.kt` — the batched cached-hash check
- `core/debrid/LocalDebridAvailabilityService.kt` — turns that into per-stream cache badges
- `core/debrid/DirectDebridResolver.kt` — magnet → playable link
- `core/debrid/TorboxDirectDebridResolver.kt`, `TorboxFileSelector.kt` — Torbox specifics
- `core/debrid/DirectDebridStreamPreparer.kt` — pre-resolves before you press play
- `core/debrid/DirectDebridStreamFilter.kt` — builds the Instant entries in the stream list
- `core/debrid/DebridStreamFormatter*.kt`, `DebridStreamTemplateEngine.kt`, `DebridStreamPresentation.kt` — how the rows read
- `core/debrid/DebridMagnetBuilder.kt`, `DebridFileSelection.kt`, `DebridDeviceAuth.kt`, `StreamTextSizeParser.kt`

Cloud library:
- `core/cloud/TorboxCloudLibraryProviderApi.kt`, `CloudLibraryRepository.kt`, `CloudLibraryModels.kt`, `CloudLibraryProviderApi.kt`

Network / storage / settings:
- `data/remote/api/TorboxApi.kt`, `data/remote/dto/TorboxDto.kt`
- `core/di/NetworkModule.kt` (Torbox Retrofit client)
- `domain/model/DebridSettings.kt`, `data/local/DebridSettingsDataStore.kt`
- `core/sync/ProviderCredentialSyncService.kt`, `core/server/DebridFormatterConfigServer.kt`

UI and consumers:
- `ui/screens/settings/DebridSettingsScreen.kt`, `DebridSettingsViewModel.kt`, `SettingsScreen.kt`
- `ui/screens/stream/StreamScreenViewModel.kt`
- `ui/screens/library/LibraryScreen.kt`, `LibraryViewModel.kt`
- `ui/screens/player/PlayerViewModel.kt`, `PlayerRuntimeController*.kt`
- `data/repository/StreamRepositoryImpl.kt`, `core/player/StreamAutoPlaySelector.kt`
- `domain/model/Stream.kt` (`StreamDebridCacheState`, `StreamDebridCacheStatus`)
