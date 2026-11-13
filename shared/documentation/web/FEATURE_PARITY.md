# Android / iOS / Web parity

Reference: native `Features/*`, `IPTVCore/{Models,Provider,CatalogStore,Storage}.swift`, Android Compose screens and the existing FEATURE_PARITY.md. Native source is preserved apart from the separately requested Rayan TV display-name change. “Implemented” describes source behavior, not universal provider/device certification. Execution evidence is recorded separately in VALIDATION.md.

| Journey | Android | iOS | Web implementation / browser boundary |
|---|---|---|---|
| Host/scheme/port/base path, active/expired login | Native | Native | Server validation, encrypted HttpOnly session; operator-approved origins |
| Restore / logout / account isolation | Secure storage | Keychain | Cookie + account-scoped IndexedDB; logout deletes local account library |
| Home, live, movies, series, favorites, history, search, settings | Native | Native | App Router routes, responsive sidebar / compact navigation |
| Hero, continue, recent live, favorites, recent movies, series, related, recommendations | Real metadata | Real metadata | Real catalogs; no fabricated trending/popularity |
| Search, categories, genre/year/language/rating, sorts, grid/list, paging | SQLite | SQLite | Cached server catalog, 48-item pages and virtualized rows |
| Recent search / voice | Native speech | Native speech | Recent searches; capability-gated browser speech with typed fallback |
| Details, trailer, seasons, watched, exact continuation | Native | Native | Typed provider parsing + local library domain rules |
| Progress / restart / history / completion / autoplay | Native | Native | IndexedDB, independent positions, threshold, queue |
| Channel selection / last channel / opt-in preview / guide / timeline | Native | Native | Selected guide, two-minute refresh; no unrequested stream start |
| HLS / VOD / audio / subtitle / quality / speed / aspect / retry | Media3 | AVPlayer | HTML video + hls.js; real manifest tracks only; bounded network recovery |
| Fullscreen / PiP / control lock / shortcuts / queue | Native | Native | Capability gated web APIs + keyboard controls |
| TS / unsupported codecs / DRM | Platform-dependent | Platform-dependent | Explicit errors; provider must supply browser-compatible HLS/MP4. No transcoding/DRM bypass |
| DASH | No dedicated catalog source | No dedicated catalog source | No DASH source in existing contract; unsupported manifests explained rather than claiming parity |
| Theme/accent/font/contrast/motion/rotation/landing/home rows | Native | Native | Shared tokens and appearance import/export schema, reduced-motion override |
| English / Arabic / RTL | Native | Native | Navigation and primary controls translated, provider metadata unchanged |
| TV remote / background playback | Android TV | OS dependent | Keyboard focus; background playback and PiP depend on browser/OS |
| Cache clearing / diagnostics | Native | Native | Catalog cache refresh, browser storage accounting and safe media diagnostics |

## Implementation phases

1. Analysis: mobile screen/model/provider/library review and this matrix.
2. Integration: pnpm packages; mobile build entrypoints untouched.
3. Foundation: Next server session, validation, bounded catalog cache, media/image relay.
4. UI: cinematic responsive app, all catalog and account routes.
5. Features: persistent library, details/episodes/live/player/settings.
6. Verification: type/lint/unit/component/API/Playwright/build; actual outcomes in VALIDATION.md.
7. Delivery: independent workflow, environment contract, standalone Docker deployment guide.
