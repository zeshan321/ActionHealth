#!/usr/bin/env bash
# Runs a small set of the end-to-end tests, for CI. It covers each way the plugin sends
# action bars and each type of scheduler:
#   - Spigot 1.8.8 on Java 8 (NMS packets)
#   - Spigot 1.21.11 on Java 21 (Spigot API)
#   - Paper 26.3 on Java 25 (Paper, newest version, with ViaVersion for the bot)
#   - Paper 1.21.11 on Java 21, started with the Adventure method (the fallback if Paper removes
#     the BungeeCord chat API)
#   - Folia 1.21.11 on Java 21 (region threads)
# matrix.sh and integrations.sh run the full set before a release.
#
#   ci.sh [plugin.jar]
set -uo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
. "$DIR/lib.sh"
PLUGIN=${1:-$(ls "$DIR"/../../build/libs/ActionHealth-*-all.jar | head -n 1)}
export SPIGOT_DIR=${SPIGOT_DIR:-$DIR/../.cache/spigot}
[ -d "$DIR/node_modules" ] || docker run --rm -v "$DIR:$DIR" -w "$DIR" node:22-slim npm ci --silent
fetch_via

failed=0
"$DIR/build-spigot.sh" 1.8.8 8 || failed=1
"$DIR/build-spigot.sh" 1.21.11 21 || failed=1

check_run "Spigot 1.8.8" spigot-1.8.8 "$SPIGOT_DIR/spigot-1.8.8.jar" 1.8.8 8 "$PLUGIN" 1.8.8
check_run "Spigot 1.21.11" spigot-1.21.11 "$SPIGOT_DIR/spigot-1.21.11.jar" 1.21.11 21 "$PLUGIN" 1.21.11
check_run "Paper 26.3" paper-26.3 "$(fill paper 26.3)" 26.3 25 "$PLUGIN" 26.1 \
  "$VIA_DIR/ViaVersion-$VIA.jar" "$VIA_DIR/ViaBackwards-$VIA.jar"
ACTIONBAR=adventure check_run "Paper 1.21.11 (Adventure)" paper-1.21.11-adventure "$(fill paper 1.21.11)" 1.21.11 21 "$PLUGIN" 1.21.11
check_run "Folia 1.21.11" folia-1.21.11 "$(fill folia 1.21.11)" 1.21.11 21 "$PLUGIN" 1.21.11
exit $failed
