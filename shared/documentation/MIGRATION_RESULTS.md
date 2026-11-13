# Migration execution and validation

Assessment began November 12, 2026 (Dubai); final checks completed November 13. Baseline: commit `62ad776`. These are executed local results. The original Android reports remain in `android-history/` as historical records.

## Phase 1 — repository assessment

Inspected the production Kotlin, manifest, Gradle configuration, resources, fixtures and previous test reports. `MIGRATION_PLAN.md` records the actual 19-file / 3,057-line Android production implementation and its feature inventory, migration sequence and platform challenges. API contracts and cinematic design tokens were extracted into `shared/api-contracts/` and `shared/design-tokens/`.

## Phase 2 — monorepo migration

Moved the complete Gradle project to `apps/android/` and legacy documentation to `shared/documentation/android-history/`. Added independent build/setup scripts and ignore rules. Historical `dist/` binaries/source archives remain explicitly labeled as pre-migration artifacts.

A byte comparison against Git HEAD confirmed that all **49 tracked Android project files are unchanged** at their new paths. Git can detect the moves by similarity when committed; no source history was rewritten.

Executed Android build/JVM tests/lint: **BUILD SUCCESSFUL**, 48 tasks executed in 48 seconds. JVM XML reports contain **39 tests, zero failures/errors**. Lint completed with **0 errors and 30 existing advisory warnings**, without adding a baseline. The migrated QA instrumentation suite then completed on API-33 Pixel 6: **17 passed, 1 skipped, zero failures/errors** in the authoritative QA XML report. The skip requires a wide display. Gradle completed in 4m42s. The emulator started for this check was closed afterward.

## Phase 3 — native iOS foundation

Added `apps/ios/IPTV.xcodeproj`, a local `Core/Package.swift`, typed domain/API/storage models, URLSession transport, Keychain vault, SQLite actor, session view model, app composition, native navigation, semantic design system, privacy resources, English/Arabic strings and the existing brand's app icon.

The app targets iOS/iPadOS 17+. Builds used Xcode 27.2 / Swift 6.4.2 in Swift 5 language mode. The local core package is linked statically through the app host once, avoiding duplicate linkage in app tests. Simulator builds use ad hoc signing so the real Keychain receives an app identity entitlement; no developer certificate or production credential was used. Disabling Xcode's debug-dylib launcher resolved an initial pre-main simulator loader stall.

## Phase 4 — feature implementation

Implemented real provider login/restoration, Home discovery, paged browsing and filters, universal/recent/voice search, watchlists/history, rich details, seasons/episodes, exact continuation, watched markers, live EPG/timeline, explicit preview and full-screen playback. Playback includes resume/restart, audio/subtitle controls, real variant quality limits, aspect modes, bounded retries, lock controls, PiP, channel switching and episode queues/autoplay. Settings include both appearances, accent/text/contrast, landing/Home rows, completion/autoplay, artwork removal and bounded appearance import/export/reset.

Production uses the actual provider repository and native playback/storage services. Test providers, HTTP fixtures and test media are confined to tests and repository utilities. The complete inventory and implementation mapping are in `FEATURE_PARITY.md`.

## Phase 5 — UI and platform consistency

Implemented native phone tabs, iPad sidebar and side-by-side channel/guide layout, adaptive posters/actions, safe areas, Dynamic Type, RTL and reduced motion. Small violet action text measures **9.33:1** against dark cards; primary violet fills with white text measure **5.70:1**. Decorative artwork no longer intercepts touches outside clipped image bounds; the regression is exercised by season watched actions and poster navigation.

Phone/iPad screenshots were inspected and retained under `validation/screenshots/`. Full-screen capture avoids the orientation/cropping issue encountered with application-only iPad screenshots. Arabic large-text login was executed on both devices. This is selected simulator coverage, not a claim of exhaustive screen-reader or physical-device certification.

## Phase 6 — executed validation

