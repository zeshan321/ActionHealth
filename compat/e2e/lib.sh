# Shared helpers for the e2e scripts. Source it after setting DIR.

CACHE="$DIR/../.cache/plugins"
VIA_DIR="$DIR/../.cache/via"
VIA=5.12.0
mkdir -p "$CACHE" "$VIA_DIR" "$DIR/runs"

# Downloads ViaVersion and ViaBackwards, for servers that mineflayer does not support yet.
fetch_via() {
  for p in ViaVersion ViaBackwards; do
    # Download to a temporary name, so a failed download is not kept as a broken jar.
    [ -f "$VIA_DIR/$p-$VIA.jar" ] || { curl -sfL -o "$VIA_DIR/$p-$VIA.jar.part" \
      "https://github.com/ViaVersion/$p/releases/download/$VIA/$p-$VIA.jar" \
      && mv "$VIA_DIR/$p-$VIA.jar.part" "$VIA_DIR/$p-$VIA.jar"; }
  done
}

# Latest build of a Paper or Folia version, from the PaperMC download API.
#   fill <paper|folia> <version>
fill() {
  local jar="$CACHE/$1-$2.jar"
  if [ ! -f "$jar" ]; then
    local url
    url=$(curl -sf "https://fill.papermc.io/v3/projects/$1/versions/$2/builds/latest" \
      | grep -oE '"url":"[^"]+"' | head -n 1 | sed 's/"url":"//; s/"$//')
    [ -n "$url" ] && curl -sfL -o "$jar.part" "$url" && mv "$jar.part" "$jar"
  fi
  echo "$jar"
}

# Runs run.sh and prints one PASS or FAIL line. Sets failed=1 on a failure.
#   check_run <label> <log name> <run.sh arguments...>
check_run() {
  local label=$1 log="$DIR/runs/$2.log"
  shift 2
  if "$DIR/run.sh" "$@" > "$log" 2>&1; then
    echo "PASS  $label $(grep -oE '^(PASS|FAIL) [a-z-]+' "$log" | tr '\n' ' ')"
  else
    failed=1
    echo "FAIL  $label $(grep -oE '^(PASS|FAIL) [a-z-]+' "$log" | tr '\n' ' ') see $log"
  fi
}
