# Movies and Series assessment and implementation plan — November 8, 2026

## Assessment

The existing Kotlin/Compose MVVM-style app has a generic paged-in-memory catalog screen shared with Live TV. Xtream get_vod_streams/get_vod_info and get_series/get_series_info are integrated, with Keystore credentials, account-scoped favorites/positions and 100 recent history records. Media3 1.11.1 handles authenticated playback, tracks, quality, aspect, PiP and bounded retry. Coil caches images; saveable state retains navigation. Current limitations: Movies/Series have no dedicated landing rails, details lack a cinematic header, episode models lack parent IDs/descriptions/durations, season choices are not durable, no episode queue/autoplay, no durable watched set or per-item history cleanup, and every catalog search traverses in-memory lists.

## Priorities and screen designs

1. Keep protocol/authentication and Live TV unchanged. Extend optional metadata safely, preserving legacy credential, favorite and position keys. Add account-scoped SQLite VOD snapshots and parameterized paged queries. This is an additive index, not an architecture/framework rewrite. Xtream's full snapshot endpoint still requires initial catalog ingestion; no unsupported server pagination is claimed.
2. Movies/Series landing: provider-artwork hero, Continue Watching, Watchlist, provider-backed categories, recently added and valid-rating rails. Deterministic recommendation rails appear only when viewing/favorite signals match real provider metadata. Browse opens a grid/list with search, genre/category, year/language filters and title/release/added/rating sorting. Query pages are bounded; no fake popularity or child-safety claims.
3. Details: backdrop/poster/title/overview and available facts; resume/play/restart/watchlist; optional authorized trailer/provider technical metadata; related provider titles. Series show actual seasons, illustrated episode cards, exact continuation, durable season selection and explicit watched actions.
4. Player: persist an episode queue by opaque local token instead of large intent payloads. Reuse the existing player for previous/next episodes, episode picker and optional autoplay. Reset a new episode to its own resume position. Preserve live-channel controls, headers/authentication and lifecycle cleanup. Intro/credits controls stay absent without reliable timestamps.
5. Personalization: account-scoped watched IDs, individual history removal, recent searches and configurable completion/autoplay preferences. Profiles remain conditional on existing support (none exists); no invented profile integration. Export paths remain free of account secrets.
6. Verify fixture API metadata/malformed/empty responses, ordering, migration/isolation, catalog SQL filtering/pagination, recommendations, continuation, watch state, player episode controls and Live TV regressions. Run phone/TV-size QA in the separate .qa package. Measure generated large catalogs and identify hardware/network limitations honestly.

## Technical interfaces and compatibility

Extend CatalogItem/Episode/ContentDetails additively with nullable provider metadata and explicit parent-series/season/episode identity. Existing stream URL construction remains authoritative. VodCatalogStore owns the private SQLite snapshot/query/queue tables; UI queries run on Dispatchers.IO and return bounded pages. Existing favorites serve as movie/series Watchlist, with type filtering. LibraryRepository gains watched/season/search/history operations without replacing its old namespaces. An optional, deterministic RecommendationEngine operates locally; no API key, cloud AI or external metadata service is introduced.

## Delivery gates and limits

Each phase must compile and retain existing tests. No sample content or credentials enter the normal APK. Fixture images/media stay in androidTest. Tables use additive creation/versioning, and sign-out clears the new caches and queue data. Local document/M3U import is not currently supported and will not be falsely advertised. Physical low-memory Android TV, production frame/memory profiling, external subtitle inventories and provider-specific trailer behavior require separate device/source verification.
