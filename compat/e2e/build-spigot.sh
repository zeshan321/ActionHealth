#!/usr/bin/env bash
# Builds a Spigot server jar with BuildTools in Docker.
#
#   build-spigot.sh <mc-version> <java-major>
#
# Output: $SPIGOT_DIR/spigot-<version>.jar (default compat/.cache/spigot). BuildTools runs
# inside the container filesystem, because old Spigot sources need a case-sensitive FS.
set -uo pipefail

V=$1
J=$2
DIR=$(cd "$(dirname "$0")" && pwd)
SPIGOT_DIR=${SPIGOT_DIR:-$DIR/../.cache/spigot}
mkdir -p "$SPIGOT_DIR/logs" "$SPIGOT_DIR/out/$V"
SPIGOT_DIR=$(cd "$SPIGOT_DIR" && pwd)

[ -f "$SPIGOT_DIR/spigot-$V.jar" ] && exit 0
docker image inspect "ah-jdk$J" > /dev/null 2>&1 || docker build -q --build-arg "JAVA=$J" -t "ah-jdk$J" "$DIR" > /dev/null
[ -f "$SPIGOT_DIR/BuildTools.jar" ] || curl -sfL -o "$SPIGOT_DIR/BuildTools.jar" \
  https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

if docker run --rm -v "$SPIGOT_DIR:/jars" -v "$SPIGOT_DIR/out/$V:/out" \
    -v "$DIR/m2-settings.xml:/root/.m2/settings.xml:ro" -e MAVEN_OPTS=-Xmx2g "ah-jdk$J" \
    sh -c "mkdir /b && cd /b && cp /jars/BuildTools.jar . && java -Xmx1500m -jar BuildTools.jar --rev $V --output-dir /out" \
    > "$SPIGOT_DIR/logs/build-$V.log" 2>&1; then
  # BuildTools names the jar after the exact build, for example 26.1 builds spigot-26.1.2.jar.
  mv "$(ls "$SPIGOT_DIR/out/$V"/spigot-*.jar | head -n 1)" "$SPIGOT_DIR/spigot-$V.jar"
  echo "built Spigot $V"
else
  echo "FAILED to build Spigot $V (see $SPIGOT_DIR/logs/build-$V.log)"
  exit 1
fi
