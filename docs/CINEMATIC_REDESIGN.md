# Cinematic redesign — audit and execution roadmap

November 8, 2026. Enhancement of the existing Kotlin/Compose Android client.

## Audit before implementation

The app already uses Material 3, Coil, a cancellable Xtream repository, an account-scoped SQLite VOD cache, Keystore credentials, local watchlists/history, and Media3. These remain the foundation. No architecture migration, provider replacement, or bundled catalog is needed.

| Screen | Existing limitation | Proposed hierarchy and behavior |
| --- | --- | --- |
| Shared system | Lavender/cyan palette diverges from the brief; default typography/shapes; focus is a border only | Semantic dark surfaces → purple actions → cyan metadata; Roboto type scale; 4dp spacing; shared motion/focus and feedback components |
| Navigation | Mobile hides Series behind Library; wide mode determined by width only; sign-out crowds every header | Mobile Home / Live TV / Movies / Series / More; Search in header; More opens watchlist/settings/sign-out; TV detected independently with collapsing sidebar; preserve section state |
| Home | Card-like hero, limited metadata; TV changes all posters to landscapes | Wide cinematic artwork with directional scrims, real metadata, primary actions; continue/recent/saved/provider rails; true 2:3 posters on every device |
| Live TV | Generic poster grid leads to another screen; no browsing preview or integrated guide | TV category column → numbered channel list → selected-channel preview/now-next/guide; mobile chips → preview → channels/guide; explicit preview start, one player, no focus-triggered video connections |
| EPG | Now/next only inside details | Real short-EPG timeline with proportional cells, current-time marker, jump to now, program selection and local time labels; honest gaps/empty/error feedback |
| Movies / Series | Working provider discovery, local recommendations and paged browser | Preserve real rails/filters; consistent poster shapes, restrained focus, scalable controls and artwork hierarchy |
| Details | Small fixed poster and title squeeze compact layouts; full-width action stacks on TV | Adaptive immersive backdrop header, compact vertical layout, wider facts and poster composition; wrapping primary actions; retain watchlist/trailer/stream info/related content |
| Episodes | Fixed thumbnail row competes with large text | Adaptive stacked/side-by-side thumbnails, metadata and wrapping actions; retain exact continuation, watched flags and remembered seasons |
| Player | Functional Media3 controls plus unstyled native action buttons; dense header | Gradient chrome, rounded stateful remote buttons and accent timeline; preserve stock Media3 transport/accessibility and all stream/episode/PiP controls |
| Search / settings / login | Functional but inconsistent spacing/focus | Inherit centralized tokens; preserve debounced scoped queries, settings and credential validation |

## Incremental execution and acceptance

1. Design system: exact requested palette, semantic foreground variants for contrast, type/shape/spacing tokens, motion preference and TV context. Build before proceeding.
2. Navigation: direct mobile destinations, accessible overflow and collapsing TV sidebar; retain test tags and state restoration. Verify Search/Watchlist/Settings remain reachable.
3. Home: shared hero, provider details, poster cards and focus restoration. No fake popularity or quality badges.
4. Live TV/EPG: bounded cached single-channel guide requests, cancellable selection, real provider categories, search/sort/favorites, optional lifecycle-scoped preview and fullscreen handoff. Verify channel changes never create simultaneous players.
5. Movies/details/series/episodes: adaptive shared artwork, facts, actions and episode layout. Retain existing metadata filters, paged SQLite browser and persistent library contracts.
6. Player: enhance native chrome without replacing Media3 transport/lifecycle. Verify HLS/MP4/error retry/episodes/aspect/PiP regressions.
7. QA: unit tests for timeline/window math, emulator phone and wide flows, Arabic/RTL and large-text spot checks, lint, APK packaging. Report measured results and remaining gaps separately.

## Boundaries requiring accurate reporting

The existing API exposes a short schedule, not an XMLTV/full-day archive. Date navigation, catch-up, descriptions and multi-day search cannot be fabricated. This project has one account and no profiles; no profile selector will imply isolation that does not exist. Popularity, intro/credits timing and content quality are shown only when reliable data exists. Physical TV, production provider behavior and low-end frame performance require separate device/provider validation.

Design review applies accessibility, hierarchy and restrained motion principles from the local apple-design references (accessibility, layout, typography, color, motion, playing-video). Android Material controls and 48dp targets remain the platform convention. Focus grouping follows https://developer.android.com/develop/ui/compose/touch-input/focus/focus-restoration and native player customization follows https://developer.android.com/media/media3/ui/playerview.

## Execution record

The implementation and verification are delivered as version 1.3.0. See `CINEMATIC_IMPLEMENTATION.md` for each screen's hierarchy, components, interactions, adaptations and changed files, and `CINEMATIC_VALIDATION.md` for actual results and remaining limits. Unsupported archive/profile capabilities remain explicitly identified rather than represented by mock UI.

Design review used the platform-independent foundations of the `apple-design` skill. For example, `accessibility.md › Vision` says “Support larger text sizes.” The Android implementation retains system scaling and adds an Arabic 1.8× layout check. `accessibility.md › Mobility` says “Support simple gestures for common interactions.” Core actions remain buttons, touch targets and D-pad operations. Artwork hierarchy and the directional scrim are product-design judgments, not claims of Android HIG compliance.
