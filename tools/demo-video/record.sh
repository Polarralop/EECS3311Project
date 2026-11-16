#!/usr/bin/env bash
# Records one Stage 2 demo video of the real app under a virtual display.
#
#   tools/demo-video/record.sh V1|V2|V3|V4|prep3|reset [out-dir]
#
# reset copies samples/sample-ashfall.json into a fresh save folder (do it once before V1).
# prep3 plays the V2 story on off camera so V3 has 10+ turns and one event left (not recorded).
# The key is read from ANTHROPIC_API_KEY, or from STORYTELLER_API_KEY when the session strips the
# first name; it is passed only to the app's process and never printed.
set -euo pipefail

VIDEO="$1"
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${2:-$REPO/tools/demo-video/out}"
WORK="${DEMO_WORK:-$REPO/tools/demo-video/work}"
mkdir -p "$OUT" "$WORK"
export STORYTELLER_SAVE_DIR="$WORK/demo-saves"
export ANTHROPIC_API_KEY="${STORYTELLER_API_KEY:-${ANTHROPIC_API_KEY:-}}"

if [[ "$VIDEO" == reset ]]; then
  rm -rf "$STORYTELLER_SAVE_DIR"
  mkdir -p "$STORYTELLER_SAVE_DIR"
  cp "$REPO/samples/sample-ashfall.json" "$STORYTELLER_SAVE_DIR/"
  echo "Save folder reset: $STORYTELLER_SAVE_DIR"
  exit 0
fi

cd "$REPO"
CP_FILE="$WORK/classpath.txt"
[[ -s "$CP_FILE" ]] || mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null
mvn -q compile >/dev/null
mkdir -p tools/demo-video/build
javac -d tools/demo-video/build -cp "target/classes:$(cat "$CP_FILE")" tools/demo-video/src/ca/yorku/storyteller/ui/*.java
CP="tools/demo-video/build:target/classes:$(cat "$CP_FILE")"

if [[ "$VIDEO" == prep3 ]]; then
  java -cp "$CP" ca.yorku.storyteller.ui.DemoDirector prep3 "$OUT/prep3.log"
  exit 0
fi

DISPLAY_NUM=":${DEMO_DISPLAY:-99}"
# The screen is 1536x864 and the recording is scaled up to 1920x1080, so app text reads at 1080p
# (Java on Linux only scales the UI by whole numbers).
Xvfb "$DISPLAY_NUM" -screen 0 1536x864x24 -nolisten tcp >/dev/null 2>&1 &
XVFB=$!
trap 'kill $XVFB 2>/dev/null || true' EXIT
export DISPLAY="$DISPLAY_NUM"
sleep 1.5
# A window manager gives the app's dialogs their title bars (apt-get install openbox).
if command -v openbox >/dev/null; then
  openbox >/dev/null 2>&1 &
  sleep 1
else
  echo "openbox not found: dialogs will have no title bars" >&2
fi

LOG="$OUT/$VIDEO-timeline.tsv"
: > "$LOG"
export RECORD_START_MS="$(date +%s%3N)"
ffmpeg -hide_banner -loglevel error -y -f x11grab -draw_mouse 1 -framerate 30 -video_size 1536x864 \
  -i "$DISPLAY_NUM" -vf "scale=1920:1080:flags=lanczos" -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -movflags +faststart \
  "$OUT/$VIDEO.mp4" < /dev/null &
FFMPEG=$!
sleep 0.5

STATUS=0
if [[ "$VIDEO" == V4 ]]; then
  python3 "$REPO/tools/demo-video/cli_driver.py" "$CP" "$LOG" || STATUS=$?
else
  java -cp "$CP" ca.yorku.storyteller.ui.DemoDirector "$VIDEO" "$LOG" || STATUS=$?
fi

kill -INT "$FFMPEG"
wait "$FFMPEG" || true
echo "$VIDEO finished with status $STATUS: $OUT/$VIDEO.mp4"
exit "$STATUS"
