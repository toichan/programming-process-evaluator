#!/usr/bin/env bash
set -euo pipefail
set +x
port=${PPE_HTTPS_PORT:-443}
tls=()
if [[ -n "${PPE_SMOKE_CA_FILE:-}" ]]; then tls=(--cacert "$PPE_SMOKE_CA_FILE"); fi
for role in student teacher; do
    host="$role.ppeval.net"
    health=$(curl -fsS --max-time 10 "${tls[@]}" --resolve "$host:$port:127.0.0.1" "https://$host:$port/health")
    [[ "$health" = '{"status":"ok"}' ]] || { echo "Health failed: $role" >&2; exit 1; }
    code=$(curl -sS --max-time 10 "${tls[@]}" --resolve "$host:$port:127.0.0.1" \
        -o /dev/null -w '%{http_code}' "https://$host:$port/$role/account/login")
    [[ "$code" = 200 ]] || { echo "Login landing failed: $role" >&2; exit 1; }
done
echo "Deployment smoke: TLS trust, DB health and both login portals passed."
