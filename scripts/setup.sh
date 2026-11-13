#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v python3 >/dev/null
if [[ "$(uname)" == Darwin ]]; then xcodebuild -version; fi
"$ROOT/scripts/build-android.sh" --version
swift package --package-path "$ROOT/apps/ios/Core" resolve
