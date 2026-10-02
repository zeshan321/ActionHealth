#!/usr/bin/env bash
# Runs the tests for optional plugins. Needs the Spigot jars from matrix.sh.
#   - WorldGuard region test on one server per WorldGuardWrapper implementation:
#     WorldGuard 6.1 (legacy), 6.2 (v6) and 7 (v7).
#   - PlaceholderAPI test on the oldest and newest versions.
#
#   integrations.sh [plugin.jar]
set -uo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
[ -d "$DIR/node_modules" ] || docker run --rm -v "$DIR:$DIR" -w "$DIR" node:22-slim npm ci --silent
PLUGIN=${1:-$(ls "$DIR"/../../build/libs/ActionHealth-*-all.jar | head -n 1)}
SPIGOT_DIR=${SPIGOT_DIR:-$DIR/../.cache/spigot}
CACHE="$DIR/../.cache/plugins"
VIA_DIR="$DIR/../.cache/via"
MODRINTH=https://cdn.modrinth.com/data
mkdir -p "$CACHE"

fetch() {
  [ -f "$CACHE/$(basename "$1")" ] || curl -sfL -o "$CACHE/$(basename "$1")" "$1"
  echo "$CACHE/$(basename "$1")"
}

# <spigot-version> <java> <client> <WorldGuard url> <WorldEdit url> [extra jars]
# WorldEdit 7.4 needs Java 25, so 1.21.11 runs on Java 25 here.
cases=(
  "1.8.8 8 1.8.8 $MODRINTH/DKY9btbd/versions/rzfNT8ql/worldguard-6.1.jar $MODRINTH/1u6JkXh5/versions/JezAXbj7/worldedit-bukkit-6.1.9.jar"
  "1.12.2 8 1.12.2 $MODRINTH/DKY9btbd/versions/9Mm5Xl5Z/worldguard-bukkit-6.2.2.jar $MODRINTH/1u6JkXh5/versions/JezAXbj7/worldedit-bukkit-6.1.9.jar"
  "1.21.11 25 1.21.11 $MODRINTH/DKY9btbd/versions/WaElxvDz/worldguard-bukkit-7.0.15.jar $MODRINTH/1u6JkXh5/versions/F5ea2ov3/worldedit-bukkit-7.4.5.jar"
  "26.3 25 26.1 $MODRINTH/DKY9btbd/versions/TtfwTyi6/worldguard-bukkit-7.0.19.jar $MODRINTH/1u6JkXh5/versions/J1eeOh6C/worldedit-bukkit-7.4.6-beta-02.jar $VIA_DIR/ViaVersion-5.12.0.jar $VIA_DIR/ViaBackwards-5.12.0.jar"
)

failed=0
check() {
  local mode=$1 mc=$2 java=$3 client=$4 label=$5
  shift 5
  local log="$DIR/runs/$mc-$mode.log"
  if MODE=$mode "$DIR/run.sh" "$SPIGOT_DIR/spigot-$mc.jar" "$mc" "$java" "$PLUGIN" "$client" "$@" > "$log" 2>&1; then
    echo "PASS  $mc with $label $(grep -oE '^(PASS|FAIL) [a-z-]+' "$log" | tr '\n' ' ')"
  else
    failed=1
    echo "FAIL  $mc with $label $(grep -oE '^(PASS|FAIL) [a-z-]+' "$log" | tr '\n' ' ') see $log"
  fi
}

for c in "${cases[@]}"; do
  set -- $c
  mc=$1; java=$2; client=$3; wg=$(fetch "$4"); we=$(fetch "$5"); shift 5
  check worldguard "$mc" "$java" "$client" "$(basename "$wg")" "$we" "$wg" "$@"
done

PAPI=$(fetch "$MODRINTH/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar")
check placeholderapi 1.8.8 8 1.8.8 "$(basename "$PAPI")" "$PAPI"
check placeholderapi 26.3 25 26.1 "$(basename "$PAPI")" "$PAPI" "$VIA_DIR/ViaVersion-5.12.0.jar" "$VIA_DIR/ViaBackwards-5.12.0.jar"
exit $failed
