# Modernization roadmap

Historical 1.1 roadmap. Release 1.2 implements the VOD catalog index, dedicated Movies/Series screens, episode queues and personalization described in [VOD_VALIDATION.md](VOD_VALIDATION.md); remaining items below are not all delivered.

## Delivery strategy

Enhance the existing application incrementally. Each release must compile, preserve the existing fixture flows and document unverified hardware/provider behavior. The first release implements the highest-impact visual and navigation foundation with real data, local history and practical player improvements. The complete master brief is a multi-release roadmap; the status matrix must distinguish shipped integration from future work.

## 1 — Audit and foundation

Complete the source audit and baseline JVM run before refactoring. Define semantic colors, typography, spacing, focus and motion behavior. Preserve credential keys and legacy local-library data. Add focused models for catalog metadata, viewing history and appearance settings. Require no new service credentials.

## 2 — Premium UI and navigation (first release)

Add Home, Live TV, Movies, Series, Favorites, Search and Settings. Compact layouts use Material bottom navigation for Home/Live/Library/Search/Settings; Library exposes Movies/Series/Favorites. Regular/TV layouts expose all sections in a side rail. Use a single selected-section state, explicit Back handling and retained scroll/focus state. Cards use provider artwork with text fallbacks, stable IDs and favorite state from precomputed sets.

Home uses provider-backed featured content, continue watching, recent channels, favorites, movies and series. Empty rows disappear. Hero details come from the provider; no fabricated trending, sports labels or ratings. Hero rotation is off initially and optional; no automatic video preview. Settings expose only working controls. Initial design tokens: background #080B12, navigation #111827, cards #1B2433, readable accent #B9BCFF, cyan #67E8F9, foreground #F9FAFB, secondary #B0B9C8. Use 48-dp minimum actions, generous focus rings and system text scaling. Keep motion optional.

## 3 — IPTV core

First release: asynchronous debounced in-section/universal catalog search, sorting, cached section navigation, refresh recovery, bounded history, favorites, recently watched and continue-watching. M3U/local-file ingestion, encrypted multiple-provider vault, persistent indexed catalog, XMLTV streaming parser/database, timeline guide and scheduled refresh are a separate migration with import/export and schema tests. Preserve the existing short EPG until that integration is tested. Display catch-up/reminders only after provider capability and notification scheduling tests exist.

## 4 — Player

Keep Media3 PlayerView and update within the Media3 family only after compatibility verification. Add aspect modes, PiP on supported devices, adjacent-channel reuse and previous-channel control, bounded transient-error retries with cancellation, paused-state restoration and safe diagnostics. Existing audio/subtitle/quality/speed controls remain native. Cast, DRM license integration, external subtitle fetching, background services and delay controls require source-specific configuration and isolated follow-up acceptance tests; do not simulate them.

## 5 — Entertainment and personalization

First release: richer movie/series metadata, season selection, history/resume/restart, saved appearance preferences and configurable home rows. Local profiles, restricted-category policies, salted PIN storage, kids allowlists and per-profile migration form the next state-management increment. On-device metadata recommendations may follow; do not claim popularity or child safety without verified metadata. AI stays optional and requires an explicit API/privacy design; core playback must never depend on it.

## 6 — Verification and release

For this release: JVM tests for parsing, migration, bounded history, filtering/sorting and isolation; fixture integration tests for existing features plus home/settings/navigation; phone and TV-size screenshot review; player failure/retry/back regressions; signed debug APK and source archive. Record actual test counts, timings and limitations in MODERNIZATION_VALIDATION.md.

Before a store release: physical phone/tablet/TV testing, Android 8/current SDK tests, low-memory profiling, frame-time/startup/switch-latency benchmarks, provider large-catalog/EPG benchmarks, dependency vulnerability review, complete Arabic/English localization and TalkBack audit, privacy policy, release signing and store assets. Performance targets are acceptance goals, never invented results.

## Acceptance gates

- Existing account keys/favorites/progress load after update, and sign-out clears account caches/history.
- Every visible action changes real state or invokes real playback; no production fixtures.
- Navigation remains usable using D-pad/Select/Back; no duplicate active playback session.
- Empty/missing metadata works and failure surfaces retain a clear retry path.
- Search filtering is cancellable and computed off the main thread, with stable result identities.
- All claims in the release notes correspond to a source integration and a recorded test or explicit verification limit.
