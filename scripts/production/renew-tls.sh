#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

fail() { echo "$1" >&2; exit 1; }
[[ $# -le 2 ]] || fail "Usage: renew-tls.sh [ENV_FILE] [--check|--dry-run|--dry-run-deploy]"
environment=${1:-/var/lib/ppe/tls-operation.env}
mode=${2:-renew}
case "$mode" in renew|--check|--dry-run|--dry-run-deploy) ;; *) fail "Invalid TLS renewal mode." ;; esac
[[ -f "$environment" && ! -L "$environment" ]] || fail "TLS environment must be a regular, non-symlink file."
[[ -n "$(find "$environment" -prune -user "$(id -un)" -perm 0600 -print)" ]] \
    || fail "TLS environment must be owned by the executing user with mode 0600."

required=(PPE_PROJECT PPE_RELEASE PPE_DB_RELEASE PPE_DB_SOURCE_DIR PPE_COMPOSE_FILE
    PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR PPE_CERTBOT_CONFIG_DIR
    PPE_CERTBOT_WORK_DIR PPE_CERTBOT_LOG_DIR)
for name in "${required[@]}" PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT; do unset "$name"; done
while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" = \#* ]] && continue
    [[ "$line" =~ ^([A-Z_]+)=([a-zA-Z0-9_./:-]+)$ ]] || fail "Invalid TLS environment assignment."
    name=${BASH_REMATCH[1]}
    value=${BASH_REMATCH[2]}
    case "$name" in
        PPE_PROJECT|PPE_RELEASE|PPE_DB_RELEASE|PPE_DB_SOURCE_DIR|PPE_COMPOSE_FILE|PPE_SECRETS_DIR|PPE_TLS_DIR|PPE_ACME_DIR|PPE_CERTBOT_CONFIG_DIR|PPE_CERTBOT_WORK_DIR|PPE_CERTBOT_LOG_DIR|PPE_BIND_ADDRESS|PPE_HTTP_PORT|PPE_HTTPS_PORT) ;;
        *) fail "Unsupported TLS environment variable." ;;
    esac
    if printenv "$name" >/dev/null; then fail "Duplicate TLS environment variable."; fi
    export "$name=$value"
done < "$environment"
for name in "${required[@]}"; do
    value=$(printenv "$name") || fail "Required TLS environment variable is missing."
    [[ -n "$value" ]] || fail "Required TLS environment variable is missing."
done
[[ "$PPE_RELEASE" =~ ^[0-9a-f]{40}$ && "$PPE_DB_RELEASE" =~ ^[0-9a-f]{40}$ ]] \
    || fail "TLS Compose releases must be fixed commit SHAs."
[[ "$PPE_PROJECT" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "Invalid TLS Compose project."
for name in PPE_DB_SOURCE_DIR PPE_COMPOSE_FILE PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR \
    PPE_CERTBOT_CONFIG_DIR PPE_CERTBOT_WORK_DIR PPE_CERTBOT_LOG_DIR; do
    value=$(printenv "$name")
    [[ "$value" = /* && "$value" != *'/../'* && "$value" != *'/./'* \
        && "$value" != */.. && "$value" != */. && "$value" != / ]] \
        || fail "TLS paths must be absolute without traversal."
    if [[ "$name" = PPE_COMPOSE_FILE ]]; then
        [[ -f "$value" ]] || fail "TLS Compose file is missing."
    else
        [[ -d "$value" ]] || fail "A required TLS directory is missing."
    fi
done
[[ -s "$PPE_CERTBOT_CONFIG_DIR/renewal/ppeval.conf" ]] || fail "The ppeval renewal configuration is missing."
[[ -s "$PPE_TLS_DIR/fullchain.pem" && -s "$PPE_TLS_DIR/privkey.pem" ]] \
    || fail "The existing serving certificate pair is missing."
export DOCKER_HOST=unix:///var/run/docker.sock
unset DOCKER_CONTEXT
docker compose -p "$PPE_PROJECT" -f "$PPE_COMPOSE_FILE" config --quiet
[[ "$mode" != --check ]] || { echo "TLS renewal environment and Compose parsing passed; no renewal was run."; exit 0; }

scripts=$(cd "$(dirname "$0")" && pwd -P)
[[ "$scripts" =~ ^/[a-zA-Z0-9_./-]+$ ]] || fail "The fixed TLS operations path contains unsupported characters."
arguments=(renew --non-interactive --no-random-sleep-on-renew --cert-name ppeval
    --config-dir "$PPE_CERTBOT_CONFIG_DIR" --work-dir "$PPE_CERTBOT_WORK_DIR"
    --logs-dir "$PPE_CERTBOT_LOG_DIR"
    --deploy-hook "/bin/bash $scripts/tls-renew-hook.sh")
case "$mode" in
    --dry-run) arguments+=(--dry-run) ;;
    --dry-run-deploy) arguments+=(--dry-run --run-deploy-hooks) ;;
esac
exec certbot "${arguments[@]}"
