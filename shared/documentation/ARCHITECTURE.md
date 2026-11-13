# Architecture decisions

## ADR 001: two native applications with shared contracts

Android's Gradle root lives in `apps/android`, including its wrapper and existing application/QA variants. Kotlin source was moved without behavior edits. iOS has a separately buildable Xcode project and a local Swift package. Neither build invokes the other platform. Relative Android asset paths continue to resolve from its unchanged module root.

Shared API contracts, design tokens and documentation express business agreements. No runtime JavaScript bridge or Kotlin multiplatform migration was introduced: preserving the working Android client while implementing SwiftUI/AVKit is the requested scope. Native business rules are implemented independently and covered by corresponding tests. The shared JSON is a documented specification, not a claim that Android loads a remote theme at runtime.

## ADR 002: iOS layering and injection

- **Domain** (`Core/Sources/IPTVCore/Models.swift`): typed content/session/library models, safe input normalization, metadata validation, retry budget, continuation and local recommendations. No SwiftUI/AVKit dependencies.
- **Data** (`Provider.swift`, `CatalogStore.swift`, `Storage.swift`): `ProviderRepository` and `CredentialStore` interfaces, injectable URLSession transport, actor-isolated Xtream/SQLite/library stores, device-only Keychain adapter. Errors returned to UI never contain request URLs or provider response bodies.
- **Presentation** (`App/AppModel.swift`, `Features/*`): main actor observable MVVM session/catalog/library state plus focused screen state; `PlayerModel` owns playback resources. The app entry point composes production dependencies; tests inject a provider and vault. Native SwiftUI navigation stacks preserve detail/back behavior.
- **View services**: artwork downsampling/cache actor, contextual speech recognizer, network diagnostics. These do not enter business rules.

The SQLite system module is linked by SPM; no third-party dependency or remote package needs pinning. A schema version migration is required before incompatible schema changes. Current catalog schema is additive and disposable; the account library uses Codable snapshots with optional-field migration defaults.

## ADR 003: state and concurrency

Provider requests use structured async/await. Catalog loads bound simultaneous connections to a catalog and its categories for one content kind at a time, preventing catalog-wide EPG fan-out. Optional category failures do not discard a working catalog. Catalog refresh has a five-minute memory TTL. EPG cache is bounded to 24 selected channels and two minutes; detail cache to 12 titles. Library saves are chained to preserve order; sign-out cancels and joins catalog work and awaits saves before deleting data. Session generation checks prevent stale responses entering a new account.

SQLite transactions atomically replace a kind's catalog/categories, use bound parameters and literal escaped search terms, and expose stable 48-item pages, facets and lookup. Catalog JSON is stored without account credentials. The browser supports literal multiword title/genre search, category/year/language/genre and real provider rating filters. Universal search pages each kind independently, displaying up to 48 matches per kind per page so no kind's matches are silently dropped.

Home rails use bounded slices from disk. Library history is capped at 100, recent searches at ten. Separate resume positions survive history eviction. Watched markers and selected seasons are independent of history. Series continuation matches exact parent/episode identifiers, follows season/episode order and skips watched episodes after completion.

## ADR 004: secure persistence and networking

The Keychain entry uses `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. No login is written to UserDefaults, catalogs, appearance exports, telemetry or application logs. API URLSession configuration is ephemeral with cookies/cache disabled, standard TLS validation, 30-second request and 60-second resource limits. Xtream necessarily puts authentication in API query and stream path segments; each segment is encoded separately, and literal query plus signs use `%2B` for PHP form decoding. The app supports provider HTTP with an explicit in-flow notice and ATS exception. Store submission must justify this exception for user-selected IPTV servers.

Library snapshots use atomic writes and iOS file protection. App Support directories exclude backup. SQLite uses WAL and file protection. On sign-out, catalog records and account snapshots are deleted and artwork responses cleared. A third-party provider may itself log requests; iOS framework/system logs are outside app-controlled logging.

## ADR 005: playback ownership

The opt-in preview owns one AVPlayer and releases it on background, navigation, full screen handoff or disappearance. Fullscreen has its own stable StateObject player model and one AVPlayerViewController. AVKit provides native seeking, audio/subtitle selection and PiP. The model adds durable checkpoints, bounded 1/2-second network retries, manual retries, stream-format request switching, fit/fill/stretch, real HLS variant caps, lock/unlock, diagnostics without URLs, channel return/switching and episode queue/autoplay. All observers and retry/asset/activation tasks are released on close. A serial audio-session worker keeps blocking activation/deactivation off the main actor. Owner identifiers prevent a prior preview or voice search from deactivating a newer player. Pending speech permission tasks are cancelled and fenced when leaving search. Background playback pauses unless PiP is active.

AVPlayer quality caps do not guarantee an exact rendition; the player may choose a lower playable rendition. A raw TS endpoint is a request alternative, not a transcoder. Unsupported codec/container or DRM errors are surfaced with recovery actions. Media3/AVPlayer equivalence does not imply identical decoder coverage.
