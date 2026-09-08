# What this fork changes

`Titanspark21/NuvioTV` tracks [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV)
and merges it every 6 hours, publishing a signed APK whenever upstream moves. Everything
below is this fork's own work, sitting as ordinary commits on top of upstream.

This file is the list. It is kept accurate as changes land; the commit history is the
changelog.

---

## Features added

**Surprise me** — pick something to watch from your recommendation addons, as a full screen and
as a shuffle button on a show's detail page. Long-press picks the stream yourself instead of
auto-playing; the next suggestion is preloaded so a re-roll is instant.

**Play a random episode** from any show's detail page, and keep shuffling from the player.

**Top rated tab** — every episode of a show listed by rating, opened at the top.

**Night mode** — screen dimming that switches itself off at 6am, closable from the player.

**Dialogue Leveler** (Player → Audio) — a four-level dynamic-range compressor that lifts quiet
speech and tames loud music/effects. It runs on both ExoPlayer PCM and MPV, keeps stereo channels
linked, caps peaks safely, remembers the selected level, and reapplies its MPV filter after an
audio-output reload.

**Addon speed** (Settings → Playback → Addon speed) — how fast each addon actually answers.
Two things in one screen:

- *Last 30 days*, recorded passively from normal use, per addon, split into metadata and
  scraping. Median is the typical wait; 95th percentile is the bad case you notice. Failed
  requests are counted but excluded from the timings, so an addon that errors quickly is not
  flattered.
- *A test you can run*, which asks every addon the same three titles. Each scraper is timed on
  its own — that is the number that identifies the slow one — and then all of them together,
  which is what pressing play actually costs.

**Next episode chosen early** — the stream for the next episode is found before the current one
ends, so it starts without a pause.

## Performance

Ported from [ysosrs123/NuvioTV-Fork](https://github.com/ysosrs123/NuvioTV-Fork), a sibling fork
of the same upstream tuned for 100GB 4K remuxes. Its buffering work turned out to be bug-fixing
rather than bigger numbers — its buffer defaults are identical to upstream's — so it applies to
ordinary 1080p streams too.

**Playback**

- Reads are served from the in-flight chunk as bytes land, instead of blocking until a whole
  8–16MB chunk arrives. The wait is chunk size divided by link speed, so this helps a slow
  connection more, not less.
- Chunks inside the live prefetch window are no longer evicted and then re-downloaded.
- A debrid 429 mid-film backs off and recovers after a cooldown, instead of staying throttled
  for the rest of the film.
- **MP4 seeks stop thrashing.** Non-faststart and badly-interleaved MP4s keep their index at the
  end of the file, so every seek sent a plain HTTP source back and forth reopening the
  connection. MP4 now goes through the chunk session — single connection, 8MB chunks — so
  backward reads come from what is already downloaded.

**Browsing**

- Coil's crossfade is off on card posters and logos; it was replaying on every recycle during
  scroll, so loaded posters visibly dimmed and faded back in. The hero backdrop keeps it.
- Original-size TMDB images are rewritten to a size bucket matching the display target.
  Multi-megabyte originals took 600–1200ms to decode, which is what left cards blank.
- Image responses with no usable `Cache-Control` get a one-week max-age, so they stop
  re-downloading on every scroll return. This is on Coil's own HTTP client, not the API client.
- Catalogue load concurrency 3 → 6.

**Deliberately not taken:** the TRaSH-guides quality filters and defaults (they drop any stream
whose resolution will not parse and exclude ~150 release groups — good for a remux library,
destructive for ordinary and anime content), and the back-buffer 15s→5s change (a fix for
90+ Mbps remuxes, a small loss at ordinary bitrates).

## Behaviour changed

**Dolby Vision Profile 7** conversion is enabled in release builds.

**Crash reporting is off.** Upstream's Sentry DSN reports to upstream's project, so a configured
fork build would send this fork's crashes and breadcrumbs to someone else's dashboard. The DSN
is forced empty, which is what disables it. The Sentry Gradle plugin — which rewrote every class
at build time and uploaded ProGuard mappings — is not applied.

## Deliberately kept

Upstream is removing the built-in debrid integration for app-store-policy reasons. **This fork
keeps Torbox Instant**, and guards it: `scripts/check-torbox-instant.sh` runs on every sync and
fails the release if any of the 16 files it is built from is deleted *or emptied out*, if the
provider loses one of its four capabilities, or if the runbook disappears. Nothing is published
when it trips, and a GitHub issue is opened. Full detail and the restore procedure:
[`preservation/TORBOX-INSTANT.md`](preservation/TORBOX-INSTANT.md). A known-good snapshot is
tagged `torbox-instant-v1` and pushed.

Also kept, against what other forks strip: **all four ABIs and the universal APK**, the
**Android TV channel/preview-program sync** that feeds the Google TV home screen rows, the
**home layout picker**, **colour themes**, and the **IAMF and MPEG-H audio decoders**.

## Release automation

`.github/workflows/fork-sync-and-release.yml` merges upstream every 6 hours and publishes
a signed release only if everything passes. Three things can stop it, and each opens a GitHub
issue rather than only reddening the Actions tab:

| What happened | What you get |
| --- | --- |
| Upstream edited the same lines this fork changes | "Upstream sync needs a hand" |
| Torbox Instant is missing or gutted | "Torbox Instant is missing - sync halted" |
| Anything else — build failure, bad secret, failed publish | "Upstream sync failed" |

Each de-duplicates: a repeat failure comments on the open issue instead of filing a new one
every 6 hours. In all three cases nothing is pushed and nothing is published, so the TV keeps
offering the last good build.

---

## Known issues

**Upstream's unit tests do not compile.** As of 2026-08-16, `TmdbCollectionSourceResolverTest`,
two `TrailerService` tests and `SearchViewModelConcurrencyTest` fail to compile against
upstream's own sources, so `./gradlew test` is unusable. This is why the Torbox guard is a shell
script and not a unit test — a test-based guard is hostage to upstream's test health, and would
have halted every release for an unrelated reason.
