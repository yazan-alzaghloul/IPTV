# Modernization release 1.1 — validation

Date: November 8, 2026 (Asia/Dubai).

## Delivered increment

The audit and roadmap were written before major refactoring. This release completes the first UI/navigation increment and selected data/player improvements; it does not claim the entire multi-phase master brief is implemented.

The existing Kotlin/Compose MVVM-style application, OkHttp/Gson Xtream API, Coil artwork, Keystore vault, legacy library keys and native Media3 player are retained. New code separates semantic design tokens, adaptive screens, appearance settings, catalog queries and retry policy. The only player-library migration is Media3 1.8.0 → compatible stable 1.11.1; builds and playback regressions verify compatibility on API 33.

Integrated behavior: provider-data Home hero and rows, optional non-video artwork rotation, adaptive navigation, per-section scroll/focus preservation, long-press favorites, retained catalog cache, off-main-thread filtering/sorting, 180-ms search debounce, cross-catalog channel/movie/series search, metadata-aware sorting, bounded viewing history, completion/resume/restart, season selection, appearance controls and credential-free appearance import/export. Player additions include single-instance adjacent-channel reuse, previous-channel control, fit/fill/zoom, video quality overrides, PiP, diagnostics without URLs and a two-attempt app reconnect budget.

Home displays no invented trending/popularity/child-safety labels. Optional metadata is omitted when missing. Test fixture art and media are confined to androidTest assets; they are not in the application APK.

## Checks

- Debug and isolated QA APK builds: passed.
- `testDebugUnitTest`: 24 tests passed, zero failures. Includes the existing provider/library cases plus legacy-format compatibility, bounded/account-isolated history, completion including short clips, clear-history semantics, English/Arabic query matching, metadata sorting, retry termination, and a 50,000-item host query.
- `lintDebug`: zero errors, 23 advisory warnings. No lint baseline was added to hide errors.
- Phone instrumentation: 9 tests passed on API 33 / Pixel 6 emulator, 1080×2400, 420 dpi. Includes previous catalog/EPG/favorites/sign-out flows, saved encrypted-login restoration, search submission, Home/settings persistence, universal search, real local MP4/HLS playback, error/retry, remote focus/Select/Back, unchanged player identity on channel switch, aspect selection and actual PiP lifecycle entry/exit.
- TV-sized instrumentation: 9 tests passed at 1920×1080, 320 dpi (960×540 dp) on the same API-33 phone emulator. Final label-polish recheck also passed all 9 tests.
- APK signature: verified using APK Signature Scheme v2. This is a debug-signed test build.

Tests use application ID `com.iptv.app.qa`; test cleanup cannot clear normal-app credentials. The normal application ID remains `com.iptv.app`, so an APK update preserves its private app storage.

## Measurements

These are observations, not production performance guarantees. No startup or memory improvement is claimed from these single-run measurements:

| Measurement | Observation | Limitation |
|---|---|---|
| Original installed 1.0 cold launch | 752 ms `am start -W` TotalTime | One API-33 emulator observation, local sign-in state |
| Original process PSS | 172,689 KiB | One idle emulator snapshot, not a leak/budget test |
| 50,000-item query | 28.48 ms | Host JVM unit-test run, not Android hardware |
| Updated first launch after installation | 4,895 ms | Includes first-run/install warm-up after display resize; exceeded the 2.5-s target |
| Updated subsequent cold launch | 820 ms | One cold-process sample after first-run warm-up; original observation was 752 ms |
| Updated process PSS | 230,927 KiB | First post-update idle snapshot; higher than the original snapshot, requires release-build profiling |
| APK size | 16.45 → 18.73 MiB | Debug builds; Media3 upgrade and UI/icon dependencies add size |

Search no longer scans favorites for every visible card: membership is prepared as a set. Provider catalog conversion, discovery queries and Home grouping/sorting run on background dispatchers. Three catalog sections are cached for five minutes; explicit refresh is available, and history is capped at 100 unique titles. No video preview streams are opened by navigation or artwork rotation.

## Visual and accessibility review

Phone screenshots were inspected with real open-movie artwork in isolated fixtures and missing-logo fallbacks. Text/card contrast: primary #F9FAFB on #1B2433 = 14.92:1; secondary #B0B9C8 = 7.89:1; accent #B9BCFF = 8.69:1. Buttons use at least 48 dp, screen-reader text labels accompany actions, fields use appropriate keyboard actions, and layout/text follow system scaling. High contrast and font adjustment are available. Optional rotation is off by default and checks the system animation scale; focus pauses rotation.

The apple-design review used accessibility, layout, typography, color, focus and video principles while retaining Android Material interaction conventions. This source/screenshot review is not a complete TalkBack, RTL or largest-font certification.

## Compatibility, risks and remaining phases

- A physical Android TV/tablet, low-memory device, Android 8 runtime and current-OS runtime have not been tested. TV-sized phone-emulator tests are identified as such.
- Existing favorites/positions were checked using their original serialized format and key derivation. No database was introduced and no old account data format was replaced.
- Catalog cache is memory-only. Extremely large persistent catalogs, pagination/FTS, full XMLTV parsing/timeline/scheduling and multiple-provider merges require the planned database migration.
- Profiles, PIN/kids policies, complete English/Arabic localization, EPG/episode universal indexing, voice search, Cast, downloads, licensed DRM, external subtitles/delay adjustment and optional AI are not implemented in this release. There are no nonfunctional controls advertising them.
- Appearance import/export is integrated with Android's document picker but its complete provider/device matrix remains unverified. Physical remote-only use of all dialogs and sustained reconnect under real network loss also remain unverified.
- Frame times, live switch latency, ANRs/crash-free rates, long-duration memory/leak profiling and dependency vulnerability certification have not been measured. Debug builds are not store-ready releases.
- No real provider is bundled or guaranteed compatible. CCcam has no video source in this project and is not advertised as supported.

Prioritized next increment: M3U/URI imports and encrypted multi-provider management, indexed catalog/streaming XMLTV database with migration tests, timeline EPG and scheduled refresh; then profiles/PIN policies/localization and physical-device performance/release checks, as detailed in MODERNIZATION_ROADMAP.md.
