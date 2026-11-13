# Modernization audit — November 8, 2026

## Existing application

Kotlin throughout, one Android application module, minimum API 26, compile SDK 36 and target SDK 35. Compose Material 3 renders sign-in, catalog and details; a native View-based Media3 PlayerView renders video. A small MVVM-style AndroidViewModel owns Compose state. OkHttp and Gson implement Xtream API integration behind ProviderRepository. Coil loads artwork. No server, analytics, database, WorkManager or dependency-injection framework is present.

Working behavior: Xtream account validation, HTTP/HTTPS/base-path handling, live/movie/series categories, category search, movie details, sorted episodes, short now/next EPG, local favorites, on-demand resume, retry, MediaSession, audio/subtitle selection, HLS/MPEG-TS fallback, phone touch and TV D-pad navigation. The existing 15 JVM tests pass. Five prior fixture-based emulator integration tests cover these paths; new changes require a new regression run.

## Findings and priorities

| Priority | Finding | Change / risk |
|---|---|---|
| P0 | Provider credentials are properly Keystore AES-GCM encrypted and excluded from backup. Full URLs can still contain secrets in native media errors or UI inspection. | Preserve storage key and account scope. Keep URL logging disabled; mask entry, exclude secrets from diagnostic/export data. Do not capture real accounts in screenshots. |
| P0 | A CCcam account has no channel video source. Existing implementation supports Xtream only. | Do not relabel protocol support or claim CCcam playback. Additional protocols require a video source and separate compatibility work. |
| P1 | Four flat catalog tabs, repeated controls and generic cards; no home or settings. | Introduce adaptive navigation, a provider-backed home, cinematic artwork and coherent tokens. Preserve all playback entry paths. |
| P1 | Favorites repeatedly scanned per card; filtering and catalog transformation run on the main thread. | Precompute membership sets; move filtering, sorting and transformation to background dispatchers; debounce search and cancel stale results. |
| P1 | Section changes discard the catalog and revalidate/refetch even if nothing changed. | Bounded in-memory catalog cache with explicit refresh and account cleanup. No unbounded prefetch or parallel streams. |
| P1 | Resume stores a position only, so home cannot display continue-watching or viewing history. | Add versioned, bounded history records while preserving existing position keys. Maintain account isolation and completion reset. |
| P1 | Details discard most provider metadata; episodes are a flat list. | Preserve optional backdrop, release date, rating, cast, director and genre; add seasons and watched/resume feedback. Never manufacture scores or popularity. |
| P1 | Player has manual retry only; no PiP/aspect/adjacent-channel controls or useful diagnostics. | Keep PlayerView/MediaSession. Add bounded recovery, PiP, aspect controls and safe channel queue reuse with lifecycle tests. |
| P2 | No M3U import, XMLTV cache/timeline, profiles, parental controls, provider switching, schedule jobs or universal search. | Deliver as explicit later increments after the UI/data foundation. No inert controls advertising unfinished features. |
| P2 | All app copy is English; font/theme choices are absent. | Add appearance controls and primary navigation localization. Track remaining localization explicitly. |
| P2 | No benchmarks, physical TV testing, low-memory tests, vulnerability scan or release signing. | Record measured fixture timings and benchmark-only scalability results. Do not label a debug build production-certified. |

## Architecture decisions

Retain Kotlin/Compose, OkHttp/Gson, Coil, repository abstractions, the native player and one Gradle module. Separate design, presentation and persistent preferences into focused files rather than introducing dozens of empty modules. Retain the current encrypted credential format and legacy resume/favorite keys. A database and scheduled synchronization are appropriate for full XMLTV and large persistent catalogs, but are not prerequisites for the first visual increment.

## Performance and security baseline

No historical startup, memory, frame-time or channel-switch measurements were recorded; none are inferred from test pass results. Measure deterministic fixture operations separately from network/provider latency. Proposed device targets: cold launch to local sign-in under 2.5 s; local 50,000-item search under 250 ms after debounce; one active decoder/session; no main-thread network requests; bounded history and catalog caches. Startup/frame/memory/provider-switch targets need representative hardware measurements before becoming release guarantees.

No credential leak was found in app logging, backups or intent extras during source review. This is not a penetration test or dependency vulnerability certification. HTTP remains supported explicitly because providers may require it; TLS validation must remain enabled.

## Migration and regression risks

Storage migration must be additive and idempotent. Failed refresh must retain previously loaded content and label stale results. New home/search surfaces must route to existing details/playback using stable content keys. Navigation and player queues must preserve selection on Back, rotation and activity reentry. Focus must be checked with touch disabled; a resized phone emulator does not establish physical TV compatibility. No existing provider account or content should be embedded in the APK.

## Implementation references

Media3 compatibility was checked against [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/media3). Focus and navigation follow [Android TV guidance](https://developer.android.com/training/tv/get-started/navigation). These references inform implementation; physical-device verification is still required.
