# H.264 HLS fixture

`init.mp4`, `segment-0.m4s`, and `index.m3u8` are a video-only H.264/fMP4 HLS derivative of `apps/android/app/src/androidTest/assets/sample.mp4`, generated using Apple's AVFoundation by `scripts/generate-hls-fixture.swift`. These files are used only by the local test HTTP server. They are not bundled in either production application.

Source: https://github.com/androidx/media/blob/release/libraries/test_data/src/test/assets/media/mp4/sample.mp4 (AndroidX Media3 repository, Apache License 2.0). Big Buck Bunny is © Blender Foundation, https://www.bigbuckbunny.org/, licensed under Creative Commons Attribution 3.0, https://creativecommons.org/licenses/by/3.0/.

The Android `sample.ts` fixture contains MPEG-2 video; it is preserved unchanged for Android compatibility coverage. It is deliberately not the iOS HLS success fixture. A raw MPEG-2 provider stream remains unsupported by the native iOS engine.

To regenerate on macOS with Xcode:

```sh
mkdir -p .validation/tools
xcrun swiftc -parse-as-library -swift-version 5 scripts/generate-hls-fixture.swift -o .validation/tools/generate-hls-fixture
.validation/tools/generate-hls-fixture apps/android/app/src/androidTest/assets/sample.mp4 shared/test-fixtures/hls
```

The generator uses AVFoundation APIs available to older supported Xcode/macOS versions. Newer SDKs may emit deprecation diagnostics for this offline utility. The applications do not use this encoder or transcode provider streams.
