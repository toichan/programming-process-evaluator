#!/usr/bin/env bash
# Sourced by mutation scripts; never source configuration or release metadata.
set -euo pipefail
set +x
umask 077
project=${PPE_PROJECT:-ppe-production}
case "${PPE_OPERATION_APPROVAL:-}" in
    production-approved) ;;
    local-test)
        [[ "$project" =~ ^ppe-(sim|preparation)-[a-z0-9-]+$ ]] || {
            echo "Local tests cannot target the production project." >&2; exit 1;
        }
        endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
        [[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
            echo "Local tests require a local Unix Docker endpoint." >&2; exit 1;
        }
        ;;
    *) echo "Explicit production-approved or guarded local-test approval is required." >&2; exit 1 ;;
esac
[[ "${PPE_MAINTENANCE_APPROVED:-}" = yes ]] || {
    echo "Approve a maintenance window outside classes before updating." >&2; exit 1;
}
: "${PPE_SECRETS_DIR:?Set PPE_SECRETS_DIR}"
: "${PPE_TLS_DIR:?Set PPE_TLS_DIR}"
: "${PPE_ACME_DIR:?Set PPE_ACME_DIR}"
: "${PPE_STATE_DIR:?Set a persistent private state directory}"
[[ "$PPE_STATE_DIR" = /* && ! -L "$PPE_STATE_DIR" && "$PPE_STATE_DIR" != / && "$PPE_STATE_DIR" != "$HOME" ]] || exit 1
mkdir -p -m 700 "$PPE_STATE_DIR"
[[ -z "$(find "$PPE_STATE_DIR" -prune ! -perm 0700 -print)" ]] || {
    echo "Operation state directory must be private (0700)." >&2; exit 1;
}
source "$(dirname "${BASH_SOURCE[0]}")/database-lock.sh"
acquire_database_lock || exit 1
mkdir "$PPE_STATE_DIR/deployment.lock" 2>/dev/null || {
    echo "Another deployment is active or a stale lock needs manual review." >&2; exit 1;
}
trap 'rmdir "$PPE_STATE_DIR/deployment.lock"' EXIT
if [[ -f "$PPE_STATE_DIR/project" ]]; then
    [[ "$(cat "$PPE_STATE_DIR/project")" = "$project" ]] || {
        echo "State directory belongs to another Compose project." >&2; exit 1;
    }
else
    printf '%s\n' "$project" > "$PPE_STATE_DIR/project"
fi
verify_release() {
    [[ "$1" = /* && -f "$1/READY" && -f "$1/commit" ]] || { echo "Release is not ready." >&2; exit 1; }
    PPE_RELEASE=$(cat "$1/commit")
    [[ "$PPE_RELEASE" =~ ^[0-9a-f]{40}$ && "$(cat "$1/READY")" = "$PPE_RELEASE" ]] || exit 1
    for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
        current=$(docker image inspect --format '{{.Id}}' "$image:$PPE_RELEASE")
        [[ "$current" = "$(cat "$1/$image.id")" ]] || { echo "Release image was retagged: $image" >&2; exit 1; }
    done
    export PPE_RELEASE PPE_PROJECT="$project"
    export PPE_COMPOSE_FILE="$1/source/compose.production.yml" PPE_BACKUP_IMAGE="ppe-backup:$PPE_RELEASE"
    export PPE_OPERATION_DIR="$PPE_STATE_DIR"
}
dc() { docker compose -p "$project" -f "$PPE_COMPOSE_FILE" "$@"; }
