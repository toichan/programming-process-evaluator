#!/usr/bin/env bash
set -euo pipefail
set +x
root=$(cd "$(dirname "$0")/../../.." && pwd)
temporary=$(mktemp -d)
trap 'rm -f "$temporary/config.json" "$temporary/missing.log"; rmdir "$temporary"' EXIT
umask 077
cd "$root"
set -a
source containers/production/backup-operation.env.example
set +a
docker compose --env-file /dev/null -f compose.production.yml config --format json > "$temporary/config.json"
jq -e '
 .services.nginx.volumes |
 any(.target=="/etc/nginx/tls" and .source=="/var/lib/ppe/tls-private/current" and .read_only==true)
' "$temporary/config.json" >/dev/null
jq -e '
 .services.nginx.volumes |
 any(.target=="/var/www/acme" and .source=="/var/lib/ppe/acme" and .read_only==true)
' "$temporary/config.json" >/dev/null
before=$(jq -S '.services.db' "$temporary/config.json")
PPE_TLS_DIR=/tmp/ppe-dummy-tls PPE_ACME_DIR=/tmp/ppe-dummy-acme \
 docker compose --env-file /dev/null -f compose.production.yml config --format json > "$temporary/config.json"
test "$before" = "$(jq -S '.services.db' "$temporary/config.json")"
for variable in PPE_TLS_DIR PPE_ACME_DIR; do
 if (
  unset "$variable"
  docker compose --env-file /dev/null -f compose.production.yml config --services
 ) > "$temporary/missing.log" 2>&1; then
  echo "Missing Compose variable was accepted: $variable" >&2
  exit 1
 fi
 grep -q "$variable" "$temporary/missing.log"
done
printf 'PASS: TLS mount, ACME mount, unchanged DB recipe, missing TLS refusal, missing ACME refusal (5 checks)\n'
