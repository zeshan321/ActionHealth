#!/usr/bin/env bash
# Checks the built plugin jar against every spigot-api version published since 1.8,
# plus any server jars given as arguments. Needs Java 11+ and curl.
#
#   ./gradlew build && compat/linkage-check.sh [spigot-server.jar ...]
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
CACHE="$DIR/.cache"
REPO=https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api
ASM=9.8
mkdir -p "$CACHE/api"

PLUGIN=$(ls "$DIR"/../build/libs/ActionHealth-*-all.jar | head -n 1)

for a in asm asm-tree; do
  [ -f "$CACHE/$a.jar" ] || curl -sfL -o "$CACHE/$a.jar" "https://repo1.maven.org/maven2/org/ow2/asm/$a/$ASM/$a-$ASM.jar"
done

# Release versions only (no pre-releases). If a version has several R0.x snapshots, the last one wins.
versions=$(curl -sf "$REPO/maven-metadata.xml" | grep -oE '<version>[0-9.]+-R0\.[0-9]-SNAPSHOT</version>' | sed -E 's#</?version>##g')
for v in $versions; do
  mc=${v%%-*}
  out="$CACHE/api/spigot-api-$mc.jar"
  stamp="$CACHE/api/spigot-api-$mc.version"
  [ -f "$out" ] && [ "$(cat "$stamp" 2>/dev/null)" = "$v" ] && continue
  value=$(curl -sf "$REPO/$v/maven-metadata.xml" | tr -d ' \n' | grep -oE '<extension>jar</extension><value>[^<]+' | head -n 1 | sed 's#.*<value>##')
  curl -sfL -o "$out" "$REPO/$v/spigot-api-$value.jar"
  echo "$v" > "$stamp"
done

targets=$(ls "$CACHE"/api/*.jar | sort -V)
java -cp "$CACHE/asm.jar:$CACHE/asm-tree.jar" "$DIR/LinkageCheck.java" "$PLUGIN" $targets "$@"
