#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/image-build-common.sh"
source "$scripts/ecr-release-common.sh"
source "$scripts/../production/source-manifest.sh"
[[ $# = 4 && "$1" =~ ^[a-f0-9]{40}$ && "$4" = /* && ! -e "$4" && ! -L "$4" ]] || exit 1
sha=$1 directory=$2 ci=$3 work=$4
image_build_target "$sha" refs/remotes/origin/main || exit 1
ecr_release_ci_evidence "$ci" "$sha" "$GITHUB_REPOSITORY" || exit 1
ecr_release_verify_transfer "$directory" "$sha" || exit 1
mkdir -m 0700 "$work" "$work/source" "$work/docker-config"
git archive --format=tar "$sha" > "$work/source.tar"
cmp "$work/source.tar" "$directory/source.tar" || exit 1
tar -xf "$work/source.tar" -C "$work/source"
generate_source_manifest "$work/source" > "$work/source.sha256"
cmp "$work/source.sha256" "$directory/source.sha256" || exit 1
for image in app tools db runner broker nginx backup; do
    archive="$directory/$image.tar"
    tar -tf "$archive" > "$work/archive-names"
    # Docker's loader must never receive links, traversal, duplicate entries or device nodes.
    ! grep -Eq '(^/|(^|/)\.\.?(/|$)|\\)' "$work/archive-names" || exit 1
    [[ "$(wc -l < "$work/archive-names")" = "$(LC_ALL=C sort -u "$work/archive-names" | wc -l)" ]] || exit 1
    tar -tvf "$archive" | awk 'substr($1,1,1)!="-" && substr($1,1,1)!="d" {bad=1} END{exit bad}' || exit 1
    tar -xOf "$archive" manifest.json > "$work/docker-manifest.json"
    tags=$(jq -c '.[0].RepoTags' "$directory/$image.json")
    jq -e --argjson tags "$tags" '
        length==1 and .[0].RepoTags==$tags and ($tags|length)==1 and
        (.[0].Config|test("^(blobs/sha256/)?[a-f0-9]{64}(\\.json)?$")) and
        (.[0].Layers|type=="array" and length>0 and
          all(type=="string" and test("^(blobs/sha256/[a-f0-9]{64}|[a-f0-9]{64}/layer\\.tar)$")))' "$work/docker-manifest.json" >/dev/null || {
        echo "Invalid Docker save manifest for $image." >&2; exit 1;
    }
    while IFS= read -r layer; do
        [[ "$(grep -Fxc "$layer" "$work/archive-names")" = 1 ]] || exit 1
    done < <(jq -r '.[0].Layers[]' "$work/docker-manifest.json")
    config=$(jq -r '.[0].Config' "$work/docker-manifest.json")
    tar -xOf "$archive" "$config" > "$work/config.json"
    id=$(jq -r '.[0].Id' "$directory/$image.json")
    [[ "sha256:$(source_hash "$work/config.json")" = "$id" ]] || exit 1
    jq -e --arg sha "$sha" '.os=="linux" and .architecture=="amd64" and
        .config.Labels["org.opencontainers.image.revision"]==$sha' "$work/config.json" >/dev/null || exit 1
    docker --config "$work/docker-config" load -i "$archive" > /dev/null || exit 1
    docker --config "$work/docker-config" image inspect "$id" > "$work/loaded.json" || exit 1
    image_build_metadata "$work/loaded.json" "$sha" || exit 1
    [[ "$(jq -r '.[0].Id' "$work/loaded.json")" = "$id" ]] || exit 1
done
echo "Transferred bytes, source correspondence and seven loaded configs verified before authentication."
