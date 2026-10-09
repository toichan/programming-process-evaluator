#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
source "$(dirname "$0")/source-manifest.sh"
[[ "$#" = 2 ]] || { echo "Usage: bash prepare-release.sh COMMIT ABSOLUTE_RELEASE_ROOT" >&2; exit 1; }
commit=$(git rev-parse --verify --end-of-options "$1^{commit}")
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit." >&2; exit 1; }
root=$2
[[ "$root" = /* && "$root" != / && "$root" != "$HOME" && ! -L "$root" ]] || {
    echo "A dedicated absolute release root is required." >&2; exit 1;
}
release="$root/$commit"
[[ ! -e "$release" ]] || { echo "Release already exists; never overwrite it." >&2; exit 1; }
mkdir -p -m 700 "$root"
mkdir -m 700 "$release" "$release/source"
git archive --format=tar "$commit" | tar -x -C "$release/source"
[[ -f "$release/source/compose.production.yml" ]] || {
    echo "Selected commit does not contain production configuration." >&2; exit 1;
}
if find "$release/source" -type f \( -name '.env' -o -name '.env.production' -o -name '*.key' -o -name '*.pem' \) |
    grep -q .; then
    echo "Sensitive file names found in committed release; review before building." >&2; exit 1
fi
build() {
    local image=$1 dockerfile=$2 context=$3
    shift 3
    docker build --build-arg "PPE_GIT_COMMIT=$commit" --tag "$image:$commit" \
        --file "$context/$dockerfile" "$@" "$context"
}
source="$release/source"
build ppe-app containers/production/Dockerfile "$source" --target app
build ppe-tools containers/production/Dockerfile "$source" --target tools
build ppe-db containers/production/Dockerfile.db "$source"
build ppe-runner Dockerfile "$source/containers/python-runner"
build ppe-broker Dockerfile "$source/containers/docker-broker"
build ppe-nginx Dockerfile "$source/containers/nginx"
build ppe-backup Dockerfile "$source/containers/backup"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image:$commit")
    [[ "$revision" = "$commit" ]] || { echo "Image revision mismatch." >&2; exit 1; }
    docker image inspect --format '{{.Id}}' "$image:$commit" > "$release/$image.id"
done
printf '%s\n' "$commit" > "$release/commit"
generate_source_manifest "$release/source" > "$release/source.sha256"
printf '%s\n' "$commit" > "$release/READY"
echo "Source manifest SHA-256: $(source_hash "$release/source.sha256")"
echo "Commit-pinned release built and verified: $release"
