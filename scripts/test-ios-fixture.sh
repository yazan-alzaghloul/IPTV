#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${IOS_DESTINATION:?Set IOS_DESTINATION to platform=iOS Simulator,id=<clean simulator UUID>}"
mkdir -p "$ROOT/.validation"
PORT_FILE="$ROOT/.validation/fixture-port-$$"
LOG_FILE="$ROOT/.validation/fixture-$$.log"
python3 "$ROOT/scripts/fixture-provider.py" --port 0 --port-file "$PORT_FILE" > "$LOG_FILE" 2>&1 &
FIXTURE_PID=$!
cleanup() {
  kill "$FIXTURE_PID" 2>/dev/null || true
  wait "$FIXTURE_PID" 2>/dev/null || true
  rm -f "$PORT_FILE"
}
trap cleanup EXIT
for _ in {1..30}; do
  if ! kill -0 "$FIXTURE_PID" 2>/dev/null; then cat "$LOG_FILE"; exit 1; fi
  if [[ -s "$PORT_FILE" ]]; then break; fi
  sleep 1
done
PORT=$(cat "$PORT_FILE")
curl -fsS "http://127.0.0.1:$PORT/player_api.php?username=fixture&password=fixture" >/dev/null
export TEST_RUNNER_IPTV_FIXTURE_URL="http://127.0.0.1:$PORT/service/"
"$ROOT/scripts/build-ios.sh" test -parallel-testing-enabled NO "$@"
