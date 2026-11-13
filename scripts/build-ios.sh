#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ACTION="${1:-build}"
if [[ $# -gt 0 ]]; then shift; fi
if [[ "$ACTION" == core-test ]]; then
  exec swift test --package-path "$ROOT/apps/ios/Core" "$@"
fi
DESTINATION="${IOS_DESTINATION:-generic/platform=iOS Simulator}"
ARCH_ARGS=()
SIGN_ARGS=("CODE_SIGNING_ALLOWED=NO")
if [[ "$DESTINATION" == *Simulator* ]]; then
  ARCH_ARGS=("ARCHS=$(uname -m)" "ONLY_ACTIVE_ARCH=YES")
  SIGN_ARGS=("CODE_SIGNING_ALLOWED=YES" "CODE_SIGN_IDENTITY=-")
fi
exec xcodebuild -project "$ROOT/apps/ios/IPTV.xcodeproj" -scheme IPTV \
  -destination "$DESTINATION" \
  -derivedDataPath "$ROOT/.validation/ios" "${SIGN_ARGS[@]}" "${ARCH_ARGS[@]}" "$ACTION" "$@"
