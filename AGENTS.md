# NuvioTV project notes

NuvioTV is a Kotlin/Jetpack Compose for TV Android app. This fork is built for a Google TV
Streamer and ships the `full` flavor with four ABI APKs plus a universal APK.

Build locally with `./gradlew :app:assembleFullDebug` or
`./gradlew :app:assembleFullRelease`; signing and SDK paths come from `local.properties` and
the local keystore. The scheduled release workflow fetches `upstream/dev`, merges it into the
fork, runs the Torbox preservation guard, and publishes signed GitHub release APKs. The app's
full flavor updater reads releases from `Titanspark21/NuvioTV`.

The audio controls include Dialogue Leveler (dynamic-range compression) for both ExoPlayer PCM
and MPV/libavfilter playback. Its level is persisted independently, uses a peak safety ceiling,
and is re-applied after MPV audio-output reloads.

Recent requests:

- 2026-09-08 — repair repeated upstream-sync failures, merge current upstream changes, and keep
  the fork release path hands-off.
- 2026-09-08 — restore and harden Dialogue Leveler so speech, music, and effects stay closer in
  perceived volume; publish a newer release for the in-app downloader.
