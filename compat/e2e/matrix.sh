#!/usr/bin/env bash
# Builds any missing Spigot jars, then runs the bot test on every entry in matrix.txt.
#
#   matrix.sh [plugin.jar] [parallel-jobs]
#
# Defaults: the jar in build/libs and 4 parallel servers. Prints one line per version and
# exits with 1 if any version fails. Logs: compat/e2e/runs/.
set -uo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/lib.sh"
[ -d "$DIR/node_modules" ] || docker run --rm -v "$DIR:$DIR" -w "$DIR" node:22-slim npm ci --silent
PLUGIN=${1:-$(ls "$DIR"/../../build/libs/ActionHealth-*-all.jar | head -n 1)}
PLUGIN=$(cd "$(dirname "$PLUGIN")" && pwd)/$(basename "$PLUGIN")
JOBS=${2:-4}
export SPIGOT_DIR=${SPIGOT_DIR:-$DIR/../.cache/spigot}
mkdir -p "$SPIGOT_DIR"
fetch_via

entries=$(grep -vE '^\s*(#|$)' "$DIR/matrix.txt")

echo "Building missing Spigot jars..."
echo "$entries" | awk '{print $1, $2}' | xargs -P 3 -L 1 "$DIR/build-spigot.sh"

run_one() {
  mc=$1; java=$2; client=${3:-$1}; via=${4:-}
  log="$DIR/runs/$mc-$(basename "$PLUGIN" .jar).log"
  if [ ! -f "$SPIGOT_DIR/spigot-$mc.jar" ]; then
    echo "FAIL  $mc (no Spigot jar)"; return
  fi
  if [ "$via" = via ]; then
    "$DIR/run.sh" "$SPIGOT_DIR/spigot-$mc.jar" "$mc" "$java" "$PLUGIN" "$client" \
      "$VIA_DIR/ViaVersion-$VIA.jar" "$VIA_DIR/ViaBackwards-$VIA.jar" > "$log" 2>&1
  else
    "$DIR/run.sh" "$SPIGOT_DIR/spigot-$mc.jar" "$mc" "$java" "$PLUGIN" "$client" > "$log" 2>&1
  fi
  status=$?
  checks=$(grep -oE '^(PASS|FAIL) [a-z]+' "$log" | tr '\n' ' ')
  note=""
  [ "$client" != "$mc" ] && note=", client $client${via:+ via ViaBackwards}"
  if [ $status -eq 0 ]; then echo "PASS  $mc (Java $java$note) $checks"
  else echo "FAIL  $mc (Java $java) $checks see $log"; fi
}
export -f run_one
export DIR PLUGIN VIA_DIR VIA

echo "Testing $(basename "$PLUGIN")..."
results=$(echo "$entries" | xargs -P "$JOBS" -I {} bash -c 'run_one {}')
echo "$results" | sort -V -k2
echo "$results" | grep -q '^FAIL' && exit 1 || exit 0
