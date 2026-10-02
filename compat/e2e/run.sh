#!/usr/bin/env bash
# Runs the bot test against one Spigot server in Docker.
#
#   [MODE=worldguard|placeholderapi] run.sh <spigot.jar> <mc-version> <java-major> <plugin.jar> [client-version] [extra-plugin.jar ...]
#
# Needs only Docker. The client version defaults to the server version. For versions
# mineflayer does not support yet, pass an older client version plus the ViaVersion and
# ViaBackwards jars. MODE=worldguard runs the region test (pass the WorldGuard and WorldEdit
# jars as extra plugins). MODE=placeholderapi runs the placeholder test (pass the
# PlaceholderAPI jar).
#
# Exit code 0 means the server started, the plugin enabled, every scenario passed and the
# log has no ActionHealth errors. Server files: compat/e2e/runs/<server>-<plugin>-<time>/.
set -uo pipefail

SPIGOT=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
MC=$2
JAVA=$3
PLUGIN=$(cd "$(dirname "$4")" && pwd)/$(basename "$4")
CLIENT=${5:-$MC}
shift 5 2>/dev/null || shift $#

DIR=$(cd "$(dirname "$0")" && pwd)
[ -d "$DIR/node_modules" ] || docker run --rm -v "$DIR:$DIR" -w "$DIR" node:22-slim npm ci --silent
# A new folder for every run. Reusing a folder that the host just cleaned can show stale files
# inside the Docker VM.
RUN="$DIR/runs/$(basename "$SPIGOT" .jar)-$(basename "$PLUGIN" .jar)${MODE:+-$MODE}-$(date +%Y%m%d-%H%M%S)-$$"
NAME="ah-e2e-$MC-$$"
mkdir -p "$RUN/plugins/ActionHealth"
cp "$PLUGIN" "$RUN/plugins/"
VIA=0
for extra in "$@"; do
  cp "$extra" "$RUN/plugins/"
  case "$(basename "$extra")" in Via*) VIA=1 ;; esac
done

# Default config with health numbers in the message, so the test can read them.
STYLE='{usestyle}'
[ "${MODE:-}" = placeholderapi ] && STYLE='%player_name%'
sed "s/^Health Message: .*/Health Message: '\&7\&l{name}: {health}\/{maxhealth} $STYLE'/" \
  "$DIR/../../src/main/resources/config.yml" > "$RUN/plugins/ActionHealth/config.yml"

level=FLAT
[ "$(printf '%s\n1.19\n' "$MC" | sort -V | head -n 1)" = "1.19" ] && level=minecraft:flat
cat > "$RUN/server.properties" <<PROPS
online-mode=false
white-list=false
enforce-whitelist=false
level-type=$level
generate-structures=false
difficulty=peaceful
spawn-monsters=false
spawn-protection=0
view-distance=4
simulation-distance=4
enable-rcon=false
motd=ActionHealth e2e
PROPS
echo "eula=true" > "$RUN/eula.txt"
# Console commands from test.js, piped into the server.
: > "$RUN/console.in"

# MODE=worldguard: a WorldGuard region around spawn that is in the default "Disabled regions".
if [ "${MODE:-}" = worldguard ]; then
  mkdir -p "$RUN/plugins/WorldGuard/worlds/world"
  cat > "$RUN/plugins/WorldGuard/worlds/world/regions.yml" <<REGIONS
regions:
    testing_region:
        min: {x: -4000.0, y: -64.0, z: -4000.0}
        max: {x: 4000.0, y: 319.0, z: 4000.0}
        members: {}
        flags: {}
        owners: {}
        type: cuboid
        priority: 0
REGIONS
fi

docker run -d --name "$NAME" \
  -v "$RUN:/srv" -v "$SPIGOT:/server.jar:ro" -w /srv "ah-jdk$JAVA" \
  sh -c 'tail -n 0 -F console.in | java -Xms512m -Xmx1536m -DIReallyKnowWhatIAmDoingISwear -jar /server.jar nogui' > /dev/null

cleanup() { docker rm -f "$NAME" > /dev/null 2>&1; }
trap cleanup EXIT

status=0
for _ in $(seq 1 300); do
  grep -q "Done (" "$RUN/logs/latest.log" 2>/dev/null && break
  [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" = true ] || break
  sleep 1
done
if ! grep -q "Done (" "$RUN/logs/latest.log" 2>/dev/null; then
  echo "SERVER DID NOT START"; tail -30 "$RUN/logs/latest.log" 2>/dev/null; docker logs --tail 30 "$NAME" 2>&1; exit 1
fi

grep -E "Enabling ActionHealth|Error occurred while enabling ActionHealth" "$RUN/logs/latest.log" | sed 's/^/server: /'

# The bots run in a Node container that shares the server's network namespace, so they
# connect to localhost without Docker port mapping.
docker run --rm --network "container:$NAME" -e VIA=$VIA -e MODE="${MODE:-}" \
  -v "$DIR:$DIR:ro" -v "$RUN:$RUN" -w "$DIR" node:22-slim \
  node "$DIR/test.js" 127.0.0.1 25565 "$CLIENT" "$MC" "$RUN" || status=1

sleep 1
# Any stack trace or warning that mentions ActionHealth is a failure.
if grep -nE "actionhealth|ActionHealth" "$RUN/logs/latest.log" | grep -iE "exception|error|could not|warn" | grep -v "Enabling ActionHealth"; then
  echo "FAIL log: ActionHealth errors in server log"; status=1
else
  echo "PASS log: no ActionHealth errors in server log"
fi
exit $status
