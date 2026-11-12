# Movies & Series release 1.2 — implementation and verification

November 8, 2026. This document describes the enhanced existing Android client; historical 1.1 reports describe the previous release.

## Integrated phases and changed files

1. Assessment: `VOD_ENHANCEMENT_PLAN.md` records the original architecture, missing behavior, screen designs and prioritized approach before the changes.
2. Shared components: `VodExperience.kt`, `VodDetailsScreen.kt` and `PremiumUi.kt` integrate FeaturedHero, poster/continuation cards, provider-backed carousels, genre chips, details headers, episode cards and loading placeholders. They use the existing semantic design system and adaptive shell.
3. Movies: dedicated discovery, metadata-aware hero, watchlist/continue/recent/category/rating/recommendation rails where data supports them; 48-item grid/list pages with category/genre/year/language filters, title search and available sorts. Details show provider facts, cast/director/languages/subtitles, related category titles and an optional validated trailer/information panel.
4. Series: actual provider seasons and ordered episodes, thumbnails/synopsis/duration, exact parent-series continuation, durable watched flags, episode/season watched actions, remembered season and per-episode resume/restart. Episode action buttons wrap onto additional lines on phones.
5. Player/personalization: `PlayerActivity.kt`, `MainActivity.kt`, `Storage.kt`, `PlaybackPreferences.kt` and `VodModels.kt` integrate an opaque account-scoped SQLite episode queue, previous/next, picker and opt-in autoplay while reusing the player/session. The player checkpoints on pause before details refresh. Watch history remains bounded to 100 unique items; explicit watched IDs persist beyond that limit. Completion defaults to 95%, configurable in Settings. Missing duration never implies completion. Favorites remain the account's watchlist; per-item history removal resets its resume position without deleting favorites.
6. Performance/QA: `VodCatalogStore.kt` adds a private SQLite cache, transactional snapshots, parameterized filters, bounded query pages and scope-checked queues. Provider parsing and database work run on background dispatchers. VOD title search uses the cache, with a 240-character query bound and debounce. Dedicated browsers and universal Search show recent searches and delegate voice search only when an installed recognizer is available. Universal Search groups capped movie/series/channel matches by type.

`Provider.kt` extends optional metadata and explicit episode identity without replacing Xtream endpoints, stream URL authentication or Live TV. The manifest only adds voice-intent visibility. Existing AES-GCM/Android Keystore storage, backup exclusions and account key derivation remain. Sign-out clears the new catalog/queue data and local account library. Saveable navigation state is account-scoped and removed when the account changes or signs out, so a later sign-in cannot restore the previous account’s browse query. No credentials, API keys, provider accounts or fixture video are embedded in the normal APK.

## Verification

- Build: debug application, isolated QA application and QA instrumentation APKs build successfully.
- JVM: 33 tests pass, zero failures/errors. Provider parsing/auth errors, base URL/port validation, legacy favorite/position compatibility, account isolation, cleanup, completion thresholds, bounded history, exact episode continuation, durable watched flags, remembered seasons, search history, metadata validation, deterministic recommendations and retry limits are covered.
- Android lint: zero errors, 28 advisory warnings; no error-hiding baseline.
- Phone instrumentation: all 14 tests passed on the API-33 Pixel 6 emulator at 1080×2400 / 420 dpi (345.13 seconds). Includes 12 app flows and two native SQLite tests. Episode next/previous/autoplay preserve the player instance; resume/season/watched persistence, MP4/HLS playback, PiP, Live TV, catalog error/retry, focus/Select/Back, search, encrypted credentials and sign-out are covered.
- Final universal-search polish passed. The remote-season test initially attempted to request focus while Android was in touch input mode; enabling D-pad input first corrected the harness, and the final phone remote-season test passed.
- Wide layout: 1920×1080 / 320 dpi (960×540 dp) on the same API-33 phone emulator. Eleven of the twelve app flows passed in the first run. The movie-landing test waited for a rail below the viewport; it was corrected to scroll to the rail. The corrected movie flow and strengthened sign-out/relogin flow both passed on the final build (55.53 seconds). All twelve distinct app flows have passing wide-layout results; this was a suite plus targeted recheck, not one uninterrupted all-green run.
- Strengthened account cleanup checks assert that movie/series SQLite snapshots are empty after sign-out and that re-signing into the same account does not restore its old browse query/state. Both wide and phone checks passed. The final phone movie/cleanup recheck passed both tests in 65.55 seconds.
- [Raw emulator run logs](test-results/vod-1.2/) retain initial harness failures and final rechecks.
- Phone fixture screenshots were inspected for movie hero, browsing/list controls, movie details, series continuation, season selection, wrapped episode controls and missing artwork fallbacks. The real application APK contains no assets from androidTest.
- APK signature verifies using v2; this is a debug-signed test build.

