#!/usr/bin/env bash
set -euo pipefail
set +x
repo=$(cd "$(dirname "$0")/../.." && pwd)
for tool in docker age age-keygen jq curl openssl dash perl; do
    command -v "$tool" >/dev/null || { echo "Missing CI tool: $tool" >&2; exit 1; }
done
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
    echo "CI tests require a local Unix Docker endpoint." >&2; exit 1;
}
docker info >/dev/null
docker compose version
docker build --build-arg "TEST_UID=$(id -u)" --tag ppe-ci-shell:local \
    --file "$repo/containers/ci/Dockerfile.shell" "$repo/containers/ci"
docker build --tag ppe-db:local --file "$repo/containers/production/Dockerfile.db" "$repo"
docker build --tag ppe-backup:local "$repo/containers/backup"
