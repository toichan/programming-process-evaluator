#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
source "$scripts/image-build-common.sh"
root=$(mktemp -d)
trap 'rm -f "$root/metadata.json" "$root/source/src/credential"; rmdir "$root/source/src" "$root/source/containers" "$root/source/docs/rubric" "$root/source/docs" "$root/source" "$root"' EXIT
mkdir -p "$root/source/src" "$root/source/containers" "$root/source/docs/rubric"
sha=$(git rev-parse HEAD)
image_build_target "$sha" HEAD
if image_build_target main HEAD || image_build_target "$(printf '%040d' 0)" HEAD; then
    echo "Invalid SHA accepted." >&2; exit 1
fi
jq -n --arg sha "$sha" \
    '[{Id:("sha256:"+("a"*64)),Os:"linux",Architecture:"amd64",Size:123,
    Config:{Labels:{"org.opencontainers.image.revision":$sha}}}]' > "$root/metadata.json"
image_build_metadata "$root/metadata.json" "$sha"
for change in '.[0].Architecture="arm64"' '.[0].Config.Labels["org.opencontainers.image.revision"]="wrong"' \
    '.[0].Id="invalid"' '.[0].Size=0' '.=[]'; do
    if jq "$change" "$root/metadata.json" | image_build_metadata /dev/stdin "$sha"; then
        echo "Invalid image metadata accepted." >&2; exit 1
    fi
done
image_build_source_safety "$root/source"
printf '%s\n' '-----BEGIN PRIVATE KEY-----' > "$root/source/src/credential"
if image_build_source_safety "$root/source" 2>/dev/null; then
    echo "Embedded private key accepted." >&2; exit 1
fi
echo "Image build gates: exact SHA, platform/revision/ID/size, source credential rejection PASS."
