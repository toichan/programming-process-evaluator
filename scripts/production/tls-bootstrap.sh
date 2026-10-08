#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

[[ "$#" = 0 ]] || { echo "Usage: bash scripts/production/tls-bootstrap.sh" >&2; exit 2; }
[[ "${PPE_TLS_BOOTSTRAP_APPROVED:-}" = yes ]] || {
    echo "Explicit approval is required before requesting a public certificate." >&2
    exit 2
}
: "${PPE_ACME_EMAIL:?Set PPE_ACME_EMAIL to the certificate contact address}"
: "${PPE_TLS_DIR:?Set PPE_TLS_DIR}"
: "${PPE_ACME_DIR:?Set PPE_ACME_DIR}"
: "${PPE_CERTBOT_CONFIG_DIR:?Set PPE_CERTBOT_CONFIG_DIR}"
: "${PPE_CERTBOT_WORK_DIR:?Set PPE_CERTBOT_WORK_DIR}"
: "${PPE_CERTBOT_LOG_DIR:?Set PPE_CERTBOT_LOG_DIR}"

project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
scripts=$(cd "$(dirname "$0")" && pwd)
[[ -f "$compose" ]] || { echo "Production Compose file was not found." >&2; exit 2; }
[[ "$PPE_ACME_EMAIL" != *[[:space:]]* && "$PPE_ACME_EMAIL" = *@* ]] || {
    echo "A valid ACME notification address is required." >&2
    exit 2
}
for directory in "$PPE_TLS_DIR" "$PPE_ACME_DIR" "$PPE_CERTBOT_CONFIG_DIR" \
    "$PPE_CERTBOT_WORK_DIR" "$PPE_CERTBOT_LOG_DIR"; do
    [[ "$directory" = /* && ! -L "$directory" ]] || {
        echo "Certificate directories must be absolute, non-symlink paths." >&2
        exit 2
    }
done
[[ ! -e "$PPE_TLS_DIR/fullchain.pem" && ! -e "$PPE_TLS_DIR/privkey.pem" ]] || {
    echo "TLS material already exists; use the renewal workflow, not bootstrap." >&2
    exit 2
}
if [[ "${PPE_ACME_TEST_MODE:-}" = mock ]]; then
    [[ "${PPE_OPERATION_APPROVAL:-}" = local-test && "$project" =~ ^ppe-(sim|preparation)-[a-z0-9-]+$ ]] || {
        echo "Mock ACME mode is restricted to guarded local test projects." >&2
        exit 2
    }
else
    [[ "${PPE_BIND_ADDRESS:-127.0.0.1}" != 127.0.0.1 ]] || {
    echo "Public HTTP-01 validation requires an explicitly approved public bind address." >&2
    exit 2
    }
fi
install -d -m 0700 "$PPE_TLS_DIR" "$PPE_ACME_DIR" "$PPE_CERTBOT_CONFIG_DIR" \
    "$PPE_CERTBOT_WORK_DIR" "$PPE_CERTBOT_LOG_DIR"

dc() { docker compose -p "$project" -f "$compose" "$@"; }
running_nginx=$(dc ps --status running -q nginx)
if [[ -n "$running_nginx" ]]; then
    echo "Stop: HTTPS Nginx is already running; bootstrap will not disturb it." >&2
    exit 1
fi

bootstrap_start_attempted=0
cleanup() {
    local status=$?
    trap - EXIT
    if (( bootstrap_start_attempted )); then
        if ! dc --profile acme-bootstrap stop acme-bootstrap; then
            echo "Failed to stop ACME bootstrap; inspect the acme-bootstrap service before retrying." >&2
            if (( status == 0 )); then status=1; fi
        fi
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

bootstrap_start_attempted=1
dc --profile acme-bootstrap up -d --no-deps --wait --wait-timeout 30 acme-bootstrap
certbot_args=(
    certonly --non-interactive --agree-tos --no-eff-email
    --webroot --webroot-path "$PPE_ACME_DIR"
    --config-dir "$PPE_CERTBOT_CONFIG_DIR"
    --work-dir "$PPE_CERTBOT_WORK_DIR"
    --logs-dir "$PPE_CERTBOT_LOG_DIR"
    --cert-name ppeval --email "$PPE_ACME_EMAIL"
    -d student.ppeval.net -d teacher.ppeval.net
)
if [[ -n "${PPE_ACME_SERVER:-}" ]]; then
    certbot_args+=(--server "$PPE_ACME_SERVER")
fi
certbot "${certbot_args[@]}"

lineage="$PPE_CERTBOT_CONFIG_DIR/live/ppeval"
[[ -s "$lineage/fullchain.pem" && -s "$lineage/privkey.pem" ]] || {
    echo "ACME did not produce both certificate files." >&2
    exit 1
}
sh "$scripts/install-tls.sh" "$lineage/fullchain.pem" "$lineage/privkey.pem" "$PPE_TLS_DIR"
dc --profile acme-bootstrap stop acme-bootstrap
bootstrap_start_attempted=0
dc up -d --no-deps --wait --wait-timeout 60 nginx
dc exec -T nginx nginx -t
dc exec -T nginx nginx -s reload
echo "Initial certificate installed and HTTPS Nginx validated. Certificate values were not printed."
