# IPTV for Android — Cinematic Premium 1.3

The existing Kotlin/Compose IPTV app now has a cinematic purple/cyan design system, direct mobile Home / Live TV / Movies / Series navigation, an adaptive TV sidebar, artwork-led discovery and consistent poster cards. Live TV adds category/channel/guide panels, favorites, an opt-in embedded preview and a real short-EPG timeline. Details and episodes adapt to narrow screens and larger text. Media3 retains its playback engine and gains styled controls, screen lock and stream-aware quality choices.

English and Arabic resources cover the main interface and common feedback. Provider data, encrypted credentials, account-scoped watchlists/history, resume positions, SQLite VOD paging, episode continuation, audio/subtitles, aspect, PiP and retry remain integrated. No production catalog or provider account is bundled.

- [Codebase audit and roadmap](docs/CINEMATIC_REDESIGN.md)
- [Screen-by-screen implementation, components and limits](docs/CINEMATIC_IMPLEMENTATION.md)
- [Verification, screenshots and release artifacts](docs/CINEMATIC_VALIDATION.md)

Historical 1.0–1.2 reports remain in `docs/`. The API supplies a short schedule rather than a full-day XMLTV archive; the new guide shows that actual data. Profiles/multiple accounts, M3U/XMLTV imports, casting, downloads and recording remain separate work. Popularity, catch-up, quality badges and intro/credits skipping are not fabricated without trustworthy metadata.

A native Kotlin / Jetpack Compose Xtream IPTV client for Android phones, tablets, and Android TV (Android 8.0 / API 26 or later). A provider subscription is required. This app does not include content or run a CCcam server.

## Install

Download `dist/IPTV-debug.apk` to your phone or TV and open it. Allow installation from that file manager when Android prompts. This APK is signed with the development machine's debug key and is intended for testing, not store distribution.

With Android platform tools:

```sh
adb install -r dist/IPTV-debug.apk
adb shell am start -n com.iptv.app/.MainActivity
```

Open **IPTV** in the launcher. Enter your provider host or base URL, optional port, username, and password, then choose **Connect**. A full URL's scheme takes precedence over the HTTP/HTTPS selector; an entered port overrides the URL port. Base paths such as `https://provider.example/service/` are preserved. Use a base URL rather than an M3U link or `player_api.php` link.

Browse Live TV, Movies, Series, or Favorites. Open a title for details and playback. Series show playable episodes sorted by season and episode. Live channel details show current and upcoming programs when timestamped EPG is available. The player settings menu selects available audio and subtitle tracks; seeking is available for on-demand content. Live stream failures offer an HLS / MPEG-TS switch for providers that only support one output format.

Use touch on phones or directional keys and Select on a TV remote. Back exits playback to details, then returns to the catalog. Signing out removes the saved account, favorites, playback positions, history, searches, season choices and account catalog/episode caches.

## Build and test

Open this folder in Android Studio, or install JDK 17 or 21 and Android SDK Platform 36. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir=/path/to/android/sdk`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
# With an Android emulator or device attached:
./gradlew connectedQaAndroidTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The QA build uses application ID `com.iptv.app.qa` so tests do not clear the normal app account. Tests use an in-process fixture server and official Media3 sample clips; they do not require provider credentials. See `docs/CINEMATIC_VALIDATION.md` for checks completed on this build.

## Structure

- `Provider.kt`: provider interface, Xtream API client, account validation, catalog/detail/EPG parsing, and encoded playback URLs.
- `Storage.kt`: credential and library interfaces, Android Keystore AES-GCM storage, account-scoped favorites and resume positions.
- `AppViewModel.kt`: cancellable catalog/detail loading and session state.
- `DesignSystem.kt`, `CinemaIcons.kt`, `UiLabels.kt`: semantic colors, typography, shapes, motion, native vectors and localized presentation labels.
- `MainActivity.kt`, `PremiumUi.kt`: edge-to-edge login, adaptive shell, cinematic home, shared hero/posters and focus restoration.
- `LiveTvScreen.kt`, `GuideLayout.kt`: channel workspace, lifecycle-scoped preview and short-EPG timeline.
- `VodCatalogStore.kt`: account-scoped SQLite catalog pages, filters and opaque episode queues.
- `VodModels.kt`: validated optional metadata, exact series continuation and local recommendation interface.
- `VodExperience.kt` / `VodDetailsScreen.kt`: dedicated discovery, browsing, details, seasons and episode components.
- `PlayerActivity.kt`: Media3 playback, media session, lifecycle handling, retry, and periodic resume checkpoints.

## Privacy and compatibility

Credentials are encrypted with a non-exportable Android Keystore key. Backup and device transfer are disabled for app data. Provider requests and stream URLs contain the provider's required credentials; the app does not log these URLs. Media3 logging is disabled. Favorites and playback positions are private local preferences scoped by a SHA-256 account identifier. No analytics or custom backend is used.

HTTP providers are supported, with an in-app notice that HTTP is unencrypted. HTTPS is recommended and certificate validation remains enabled. Artwork is fetched from provider-supplied URLs. Android device codec capabilities determine which streams can play.

This release supports one account, English/Arabic core UI, live TV with optional preview, movies, series, local favorites/history, on-demand resume, and a selected-channel short-EPG timeline. It excludes recording, downloads, casting, catch-up, multi-channel full-day EPG grids, and Google Play publishing. Provider-specific API differences and DRM-protected streams require separate compatibility work.
