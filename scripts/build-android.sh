#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]]; then
  export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
cd "$ROOT/apps/android"
if [[ $# -eq 0 ]]; then set -- assembleDebug testDebugUnitTest lintDebug; fi
exec ./gradlew "$@"
