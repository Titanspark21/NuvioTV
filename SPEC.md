# NuvioTV — Titanspark21 fork

## What it is

A personal fork of [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV), a TV-first
Android media player written in Kotlin that plays from the Stremio addon ecosystem. The fork
exists to add a few features the owner wants, to keep one feature upstream is removing, and to
build itself automatically.

## Who it is for

One person, one household. Primary device is a **Google TV Streamer** (arm64). No support is
offered and no general release is intended, but the repo is public and the release APKs are
installable by anyone.

## How it is built and run

- Kotlin, Jetpack Compose for TV, media3/ExoPlayer with a vendored engine, libmpv as the
  alternate engine.
- Product flavours `full` and `playstore`; the fork only ships `full`.
- All four ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) plus a universal APK. x86_64 is
  kept deliberately — it is the only way to run the app on an emulator for testing.
- Debug builds are signed with the release config, so `local.properties` needs signing values
  even for `assembleFullDebug`.
- Build: `./gradlew :app:assembleFullDebug`. Install: `adb install -r app/build/outputs/apk/full/debug/app-full-<abi>-debug.apk`.
- The debug application id is `com.nuviodebug.com`; the launcher activity is
  `com.nuvio.tv.MainActivity` under the LEANBACK launcher category.

## Releases

A GitHub Actions workflow rebases onto upstream every 6 hours and publishes a signed release
when upstream moves. The app's in-app updater points at this repo, so a successful run is what
makes the TV offer an update. Failures never publish, and each opens a GitHub issue. See
[FORK.md](FORK.md) for the three failure modes.

## Features this fork adds

Surprise me (screen + detail-page shuffle button, long-press to pick the stream, next suggestion
preloaded); play a random episode; a Top rated tab listing episodes by rating; night mode
dimming with a 6am switch-off; next-episode stream chosen before the current episode ends; and
Addon speed.

**Addon speed** lives at Settings → Playback → Addon speed, in both Essential and Advanced
modes. It shows a 30-day rolling history recorded passively from normal use — per addon, split
into metadata and scraping, reported as median, 95th percentile, request count and failures —
and an on-demand test over three fixed titles that times each scraper individually and then all
of them together. Failed requests are excluded from timings so a fast failure is not mistaken
for a fast addon. History is a plain append-only TSV in the app's files directory, pruned to 30
days; recording is fire-and-forget so it can never slow down the request it measures.

## Behaviour that differs from upstream

Dolby Vision Profile 7 conversion is on in release builds. Crash reporting is off — the DSN is
forced empty and the Sentry Gradle plugin is not applied.

## What is deliberately preserved

Upstream is removing the built-in debrid integration. This fork keeps **Torbox Instant** and
guards it in CI: a shell script checks that all 16 source files exist and still contain the
symbol that makes them matter, that the provider keeps its four capabilities, and that the
runbook is present. A failure blocks the release and opens an issue. Restore procedure and file
list: `preservation/TORBOX-INSTANT.md`; snapshot tag `torbox-instant-v1`.

Also kept where other forks strip: all four ABIs and the universal APK, Android TV
channel/preview-program sync, the home layout picker, colour themes, IAMF and MPEG-H decoders.

## Constraints and edge cases

- **Sync must stay hands-off.** The owner is not a developer and cannot resolve a rebase
  conflict. Every change should therefore prefer small, self-contained edits in files upstream
  rarely touches; anything that widens the conflict surface is a real cost, not a stylistic one.
- **Upstream's unit tests do not compile**, so `./gradlew test` cannot be used as a gate. Any CI
  guard must not depend on compiling the test source set.
- `scripts/` is gitignored wholesale; anything CI needs from it must be un-ignored explicitly.
- Shell scripts are pinned to LF via `.gitattributes` — this is a Windows working copy and the
  runner is Ubuntu.
