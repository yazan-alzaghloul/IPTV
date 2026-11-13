# Feature parity and release checklist

Assessment: Android commit `62ad776`; iOS implementation in this migration. **Implemented** means a real production code path exists, not that every device/provider scenario was executed. **Core** refers to the passing Swift domain/URLSession/SQLite suite. Simulator outcome details and unverified cases are recorded in MIGRATION_RESULTS.md. Fixtures are injected by tests or supplied over a local HTTP server; production never swaps in fixture screens or a mock account.

| Existing Android behavior | Native iOS implementation | Automated evidence / remaining validation |
|---|---|---|
| Provider host/scheme/base path/port | Credentials.normalize + native form | Core normalization, invalid paths/ports, safe query/path encoding |
| Password visibility and secure entry | SecureField / reveal action | Phone/iPad login checks passed; physical-device review remains |
| Active/denied/expired account validation | URLSession XtreamRepository | Core active/auth/status/expiry/HTTP/malformed/network cases |
| Saved login restoration | Keychain device-only vault + AppModel.restore | Native app/session tests; physical Keychain accessibility remains device QA |
| Sign-out removes account data | Cancel/join loads/saves; clear cache/library/vault/artwork | AppModel lifecycle test; real UI cleanup flow |
| Home / Live / Movies / Series | SwiftUI tabs, navigation stack, iPad sidebar | Phone/iPad fixture suites and selected screenshot review passed |
| Favorites / Search / Settings | More on phone; sidebar on iPad | Native routes; fixture navigation coverage |
| Artwork hero, prior/next title | FeaturedHero + bounded detail loading | Source + screenshot review |
| Optional rotation | 12-second task, disabled by Reduce Motion | Source; reduced-motion device/visual check |
| Continue watching / recent live / favorites rails | Account library + parent-series mapping | Core history/resume rules; app UI check |
| Movie/series/genre discovery rails | SQLite slices, real provider metadata | Core paging/facets; visual fixture review |
| Local recommendations | Genre/category overlap, exclude known titles | Core requires real history/favorite signals |
| Discover/browse navigation | Native discover view + browser navigation link | Native routes; fixture UI check |
| Category/title/genre/year/language/rating filters | Bound SQLite queries, facets, literal search | Core escaped %, _, account and page isolation |
| Provider/title/added/release/rating sort | CatalogSort to fixed SQL order | Core pages; additional provider data manual QA |
| 48-item pages, grid/list | SQLite pages + SwiftUI adaptive grid/list | Core >100 item page test; fixture list UI |
| Per-screen browser state | Session-scoped filter/page/list/kind maps + navigation state | Source; back/navigation visual QA |
| Universal search by kind | Kind filters and per-kind pages; up to 48 matches/kind/page | Core/API and browser code; UI fixture |
| Recent searches | Ten entries, case-insensitive deduplication | Core bounds/deduplication |
| Voice search | Speech + AVAudioEngine, contextual permission and error fallback | Compiles; actual mic/permission/device validation pending |
| Details, metadata, images | DetailsView + ContentDetails | Core numeric/null/missing field and series parsing; fixture movie plot |
| Safe trailers | Validated HTTP(S)/YouTube Link | Core unsafe scheme/embedded credential rejection |
| Related titles | Same-kind category SQLite page, excludes selected | Source + cache tests |
| Favorites / watchlist / long-press | Native context menus, account snapshot | Core toggle/isolation persistence tests |
| Watched/unwatched movie/episode/season | Independent watched keys + bulk mark | Core watched reset + continuation tests |
| Season order and remembered choice | Sorted episode models + saved season map | Core ordered parsing; native selection route |
| Exact series continuation | Match parent/id; resume or next unwatched | Core continuation scenarios |
| Resume / restart | Stored positions + AVPlayer seek when ready | Core completion/live/no-resume/history eviction; player UI playback check |
| Previous/next episode and browser | In-memory opaque queue (no URLs in navigation) | PlayerModel + native queue sheet; device stream check |
| Optional next episode autoplay | Completion observer + saved autoplay preference | Source; fixture short media/device validation |
| Live categories, channel favorites | Native list; side-by-side guide on regular width | Provider/category tests + live route |
| Opt-in embedded preview | AVPlayer only after Start Preview; release before full screen | Phone/iPad real HLS readiness passed; preview ownership is released on handoff |
| Short guide / now / next | Timestamp validation, Base64/plain title decoding | Core EPG intervals/decoding; live fixture |
| EPG timeline/current marker | Time-positioned program blocks and current-time rule | Source; time gaps/long labels manual review |
| Selected-channel EPG refresh/cache | 2-minute selected guide task, 24-entry cache | Source; cancellation/session-generation checks |
| HLS live and compatible MP4 VOD | Native AVPlayer/AVKit | Simulator sample playback plus physical/provider codec checks |
| MPEG-TS fallback request | Native button requests .ts instead of .m3u8 | URL construction tested; actual decoder support differs on iOS |
| Audio/subtitle tracks, seeking, pause | AVPlayerViewController native transport and media selection | Apple platform control; multitrack physical/provider checks pending |
| Fit/fill/stretch | Native AVLayerVideoGravity | Source + build; visual check |
| Real stream video quality | Enumerate AVAsset variants, upper bitrate/resolution caps | Source + compile; multivariant HLS/provider validation pending |
| Bounded reconnect/manual retry | Two transient network retries (1s/2s), explicit reset | Core retry budget; player error observer code |
| Playback diagnostics | Dimensions/observed bitrate/container, excludes URLs | Native diagnostics; fixture playback check |
| Lock controls | Suppress controls/interactions with explicit unlock | Native player code; interaction QA |
| Picture in Picture / background | Native AVKit delegate + audio mode; pause outside PiP | Build only until physical PiP test; not claimed certified |
| Live next/prior/previous channel | Same player with selected queue + prior channel | PlayerModel code; multi-channel provider check |
| Completion threshold | 90/95/98/100 UI; clamp to 80–100 domain | Core completion behavior |
| 100-entry history / individual remove / clear | Snapshot + independent resume map | Core persistence/history/eviction; native history route |
| Theme/accent/high contrast/font scale | Semantic colors, system text ladder, high-contrast secondary ink | Dark and Arabic large-text captures reviewed; light-appearance visual QA remains |
| Landing section and hidden Home rows | Same export keys and six rows | Core appearance validation + actual bindings |
| Export/import/reset appearance | Native file exporter/importer, 32KB bound, shared schema | Core valid/invalid/oversized JSON; physical file-provider check |
| Artwork cache removal | URLCache actor with cancellation/generation fence | Native implementation; storage/device QA |
| Diagnostics | Version, OS, NWPathMonitor, physical memory | Native framework code + build |
| English / Arabic / RTL | Localizable.strings + native layout direction | Phone/iPad Arabic large-text login passed; complete linguistic review remains |
| Android TV remote and sidebar | Android retained; native iPad sidebar/keyboard on iOS | Android byte-preservation + build; iOS does not target tvOS |

## Platform differences that remain real

AVPlayer does not reproduce Media3's entire format/codec/DRM matrix. A working Android stream can need HLS/MP4 conversion by the provider on iOS. No transcoder, FairPlay/license configuration, raw-TS compatibility guarantee or alternate decoding engine is claimed. Native quality choices cap playback rather than forcing an exact track. Voice recognition and PiP depend on permission, availability and device support. iOS typography is mapped to Dynamic Type to preserve accessibility rather than matching Android pixel measurements.

## Release acceptance gates

- [x] Native monorepo layout and independent projects/scripts.
- [x] Existing Android production source preserved, moved Gradle build/JVM tests/lint executed successfully.
- [x] Native Swift/SwiftUI client with real API, storage and playback paths.
- [x] Shared API, appearance schema and design specifications.
- [x] Independent automated build/test workflows and developer documentation.
- [ ] Full physical iPhone/iPad/provider validation across the rows above.
- [ ] Production signing, App Store/Play review and release distribution configuration supplied by owner.
- [ ] Broad production stream/codec parity certification. The native engine limitation above remains applicable.

A checked source implementation is not a substitute for an unexecuted platform test. The final validation report explicitly records which checks ran.
