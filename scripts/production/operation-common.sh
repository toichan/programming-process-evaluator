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
source "$(dirname "${BASH_SOURCE[0]}")/source-manifest.sh"
acquire_database_lock || exit 1
tls_parent=$(dirname "$PPE_TLS_DIR")
[[ -d "$tls_parent" && ! -L "$tls_parent" && ! -L "$tls_parent/.ppe-tls-operation.lock" ]] || {
    echo "A safe TLS operation lock parent is required." >&2; exit 1;
}
exec 8>>"$tls_parent/.ppe-tls-operation.lock"
chmod 0600 "$tls_parent/.ppe-tls-operation.lock"
flock -n 8 || { echo "TLS renewal owns the operation lock; retry deployment in a reviewed window." >&2; exit 1; }
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
    if [[ -f "$1/source.sha256" || -n "${PPE_SOURCE_MANIFEST_FILE:-}" ]]; then
        verify_source_manifest "$1" "${PPE_SOURCE_MANIFEST_FILE:-$1/source.sha256}" || exit 1
    fi
    for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
        current=$(docker image inspect --format '{{.Id}}' "$image:$PPE_RELEASE")
        [[ "$current" = "$(cat "$1/$image.id")" ]] || { echo "Release image was retagged: $image" >&2; exit 1; }
    done
    export PPE_RELEASE PPE_PROJECT="$project"
    export PPE_COMPOSE_FILE="$1/source/compose.production.yml" PPE_BACKUP_IMAGE="ppe-backup:$PPE_RELEASE"
    export PPE_OPERATION_DIR="$PPE_STATE_DIR"
}
dc() { docker compose -p "$project" -f "$PPE_COMPOSE_FILE" "$@"; }

verify_operation_resource_paths() {
    local environment setting expected actual
    for environment in "$PPE_BACKUP_OPERATION_ENV" "$PPE_TLS_OPERATION_ENV"; do
        for setting in PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR; do
            case "$setting" in
                PPE_SECRETS_DIR) expected=$PPE_SECRETS_DIR ;;
                PPE_TLS_DIR) expected=$PPE_TLS_DIR ;;
                PPE_ACME_DIR) expected=$PPE_ACME_DIR ;;
            esac
            actual=$(awk -v k="$setting" 'index($0,k"=")==1 {n++; value=substr($0,length(k)+2)}
                END {if(n!=1) exit 1; print value}' "$environment") || return 1
            [[ "$actual" = "$expected" ]] || {
                echo "Deployment and operation paths differ; credential/TLS/ACME changes need separate review." >&2; return 1;
            }
        done
    done
}

verify_tls_operation_wrapper() {
    local definition active
    [[ "${PPE_TLS_OPERATION_WRAPPER:-}" = /* && -f "$PPE_TLS_OPERATION_WRAPPER" \
        && ! -L "$PPE_TLS_OPERATION_WRAPPER" \
        && "$(source_hash "$PPE_TLS_OPERATION_WRAPPER")" = "$(source_hash "$scripts/renew-tls.sh")" ]] || {
        echo "Install and pin the TLS wrapper with shared operation locking before deployment/rollback." >&2; return 1;
    }
    if [[ "$PPE_OPERATION_APPROVAL" = production-approved ]]; then
        definition=$(systemctl show certbot.service --property=ExecStart --value) || return 1
        active=$(systemctl show certbot.service --property=ActiveState --value) || return 1
        [[ "$definition" = *"$PPE_TLS_OPERATION_WRAPPER $PPE_TLS_OPERATION_ENV"* && "$active" = inactive ]] || {
            echo "Certbot must be inactive and wired to the verified shared-lock wrapper/environment." >&2; return 1;
        }
    fi
}

verify_five_services() {
    local service container status image expected directory
    for service in db docker-broker python-runner app nginx; do
        container=$(dc ps --all -q "$service") || return 1
        [[ "$container" =~ ^[a-f0-9]{12,64}$ ]] || {
            echo "Missing or multiple deployment service containers: $service." >&2; return 1;
        }
        status=$(docker inspect --format '{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container") || return 1
        [[ "$status" = 'true|healthy' ]] || {
            echo "Deployment service is not running and healthy: $service." >&2; return 1;
        }
        case "$service" in
            db) image=ppe-db; directory="${PPE_DB_SOURCE_DIR%/source}" ;;
            docker-broker) image=ppe-broker; directory="${PPE_COMPOSE_FILE%/source/compose.production.yml}" ;;
            python-runner) image=ppe-runner; directory="${PPE_COMPOSE_FILE%/source/compose.production.yml}" ;;
            app) image=ppe-app; directory="${PPE_COMPOSE_FILE%/source/compose.production.yml}" ;;
            nginx) image=ppe-nginx; directory="${PPE_COMPOSE_FILE%/source/compose.production.yml}" ;;
        esac
        expected=$(cat "$directory/$image.id") || return 1
        [[ "$(docker inspect --format '{{.Image}}' "$container")" = "$expected" ]] || {
            echo "Running deployment image differs from pinned image: $service." >&2; return 1;
        }
    done
}

verify_previous_release() {
    local previous="$(dirname "$release")/$deployment_previous" image actual
    [[ "$deployment_previous" =~ ^[a-f0-9]{40}$ && -f "$previous/READY" \
        && "$(cat "$previous/READY")" = "$deployment_previous" \
        && "$(cat "$previous/commit")" = "$deployment_previous" ]] || {
        echo "The recorded previous immutable release is unavailable." >&2; return 1;
    }
    PPE_SOURCE_MANIFEST_SHA256=${PPE_PREVIOUS_SOURCE_MANIFEST_SHA256:-} \
        verify_source_manifest "$previous" "${PPE_PREVIOUS_SOURCE_MANIFEST_FILE:-$previous/source.sha256}" || return 1
    for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
        actual=$(docker image inspect --format '{{.Id}}' "$image:$deployment_previous") || return 1
        [[ "$actual" = "$(cat "$previous/$image.id")" ]] || {
            echo "Previous release image has changed: $image." >&2; return 1;
        }
    done
}

restore_previous_app() {
    local target_release=$PPE_RELEASE target_compose=$PPE_COMPOSE_FILE
    local previous="$(dirname "$release")/$deployment_previous" failed=0
    verify_previous_release || return 1
    export PPE_RELEASE="$deployment_previous" PPE_COMPOSE_FILE="$previous/source/compose.production.yml"
    if ! dc config --quiet || ! dc up -d --no-deps --wait --wait-timeout 100 python-runner app \
        || ! verify_five_services || ! bash "$scripts/smoke.sh"; then
        failed=1
    fi
    export PPE_RELEASE="$target_release" PPE_COMPOSE_FILE="$target_compose"
    (( failed == 0 ))
}
