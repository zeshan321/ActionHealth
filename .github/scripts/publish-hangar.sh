#!/usr/bin/env bash
# Uploads a release jar to Hangar (hangar.papermc.io) as a new version of the project.
#
#   HANGAR_TOKEN=... publish-hangar.sh <version> <jar> <notes.md>
#
# HANGAR_PROJECT is the project slug (default: ActionHealth). MINECRAFT_VERSIONS is the tested
# range, for example 1.8-26.3. Hangar takes the range as it is. If the project already has the
# version, nothing is uploaded. DRY_RUN=1 prints the request data instead.
set -euo pipefail

VERSION=$1
JAR=$2
NOTES=$3
PROJECT=${HANGAR_PROJECT:-ActionHealth}
RANGE=${MINECRAFT_VERSIONS:?set MINECRAFT_VERSIONS, for example 1.8-26.3}
API=https://hangar.papermc.io/api/v1

data=$(jq -n --arg version "$VERSION" --rawfile notes "$NOTES" --arg range "$RANGE" '{
  version: $version, channel: "Release", description: $notes,
  platformDependencies: {PAPER: [$range]}, pluginDependencies: {},
  files: [{platforms: ["PAPER"]}]
}')

if [ "${DRY_RUN:-}" = 1 ]; then
  echo "$data" | jq 'del(.description) + {description_length: (.description | length)}'
  exit 0
fi

# The API key gives a short-lived token for the other requests.
token=$(curl -fsS -X POST "$API/authenticate?apiKey=$HANGAR_TOKEN" | jq -r .token)
auth="Authorization: HangarAuth $token"

if curl -fsS -o /dev/null -H "$auth" "$API/projects/$PROJECT/versions/$VERSION" 2>/dev/null; then
  echo "Hangar already has version $VERSION. Nothing to upload."
  exit 0
fi

payload=$(mktemp)
echo "$data" > "$payload"
curl -fsS -X POST -H "$auth" "$API/projects/$PROJECT/upload" \
  -F "versionUpload=@$payload;type=application/json" -F "files=@$JAR;type=application/java-archive" | jq -r '"Uploaded to Hangar: \(.url)"'
