# Build, CI and signing

## Independent pipelines

`android.yml` triggers on Android, Android build script, API contracts and its own workflow. It sets JDK 17, installs SDK 36, resolves the dependency graph, builds debug/QA/instrumentation APKs, runs JVM tests and lint, and uploads APKs/reports. A separate emulator job uses API 33 and runs `scripts/build-android.sh connectedQaAndroidTest`; it uploads instrumentation reports independently. The same command runs locally on a configured emulator.

`ios.yml` triggers on iOS, iOS scripts, the fixture provider, shared files and its own workflow. It resolves the local package, runs domain/URLSession/SQLite tests on the macOS host, chooses an installed iPhone simulator, runs native app/UI tests with the fixture server, runs Xcode analysis, and uploads xcresult bundles. Simulator test flows require a fresh account state. The local server binds loopback, uses only fixture credentials, suppresses URL logs and reuses licensed Android Media3 artwork/MP4 and a small H.264 HLS derivative under shared/test-fixtures; none are production app resources.

`contracts.yml` parses shared JSON and verifies key design-token/source agreement. Shared OpenAPI is a documented observed contract; a provider's deployment can differ. No generated fake backend is used in production.

## Dependencies and quality

Android direct dependency versions remain unchanged. Its Gradle wrapper is checked in; dependency resolution, compilation, JVM tests and Android lint form the current gate. iOS has zero remote Swift packages and links only Apple SDKs plus SQLite. `swift package resolve` validates the local package graph. Xcode analysis and compiler diagnostics run separately from tests. Swift formatting is reproducible with `xcrun swift-format format --in-place --recursive apps/ios/App apps/ios/Features apps/ios/Core/Sources/IPTVCore apps/ios/Core/Tests apps/ios/AppTests apps/ios/UITests` (available with current Xcode).

GitHub workflow YAML has been added locally. No remote workflow run is considered validated without its result. Action references are major versions; organizational supply-chain policy can pin reviewed commit SHAs before public distribution.

## Signing and release

Local scripts use ad hoc signing for the simulator so the real Keychain has app identity entitlements; no developer certificate is required. Use Xcode team selection and unique bundle identifiers for device development. A release archive requires your Apple account, app record, distribution certificate/profile and export options. Store these in protected GitHub environments or an approved signing service, never source files. Add a separate manually approved distribution job only when those identifiers and release destinations are defined.

Android debug/QA builds remain debug signed. Supply release keystore/passwords via a protected CI secret store when a distribution channel is chosen; no production signing configuration was invented. Ignore patterns exclude keystores, certificates and profiles. `.validation/`, Gradle caches, Swift caches and Xcode user settings are ignored.

Neither app is automatically published or uploaded to a store. Review ATS/HTTP justification, privacy disclosures, provider/content rights and physical-device playback before distribution.

The checked workflow versions follow current maintainer usage examples: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), [setup-android](https://github.com/android-actions/setup-android), [setup-gradle](https://github.com/gradle/actions/blob/main/setup-gradle/README.md), [upload-artifact](https://github.com/actions/upload-artifact), and [Android emulator runner](https://github.com/ReactiveCircus/android-emulator-runner). iOS artifacts explicitly include the named `.validation/*.xcresult` paths; no signing secret directory is uploaded.
