# Migration assessment and execution plan

Assessed November 12, 2026 against commit 62ad776. The Android app contains 3,057 lines of production Kotlin across 19 files, one application module, Compose/Material 3, Media3, OkHttp, Gson and Coil. Its existing reports describe emulator validation; those are historical evidence, not results of this migration.

## Existing implementation inventory

| Area | Source | Behavior to preserve and port |
|---|---|---|
| Session | Provider.kt, Storage.kt, MainActivity.kt | Host/base path, scheme and port normalization; secure password field; Xtream authentication, status and expiry validation; encrypted saved account; restore and sign out with data removal |
| Navigation | AppViewModel.kt, PremiumUi.kt | Home, Live, Movies, Series, Favorites, Search, Settings; phone bottom navigation and wide sidebar; details then player then return; state retained per section |
| Home | PremiumUi.kt | Artwork hero, previous/next and optional 12-second rotation, continue watching, recent channels, favorites, live/movie/series rails, genre rails and local recommendations |
| Catalog | VodExperience.kt, Preferences.kt, VodCatalogStore.kt | Discover and browse; category/title/genre/year/language filters, provider/name/added/release/rating sorts, 48-item pages, grid/list, recent and voice searches, account-scoped SQLite catalog cache |
| Details | VodDetailsScreen.kt, Provider.kt | Plot, cast, director, rating, artwork, genre/year/duration/classification/languages/subtitles, safe trailer links, technical info, related titles, watchlist, resume/restart, watched toggles |
| Series | VodModels.kt, VodDetailsScreen.kt | Ordered seasons and episodes; selected season persistence; episode and season watched state; exact continuation, previous/next/browser, optional autoplay |
| Live | LiveTvScreen.kt, GuideLayout.kt | Category/channel/guide workspace; explicit preview only; selected-channel eight-program EPG, now/next and timeline; periodic refresh; favorites; full screen handoff |
| Playback | PlayerActivity.kt, PlaybackPolicy.kt | HLS/MPEG-TS, VOD stream paths, resume/checkpoints, seeking, pause, audio/subtitles, aspect, real video quality choices, two bounded network retries, manual retry, diagnostic info, lock controls, PiP, channel switching/previous channel, episode queue |
| Library | Storage.kt | Account isolation; 100 history entries, ten recent searches; favorites, watched markers, completion threshold 80–100%, remove/clear history and resume reset |
| Settings | SettingsScreen.kt, Preferences.kt | Dark/light, violet/cyan, font scaling, high contrast, optional motion/rotation, landing section, six hidden Home rails, autoplay/completion, clear artwork, bounded JSON appearance import/export/reset, diagnostics |
| Localization | values-ar, UiLabels.kt | English/Arabic core UI, RTL, system large text, readable focus/touch targets |

## Platform decisions and challenges

- Retain Android source and Gradle settings unchanged during the move. Git similarity detection preserves file history. Build outputs and local machine paths stay ignored.
- Native iOS 17+ SwiftUI shell and MVVM; a local Swift package separates domain/API/persistence from presentation. URLSession uses async/await; Keychain uses device-only accessibility; SQLite provides indexed disk paging; AVKit owns video and native media selection/PiP.
- AVPlayer supports a narrower codec/container set than Media3. A provider's raw TS, MKV or DASH stream may require a provider HLS/MP4 endpoint or a separately evaluated decoding engine. Never label a format conversion as implemented. Report unsupported streams explicitly.
- Native dictation/voice requires contextual permissions. TV remote behavior applies to Android TV; iOS targets iPhone/iPad, with keyboard and VoiceOver navigation.
- Device signing and production provider credentials are not present. Simulator and fixture checks can establish integration, but cannot certify all providers, codecs, DRM, physical PiP or App Store readiness.

## Incremental plan and acceptance gates

1. **Assessment:** inventory above, shared API contracts/tokens, parity checklist. Complete before moving application files.
2. **Monorepo:** move Gradle project to apps/android; retain historical docs under shared/documentation/android-history; add setup and independent build scripts. Gate: Android assemble, JVM tests and lint.
3. **iOS foundation:** local IPTVCore Swift package, native Xcode project, secure session, actual provider client, persistence, theme and adaptive shell. Gate: package tests and unsigned simulator build.
4. **Features:** implement provider catalogs, home, search, favorites/history, details/series, live guide/preview and player; settings and localization. Gate: domain/HTTP/persistence tests, compile, fixture-driven app UI checks.
5. **Visual parity:** shared semantic tokens; artwork hero/cards/rails; native tabs and iPad sidebar; dynamic type, RTL and reduced motion. Gate: simulator phone/tablet inspection when available.
6. **Validation:** run both builds and tests, record exact outcomes and remaining device/provider limitations. Fix implementation failures before final report.
7. **Automation/docs:** path-filtered GitHub Actions, dependency resolution, build/test/setup instructions, decisions, parity results. No signing material committed.

Each phase's changed files, results and remaining work are recorded in MIGRATION_RESULTS.md. Unexecuted checks remain unverified, never marked passing.
