#!/usr/bin/env bash
# Runs the tests for optional plugins. Needs the Spigot jars from matrix.sh.
#   - WorldGuard region test on WorldGuard 6.1, 6.2 and 7 (two versions). WorldGuard 6 and 7
#     have different APIs.
#   - PlaceholderAPI test on the oldest and newest versions.
#   - ModelEngine test on 1.20.4, if a ModelEngine jar is in compat/.cache/plugins.
#   - The main test on Paper 1.8.8, 1.20.6, 1.21.11 and 26.3. Paper rewrites plugin
#     reflection on 1.20.5+, which once broke a getMethod call that Spigot accepts.
#   - The main test on Paper 1.16.5, started with the Adventure action bar method. 1.16.5 is the
#     oldest Paper version with Adventure.
#   - The main test on Folia 1.21.11 and 26.2. Folia has a thread for each region of the world
#     and no main thread.
#
#   integrations.sh [plugin.jar]
set -uo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/lib.sh"
[ -d "$DIR/node_modules" ] || docker run --rm -v "$DIR:$DIR" -w "$DIR" node:22-slim npm ci --silent
PLUGIN=${1:-$(ls "$DIR"/../../build/libs/ActionHealth-*-all.jar | head -n 1)}
SPIGOT_DIR=${SPIGOT_DIR:-$DIR/../.cache/spigot}
MODRINTH=https://cdn.modrinth.com/data
fetch_via

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
  "26.3 25 26.1 $MODRINTH/DKY9btbd/versions/TtfwTyi6/worldguard-bukkit-7.0.19.jar $MODRINTH/1u6JkXh5/versions/J1eeOh6C/worldedit-bukkit-7.4.6-beta-02.jar $VIA_DIR/ViaVersion-$VIA.jar $VIA_DIR/ViaBackwards-$VIA.jar"
)

failed=0
# <mode> <spigot-version> <java> <client> <label> [extra jars]
check() {
  local mode=$1 mc=$2 java=$3 client=$4 label=$5
  shift 5
  MODE=$mode check_run "$mc with $label" "$mc-$mode" "$SPIGOT_DIR/spigot-$mc.jar" "$mc" "$java" "$PLUGIN" "$client" "$@"
}

for c in "${cases[@]}"; do
  set -- $c
  mc=$1; java=$2; client=$3; wg=$(fetch "$4"); we=$(fetch "$5"); shift 5
  check worldguard "$mc" "$java" "$client" "$(basename "$wg")" "$we" "$wg" "$@"
done

PAPI=$(fetch "$MODRINTH/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar")
check placeholderapi 1.8.8 8 1.8.8 "$(basename "$PAPI")" "$PAPI"
check placeholderapi 26.3 25 26.1 "$(basename "$PAPI")" "$PAPI" "$VIA_DIR/ViaVersion-$VIA.jar" "$VIA_DIR/ViaBackwards-$VIA.jar"

# ModelEngine has no public download URL. To run this test, download the free "Legacy Model Engine
# Demo" (R3, 1.16.5-1.20.4) from https://www.spigotmc.org/resources/106521/ into compat/.cache/plugins.
ME=$(ls "$CACHE"/ModelEngine-*.jar 2>/dev/null | head -n 1)
if [ -n "$ME" ]; then
  check modelengine 1.20.4 17 1.20.4 "$(basename "$ME")" "$ME"
else
  echo "SKIP  ModelEngine: no ModelEngine-*.jar in $CACHE"
fi

# <paper|folia> <version> <java> <client> [extra jars]
check_fill() {
  local project=$1 mc=$2 java=$3 client=$4
  shift 4
  local label
  label="$(echo "${project:0:1}" | tr a-z A-Z)${project:1} $mc"
  check_run "$label" "$project-$mc" "$(fill "$project" "$mc")" "$mc" "$java" "$PLUGIN" "$client" "$@"
}

check_fill paper 1.8.8 8 1.8.8
check_fill paper 1.20.6 21 1.20.6
check_fill paper 1.21.11 21 1.21.11
check_fill paper 26.3 25 26.1 "$VIA_DIR/ViaVersion-$VIA.jar" "$VIA_DIR/ViaBackwards-$VIA.jar"
ACTIONBAR=adventure check_run "Paper 1.16.5 (Adventure)" paper-1.16.5-adventure "$(fill paper 1.16.5)" 1.16.5 8 "$PLUGIN" 1.16.5
check_fill folia 1.21.11 21 1.21.11
check_fill folia 26.2 25 26.1 "$VIA_DIR/ViaVersion-$VIA.jar" "$VIA_DIR/ViaBackwards-$VIA.jar"
exit $failed
