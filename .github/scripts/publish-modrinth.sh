#!/usr/bin/env bash
# Uploads a release jar to Modrinth as a new version of the project.
#
#   MODRINTH_TOKEN=... publish-modrinth.sh <version> <jar> <title> <notes.md>
#
# MODRINTH_PROJECT is the project slug or ID (default: actionhealth). MINECRAFT_VERSIONS is the
# tested range, for example 1.8-26.3: every Minecraft release in it is listed. If the project
# already has the version, nothing is uploaded. DRY_RUN=1 prints the request data instead.
set -euo pipefail

VERSION=$1
JAR=$2
TITLE=$3
NOTES=$4
PROJECT=${MODRINTH_PROJECT:-actionhealth}
RANGE=${MINECRAFT_VERSIONS:?set MINECRAFT_VERSIONS, for example 1.8-26.3}
API=https://api.modrinth.com/v2
AGENT="zeshan321/ActionHealth release workflow (github.com/zeshan321/ActionHealth)"

api() { curl -fsS -A "$AGENT" ${MODRINTH_TOKEN:+-H "Authorization: $MODRINTH_TOKEN"} "$@"; }

# Every release version from the lowest to the highest version of the range.
min=${RANGE%-*}
max=${RANGE#*-}
versions=$(api "$API/tag/game_version" | jq -r '.[] | select(.version_type == "release") | .version' |
  while read -r v; do
    if [ "$(printf '%s\n%s\n' "$min" "$v" | sort -V | head -n 1)" = "$min" ] &&
      [ "$(printf '%s\n%s\n' "$v" "$max" | sort -V | head -n 1)" = "$v" ]; then
      echo "$v"
    fi
  done | jq -R . | jq -s .)
[ "$(echo "$versions" | jq length)" -gt 0 ] || { echo "No Minecraft versions found in $RANGE"; exit 1; }

data=$(jq -n --arg version "$VERSION" --arg title "$TITLE" --rawfile notes "$NOTES" --argjson versions "$versions" \
  --arg project "$PROJECT" '{
    project_id: $project, version_number: $version, name: $title, changelog: $notes,
    game_versions: $versions, version_type: "release",
    loaders: ["bukkit", "spigot", "paper", "purpur", "folia"],
    dependencies: [], featured: true, file_parts: ["jar"], primary_file: "jar"
  }')

if [ "${DRY_RUN:-}" = 1 ]; then
  echo "$data" | jq 'del(.changelog) + {changelog_length: (.changelog | length)}' 2>/dev/null || echo "$data"
  exit 0
fi

# The upload needs the project ID, not the slug.
id=$(api "$API/project/$PROJECT" | jq -r .id)
if api "$API/project/$id/version" | jq -e --arg v "$VERSION" 'any(.[]; .version_number == $v)' > /dev/null; then
  echo "Modrinth already has version $VERSION. Nothing to upload."
  exit 0
fi

data=$(echo "$data" | jq --arg id "$id" '.project_id = $id')
api -X POST "$API/version" -F "data=$data;type=application/json" -F "jar=@$JAR;type=application/java-archive" |
  jq -r '"Uploaded to Modrinth: https://modrinth.com/plugin/'"$PROJECT"'/version/\(.version_number)"'
