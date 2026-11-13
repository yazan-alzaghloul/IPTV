# IPTV — native Android and iOS

A monorepo containing a Kotlin/Compose Android app and a Swift/SwiftUI iPhone/iPad app for an IPTV provider's Xtream account. Both use the same provider API, cinematic purple/cyan identity, and local viewing library. No subscription, content, production credentials, analytics, or custom backend is bundled.

## Projects

```text
apps/android/       Android Studio / Gradle project (Android 8.0+, Android TV)
apps/ios/           IPTV.xcodeproj, native SwiftUI app (iOS/iPadOS 17+)
  App/              composition, session view model, navigation, design system
  Features/         login, discovery, browser, live/EPG, details, player, settings
  Core/             local Swift package: domain, URLSession API, SQLite, Keychain
  AppTests/         app/session lifecycle tests
  UITests/          native UI and local fixture integration tests
shared/
  api-contracts/    observed Xtream contract and appearance export schema
  design-tokens/    semantic color, spacing, motion and layout specifications
  test-fixtures/    licensed H.264 HLS sample for native playback validation
  documentation/   assessment, architecture, parity, validation and legacy reports
scripts/            independent builds, setup, shared checks and local fixture server
.github/workflows/  independent Android, iOS and shared-contract checks
dist/               historical debug APKs and source archives from before migration
```

The existing Android source moved intact from the root into `apps/android`. Open that directory in Android Studio. Old source archives and old validation screenshots in `dist/` and `shared/documentation/android-history/` describe pre-migration versions; they are not current source or current validation results.

## Setup

Android requires JDK 17 or 21, Android SDK Platform 36 and accepted SDK licenses. `scripts/build-android.sh` detects Android Studio's bundled JDK on macOS when `JAVA_HOME` is unset. Set `ANDROID_HOME` to your SDK (defaults to `~/Library/Android/sdk` on macOS), or create ignored `apps/android/local.properties` with `sdk.dir=/absolute/path/to/sdk`.

iOS requires macOS, full Xcode 15+ with the iOS 17+ SDK, command-line tools selected for that Xcode, and an installed iOS Simulator runtime. The project was built locally using Xcode 27.2 / Swift 6.4.2 in Swift 5 language mode. It has no external Swift package dependencies; the local `IPTVCore` package uses Apple's SDKs and system SQLite. Python 3 is used for repository utilities. `scripts/setup.sh` reports tool versions and resolves the package.

## Android build and run

```sh
scripts/build-android.sh
# Default: assembleDebug testDebugUnitTest lintDebug
scripts/build-android.sh assembleQa assembleQaAndroidTest
# With a connected emulator/device:
scripts/build-android.sh connectedQaAndroidTest
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

QA uses `com.iptv.app.qa` to isolate fixture data from the normal app. Debug APKs use a development signing key and are not a store release. The original release notes are in [Android documentation](shared/documentation/android-history/CINEMATIC_VALIDATION.md).

## iOS build and run

Open `apps/ios/IPTV.xcodeproj`, select the shared **IPTV** scheme, choose an iPhone or iPad simulator and Run. Real device installation requires selecting your own development team and unique bundle identifier in Xcode. No provisioning profiles or signing secrets are committed.

```sh
scripts/build-ios.sh                 # ad hoc signed simulator build
scripts/build-ios.sh core-test       # domain, HTTP and persistence tests on macOS
scripts/build-ios.sh analyze         # Xcode static analysis
xcrun simctl list devices available
IOS_DESTINATION='platform=iOS Simulator,id=SIMULATOR_UUID' scripts/build-ios.sh test
# Full local HTTP fixture flow, on a clean simulator:
IOS_DESTINATION='platform=iOS Simulator,id=SIMULATOR_UUID' scripts/test-ios-fixture.sh
```

Local products and result bundles are ignored under `.validation/`. Simulator builds select the host architecture consistently for the app and local package. For signing/archive use Xcode's Product → Archive with your distribution settings; the build script uses ad hoc simulator signing and disables signing for generic device compile checks.

The Xcode project is checked in and needs no generator to build. After adding/removing Swift files, `python3 scripts/generate-ios-project.py` refreshes the project using Python's standard library. `python3 scripts/sync-ios-localization.py` reuses matching Android English/Arabic translations plus iOS-specific strings. `python3 scripts/check-contracts.py` checks shared JSON/token agreement.

## Using either application

Enter a provider base URL (including any base path), optional port, username and password. Use the base URL rather than `player_api.php` or an M3U link. A scheme explicitly entered in the URL overrides the protocol selector; an explicit port field overrides the URL's port. An active provider subscription is required.

Browse Home, Live TV, Movies and Series. On phones, More contains Favorites, Search, History and Settings. On iPad, the sidebar exposes all sections. Open details for seasons, episodes, metadata, watchlist actions, restart/resume and related titles. Long-press a poster for favorite/watched actions. Live previews start only when requested and stop before fullscreen playback. Native iOS player controls provide seeking, audio, subtitles and PiP where the media/device supports them. Quality choices appear only when the stream exposes actual variants; iOS choices are upper bitrate/resolution limits.

Settings include theme, accent, text size, contrast, Home rows and landing section, optional artwork rotation, autoplay, completion threshold, appearance JSON import/export, artwork cache removal, history clearing and account sign-out. Appearance exports are compatible between platforms and exclude credentials. English/Arabic core UI follows the device language; iOS speech recognition asks for microphone and speech permissions only when Voice Search is used.

## Compatibility and privacy

Android credentials are protected by Android Keystore; iOS credentials use a device-only Keychain item. Local libraries and caches are scoped to a hash of the account. iOS app support data is excluded from backup and protected until first unlock; provider API sessions are ephemeral and app code never logs credential-bearing URLs. Signing out removes account/library/catalog data and the iOS artwork cache. HTTP remains supported because some providers require it; the login screen explains that HTTP is unencrypted. TLS certificate validation remains enabled.

AVPlayer and Media3 have different format/codec support. iOS should use provider HLS or compatible MP4 streams. Raw MPEG-TS, DASH, MKV, unsupported codecs, provider authentication quirks, and DRM may need a provider alternative or further integration. The app does not transcode media or provide a DRM license system. Hardware PiP, voice recognition, production provider playback and App Store distribution require device/provider validation; see the [feature parity matrix](shared/documentation/FEATURE_PARITY.md) and [executed results](shared/documentation/MIGRATION_RESULTS.md).

Recording, downloads, casting, catch-up, M3U/XMLTV imports, multi-account profiles and full-day multi-channel EPG were not implemented in the original app and are outside this migration.

## Architecture and automation

Read [the assessment and plan](shared/documentation/MIGRATION_PLAN.md), [architecture decisions](shared/documentation/ARCHITECTURE.md), [design specification](shared/documentation/DESIGN.md), and [CI/signing guide](shared/documentation/CI.md). GitHub workflows use platform paths so Android-only changes do not build iOS and iOS-only changes do not build Android. Shared API/design changes validate both clients; shared documentation changes run lightweight shared checks. CI configuration is supplied; remote runs are not claimed as passed without a GitHub result.
