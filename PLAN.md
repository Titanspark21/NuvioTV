# Plan

Outstanding work only. Finished batches are deleted; the commit that completed them is the
record.

## Other

### 2026-08-16 — porting performance work from the ysosrs123 fork

Reviewed [ysosrs123/NuvioTV-Fork](https://github.com/ysosrs123/NuvioTV-Fork) — a sibling fork
of the same upstream, tuned for 100GB 4K remuxes. Its buffering work is bug-fixing rather than
bigger numbers (its buffer defaults are identical to upstream's), so most of it is safe for
ordinary 1080p streams. Deliberately NOT taking: the TRaSH quality filters and defaults, the
back-buffer 15s→5s change, and its removals of TV channel sync, the layout picker and colour
themes.

**Blocked on being able to verify.** None of these can be tested on the only device available
(an x86_64 emulator with no addons, no debrid credentials and no TMDB key — `local.properties`
was wiped on 2026-08-12). Porting streaming-engine changes that cannot be exercised, into a
pipeline that auto-publishes to the TV, is the risk to weigh before starting.

- [ ] Decide how to verify: restore local credentials, or accept that the real device is the
      test and stage the ports one release at a time.
- [ ] Progressive reads from the in-flight chunk. Biggest win and bitrate-independent: today a
      read blocks until a whole 8–16MB chunk lands, even when the bytes it needs arrived
      hundreds of ms earlier. Done = first frame arrives without waiting on the full chunk.
- [ ] MP4 chunk-session seek fix. Non-faststart / badly-interleaved MP4s stop thrashing on
      every seek. Applies to ordinary WEB-DL, not just remuxes.
- [ ] Debrid rate-limit backoff and recovery. Today a 429 mid-film stays throttled to the
      credits. Done = speed recovers on its own after a cooldown.
- [ ] Browsing performance: poster downscale-before-decode, image caching for header-less
      responses, higher catalogue load concurrency, crossfade off on cards.
- [ ] Prefetch/prewarm startup chain (the ~6.4s → ~2s claim). Largest port, touches detail,
      stream and player screens — most rebase-conflict surface, so do it last and alone.
- [ ] Eviction re-download fix — only matters if parallel connections are turned on, which is
      off by default in both forks.

Each item lands as its own commit so a bad one can be reverted without taking the others.

## Bugs

_(none outstanding)_

## Visual

_(none outstanding)_

---

## Needs the real device

Things I cannot check from here — a Google TV Streamer with the real addons and debrid account.

- [ ] Confirm the per-addon scraping times in Settings → Playback → Addon speed look sane. The
      code path is verified and metadata timings are confirmed working, but this emulator has no
      scraping addons installed, so the per-addon stream rows have never returned real data.