| Check | Result |
|---|---|
| Android debug build + JVM tests + lint | Passed; 39 JVM tests; lint 0 errors / 30 warnings |
| Android QA device suite | Passed; 17 passed, 1 wide-display check skipped |
| Swift core tests | **20 passed**, zero failures |
| iPhone 18 Pro / iOS 27 native suite | **6 passed**, zero failures/skips, no reported runtime warnings in final result bundle |
| iPad Pro 11-inch M4 / iOS 18.1 native suite | **6 passed**, zero failures/skips in the full suite |
| Final iPad provider-flow recheck | **Passed**, including compatible HLS readiness, poster navigation, series state and sign-out |
| Final Xcode analysis | **ANALYZE SUCCEEDED** |
| Swift formatting lint | Passed for all application/core/native test sources |
| Shared JSON/token/example checks | Passed |
| Shell/Python/workflow syntax | Shell checks, Python compilation and workflow YAML parsing passed |

The native suites contain three app tests (invalid session input, real Keychain round trip, session/library/sign-out lifecycle) and three UI tests (Arabic large text, full HTTP-provider journey, login validation). The HTTP journey checks actual MP4 presentation dimensions and real HLS preview readiness, plus catalogs, details, favorites, watched season state, navigation and logout. The final iPad recheck completed in **50.559 seconds** with the complete wrapper returning success.

Core coverage includes normalization, PHP-safe plus-sign query encoding, encoded stream segments, auth/status/expiry, sanitized HTTP/malformed/network failures, heterogeneous scalar fields, ordered episodes/parent identity, EPG decoding/intervals, escaped search, indexed disk paging and account isolation, library persistence, completion/watched state, resume after history eviction, retry budget, metadata/trailer validation, appearance bounds and recommendations.

### Failures investigated rather than hidden

- Initial simulator loader stalls and unsigned-Keychain access were resolved by static linkage, launcher configuration and local simulator signing.
- Clipped decorative image hit regions blocked a season action; disabling decorative hit testing and defining card touch bounds fixed the full UI journey.
- A runtime warning identified audio activation on the main thread. A serial audio-session worker and owner checks now handle playback/preview/voice activation; final iPhone and iPad result summaries report no runtime warnings.
- The old Android TS fixture contains MPEG-2 video. The iOS HLS success test now uses a licensed H.264/fMP4 derivative generated with AVFoundation. The original Android asset remains unchanged; this does not add transcoding to either production app.
- One later iPad suite attempt failed only while requesting a screenshot after prolonged pauses; it was not called passing. The affected journey was rerun with idle sleep prevented and passed. Earlier full-suite and final recheck summaries are retained separately.
- During development a running shell wrapper was edited and reported an EOF after Xcode itself had passed. The stable final wrapper passed syntax checks and the final iPad recheck exited successfully. Failed/interrupted development xcresult bundles remain ignored under `.validation/`, not represented as passing runs.

Evidence: `validation/` contains raw Android logs/XML, core output, final analyzer output, iPhone/iPad result summaries and selected screenshots. Full local xcresult bundles remain under ignored `.validation/`.

## Phase 7 — CI and developer documentation

Added independent Android/iOS/shared GitHub workflows, including Android emulator instrumentation, native iOS/fixture tests, formatting/static checks and report artifacts. Platform path filters avoid building the other app for changes confined to one platform. Shared API/design changes validate both. Added reproducible build, fixture, generation, localization and setup utilities, architecture decisions, design guidance, API/appearance contracts and a comprehensive parity matrix.

Workflow syntax was checked locally and action versions checked against maintainer usage documentation. Remote GitHub workflows have **not** been executed in this migration. Store deployment is not configured with fabricated credentials.

## Remaining release validation

The implemented clients and local automation are reviewable and build independently. Physical iPhone/iPad PiP, actual microphone permission/recognition behavior, production provider accounts, multitrack/multivariant streams, exhaustive accessibility and the production codec/DRM matrix still require device/provider checks. Light appearance is implemented and compiles, but the retained screenshot suite is primarily dark appearance. Production signing and store publication require the owner's identifiers and credentials.

AVPlayer does not match Media3's complete decoder coverage: raw MPEG-2, some TS/MKV/DASH streams or DRM configurations can need a provider HLS/MP4 alternative or further engine integration. No universal stream compatibility or production-release certification is claimed.
