# Cinematic Premium 1.3 — verification and delivery

November 8, 2026. Existing app enhanced in place; version name **1.3.0**, version code **4**. The deliverable remains a debug-signed testing build.

## Results

| Check | Result |
| --- | --- |
| Build | Debug app, isolated QA app and QA instrumentation APK compile successfully |
| JVM unit suite | **39 passed**, zero failures/errors |
| Final full phone suite | **17 passed, 1 skipped**, zero failures/errors; the skipped check requires a wide display for the TV sidebar |
| Wide coverage | The 18-test wide suite passed 17 checks; its poster test initially waited for an off-screen rail. The corrected rail-specific test plus sidebar, preview and Arabic checks subsequently all passed. All 18 distinct wide checks therefore have passing coverage across the suite and rechecks |
| Final contrast recheck | **3 passed**: complete login/catalog/favorites/episodes/account cleanup, universal search and Arabic large-text login |
| Final wide visual recheck | **4 passed**: rail-specific poster focus restoration, TV sidebar focus, preview/fullscreen handoff, Arabic large text |
| Android lint | **0 errors, 30 advisory warnings**, no error-hiding baseline |
| APK signature | Verified with APK Signature Scheme v2; one development/debug signer |

The final full phone run reported 76.577 seconds of test execution (Gradle 1m27s). The earlier corrected four-check wide run reported 15.367 seconds (Gradle 29s). The final screenshot-collection wide run took 596.961 seconds (Gradle 10m4s); this variability is explicitly retained in the raw results and is not a playback/UI speed measurement. These are emulator debug runs, not physical-device performance certification.

Test configuration: API-33 Pixel 6 emulator, phone 1080×2400 at 420dpi, wide 1920×1080 at 320dpi. The TV-specific focus test supplies the TV shell context on this emulator; this is not a physical Android TV device. The emulator's size and density were reset after verification.

## What the tests establish

- Xtream input normalization, encoded authentication/stream paths, account validation, denied/malformed/unreachable responses, metadata parsing and missing artwork remain covered.
- Real in-process fixture HTTP requests drive the actual app screens and Media3 engine. MP4/HLS playback, retry, seek/pause, aspect, PiP and same-engine live/episode switching remain covered.
- Preview browsing opens no video connections. Explicit preview playback reaches READY. Fullscreen releases the embedded player's ownership before its activity player becomes ready. Returning does not restart preview automatically after that handoff.
- Favorites/watchlist, bounded history/resume, completion flags, parent-series identity, season restoration, watched state, previous/next/autoplay and account cleanup remain covered.
- Poster focus distinguishes the rail as well as the content key, including a title appearing in multiple collections. The TV drawer retains the focused node when its labels expand and its selected destination changes.
- Screen lock disables the transport overlay, unlock restores it, and the same player remains attached. Quality selection is exposed only when supported video variants exist.
- EPG tests cover current/end boundaries, progress clamping, actual gaps, bounded invalid geometry, epoch behavior across a DST boundary, optional descriptions and rejection of invalid provider intervals.
- The existing native SQLite tests still exercise 50,000 generated test rows, bounded pages, literal query filters, account isolation and reopening the cache.
- Arabic RTL login at 1.8× system font scale keeps the connection action reachable after keyboard dismissal. The common form style now uses the high-contrast purple foreground for focused labels/cursors/borders.

The JVM's 50,000-item query logged **23.48ms** on the host. This measures a host query only; no new frame-time, startup, memory or low-end Android performance improvement is claimed.

## Corrections retained in the evidence

Initial wide checks exposed exact-string assumptions about episode whitespace after moving labels to Android resources. Assertions now follow the normalized label. A new poster test also needed to scroll to its rail and select a card within that specific rail, because the same title appears in multiple collections. The app now scopes saved focus by rail/content and delays creating restored lazy content until cached rows are ready, preventing an empty intermediate list from resetting scroll position.

A later Arabic test clicked while its input keyboard was still active; the harness now dismisses the keyboard and verifies that the connection button is displayed before tapping. The final recheck passed. Initial failed logs/XML remain in the evidence folder alongside successful results; they are not hidden.

## Screenshots

All images use test-only provider fixtures. Test artwork and sample streams are absent from the normal APK. The fixture catalog is intentionally small and is not a substitute production catalog.

- [Phone home](screenshots/cinematic/phone-modern-home.png)
- [Phone live channels](screenshots/cinematic/phone-catalog.png)
- [Phone live preview](screenshots/cinematic/phone-cinema-live-preview.png)
- [Phone movies](screenshots/cinematic/phone-vod-movies-home.png)
- [Phone movie details](screenshots/cinematic/phone-vod-movie-details.png)
- [Phone episodes](screenshots/cinematic/phone-vod-series-details.png)
- [Phone Arabic / large text](screenshots/cinematic/phone-arabic-large-text.png)
- [Wide TV sidebar focus](screenshots/cinematic/wide-cinema-tv-sidebar.png)
- [Wide restored poster focus](screenshots/cinematic/wide-cinema-restored-poster-focus.png)
- [Wide live workspace](screenshots/cinematic/wide-cinema-live-guide.png)

Phone hero/details/live/Arabic screenshots and wide sidebar/focus/live screenshots were visually inspected. Missing-logo fallbacks, long content and provider-artwork variation still need validation with an actual provider catalog. A complete TalkBack sweep, all-screen RTL/maximum-font pass, physical remote test and native speaker review remain outstanding.

## Artifacts and source

- [Versioned APK](../dist/IPTV-1.3-debug.apk)
- [Latest APK alias](../dist/IPTV-debug.apk)
- [Versioned source archive](../dist/IPTV-1.3-source.zip)
- [Checksums](../dist/SHA256SUMS.txt)
- [Audit and roadmap](CINEMATIC_REDESIGN.md)
- [Implementation / screen hierarchy / changed files](CINEMATIC_IMPLEMENTATION.md)
- [Raw test evidence](test-results/cinematic/)

Source packaging excludes Gradle/build caches, local SDK configuration, credentials, signing keys and distribution files. It includes test fixtures and their existing notice as source-only test assets. Historical versioned artifacts are retained.

## Remaining product and release limits

No real Xtream account was supplied for this redesign, so production provider compatibility has not been re-certified. Physical TV, Android 8/current-OS runtime, long sessions, low-memory devices and production startup/frame-time profiling remain unverified. The app uses its existing one-account architecture and stock Media3 transport; no wholesale framework migration was made.

The guide visualizes the provider's selected-channel short EPG. A synchronized multi-channel/day archive, archive search/date browsing, catch-up, profile isolation, M3U/XMLTV import and the other explicitly excluded capabilities in the implementation report are not present. Arabic covers core UI and common feedback; some legacy technical/provider-derived text can remain English. This build should undergo those provider/device/accessibility checks before production distribution.