Fixture media and server exist only in `androidTest`. The QA package is `com.iptv.app.qa`; normal account data is not cleared by tests. Network failure tests simulate HTTP 503 and retry, rather than claiming all physical network disconnect behavior is certified.

The normal `com.iptv.app` package was updated in place to version 1.2.0 (code 3), launched successfully and confirmed as the emulator's resumed activity. QA/test packages were removed after saving results. The display was restored to 1080×2400 / 420 dpi.

## Measurements and limits

Native API-33 emulator runs indexed 50,000 generated rows in 1,240.45 ms / 4,841.20 ms and queried one matching title in 36.76 ms / 205.72 ms respectively. The slower second observation came from the final full phone suite. These variable debug emulator observations are not physical low-memory TV data or production performance guarantees. Query pages are capped at 100, default 48; search does not materialize the full VOD catalog for each keystroke.

A single cold launch immediately after final installation reported 8,591 ms in `am start -W` on the loaded debug emulator. This exceeds a fast-start target; no startup improvement is claimed. Release-build startup/frame/memory profiling and optimization remain necessary before a production performance claim.

Xtream snapshot endpoints still require initial full-catalog ingestion, and the pre-existing three-section in-memory catalog cache remains. No server-side pagination or full-text search index is claimed: SQLite indexes scope/category/order and substring title filters use LIKE. Detail metadata is cached in process for at most 12 titles. Coil handles bounded displayed artwork and its cache; explicit predictive image preloading and a new image pipeline were not added.

Only actual provider categories and valid positive ratings produce those rails. Recommendations are deterministic local genre/category matches over bounded candidates, not popularity or editorial rankings. No unsupported trending, skip-intro timestamps or language/quality guarantees are fabricated. Related titles currently use the same provider category. Filters depend on catalog metadata; detail-only metadata is shown on details/hero and is not automatically enriched across every catalog row.

Physical Android TV, low-memory hardware, tablets, Android 8 runtime, current-OS runtime, production frame times, long-session memory/leaks, full TalkBack/RTL/maximum-font certification and voice-recognizer/provider-specific trailer compatibility remain unverified. A resized phone emulator checks responsive layout and some D-pad behavior; it does not certify physical TV firmware and remotes. No real Xtream test account was supplied for this release; live provider compatibility remains unverified. Existing CCcam credentials do not supply IPTV video and are not supported as Xtream accounts.

One saved account is supported; no profile system existed to integrate. M3U import, full XMLTV schedules, multiple accounts, Cast, downloads, recording and store publishing remain outside this release. Legacy episode history without parent-series identity is not guessed into a series continuation; existing direct episode resume positions remain readable.

## Screenshots

Fixture-only UI previews: [phone movies](screenshots/vod-1.2/phone-vod-movies-home.png), [phone movie details](screenshots/vod-1.2/phone-vod-movie-details.png), [phone episodes](screenshots/vod-1.2/phone-vod-series-details.png), [wide movies](screenshots/vod-1.2/wide-vod-movies-home.png), [wide browsing](screenshots/vod-1.2/wide-vod-movie-browse.png), [wide season focus](screenshots/vod-1.2/wide-vod-series-details.png).
