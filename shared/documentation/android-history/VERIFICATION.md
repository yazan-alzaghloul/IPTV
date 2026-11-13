# Version 1.0 verification — November 8, 2026

## Automated checks

- `assembleDebug`: passed; debug APK generated.
- `testDebugUnitTest`: 15 tests passed (11 provider/input tests, 4 local-library tests).
- `lintDebug`: passed with 0 errors. Advisory warnings remain for dependency updates, target SDK currency, English native-player strings, preference KTX style, and the vector TV banner size.
- `connectedDebugAndroidTest`: all 5 integration tests passed on Android 13 / API 33 in both a 1080×2400 phone configuration and 1920×1080 wide configurations at 160 and 320 dpi (including a 960×540-dp TV layout). The final smaller-layout run used direct instrumentation with all five tests passing.

Tests cover URL/port validation and base-path preservation; encoded credentials; active/invalid/expired accounts; catalog, category, movie, and series parsing; episode sorting; missing artwork and EPG; unavailable providers; malformed/error responses; account-scoped favorites and resume positions; completion reset; and account cleanup.

On-device tests exercise login validation, saved-login encryption and restoration, favorites, catalog search, series episode presentation, now/next EPG, remote focus and D-pad-center selection, keyboard search submission, catalog retry, real Media3 playback from local MP4 and HLS fixtures, pause/seek controls, media keys, stream failure/retry, Back navigation, and sign-out cleanup.

## Visual review

Phone and wide-screen screenshots were inspected for hierarchy, readable text, missing-artwork fallbacks, and control layout. The interface uses Material controls, system-scaled text, 48-dp action targets, scrollable forms/details, adaptive catalog columns, and visible focus outlines. Palette contrast on the card surface is 13.93:1 for primary text, 8.62:1 for secondary text, and 8.84:1 for accent text.

Design principles were checked using the apple-design skill, translated to Android Material conventions: accessibility and scalable text (`accessibility.md` / `typography.md`), predictable layout (`layout.md`), readable colors (`color.md`), explicit focus (`focus-and-selection.md`), and familiar playback controls (`playing-video.md`). This is an Android app; Apple navigation conventions were not applied.

## Limits

No real provider credentials were supplied, so provider compatibility is verified with representative fixture responses only. Hardware Android TV behavior, Android 8 and newer-version runtime behavior, device-specific codecs, DRM streams, track switching with multilingual/subtitle content, and sustained playback through an actual network disconnect remain unverified. Wide-screen/D-pad tests used a resized phone emulator rather than a TV system image.

The provided APK is for sideload testing. It is signed with a debug key; release signing and Google Play publication are excluded.
