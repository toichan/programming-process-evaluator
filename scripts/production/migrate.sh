#!/bin/sh
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi
set -euo pipefail
set +x
source "$(dirname "$0")/database-lock.sh"
acquire_database_lock
: "${PPE_SECRETS_DIR:?Set PPE_SECRETS_DIR}"
project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
case "$project" in ''|*[!a-z0-9_-]*) echo "Invalid Compose project." >&2; exit 1 ;; esac
umask 077
operation_dir=${PPE_OPERATION_DIR:-deploy/runtime/operations/$project}
mkdir -p -m 700 "$operation_dir"
lock="$operation_dir/migration.lock"
mkdir "$lock" 2>/dev/null || { echo "Another migration is active or a stale lock needs review." >&2; exit 1; }
release_lock() {
    rmdir "$lock" 2>/dev/null || {
        echo "Migration lock could not be removed; inspect it before retrying." >&2
        return 1
    }
}
trap release_lock EXIT
trap 'exit 1' HUP INT TERM
dc() { docker compose -p "$project" -f "$compose" "$@"; }
bash "$(dirname "$0")/ensure-migration-privileges.sh"
revoke() {
    bash "$(dirname "$0")/ensure-migration-privileges.sh"
}
finish() {
    status=$?
    trap - EXIT
    if ! revoke; then
        echo "SUPER revocation failed; revoke manually before further operations." >&2
        status=1
    fi
    release_lock || status=1
    exit "$status"
}
trap finish EXIT
printf "GRANT SUPER ON *.* TO 'ppe_migrate'@'%%';\n" |
    dc exec -T db sh /opt/ppe/db-admin.sh
dc run --rm --no-deps migrate
