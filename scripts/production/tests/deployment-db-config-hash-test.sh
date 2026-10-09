#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

repo=$(cd "$(dirname "$0")/../../.." && pwd)
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "SKIP: real DB configuration hash regression requires Docker Compose (no daemon needed)."
    exit 0
fi
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-db-config-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
old_sha=1111111111111111111111111111111111111111
new_sha=2222222222222222222222222222222222222222
old_source="$test_root/releases/$old_sha/source"
new_source="$test_root/releases/$new_sha/source"
mkdir -p "$old_source" "$new_source" "$test_root/secrets" "$test_root/tls" "$test_root/acme"
cp "$repo/compose.production.yml" "$old_source/compose.production.yml"
cp "$repo/compose.production.yml" "$new_source/compose.production.yml"
export PPE_SECRETS_DIR="$test_root/secrets"
export PPE_TLS_DIR="$test_root/tls" PPE_ACME_DIR="$test_root/acme"
for secret in db-password db-root-password db-migration-password gemini-api-key student-credential-key runner-token; do
    : > "$PPE_SECRETS_DIR/$secret"
done
unset PPE_DB_RELEASE PPE_DB_SOURCE_DIR
export PPE_RELEASE=$old_sha
baseline=$(docker compose -p ppe-sim-db-config -f "$old_source/compose.production.yml" config --hash db)
export PPE_RELEASE=$new_sha PPE_DB_RELEASE=$old_sha PPE_DB_SOURCE_DIR="$new_source"
unfixed=$(docker compose -p ppe-sim-db-config -f "$new_source/compose.production.yml" config --hash db)
export PPE_DB_SOURCE_DIR="$old_source"
pinned=$(docker compose -p ppe-sim-db-config -f "$new_source/compose.production.yml" config --hash db)
rendered=$(docker compose -p ppe-sim-db-config -f "$new_source/compose.production.yml" config db)
grep -Fq "context: $old_source" <<< "$rendered"
grep -Fq "image: ppe-db:$old_sha" <<< "$rendered"
[[ "$baseline" =~ ^db\ [0-9a-f]{64}$ && "$pinned" = "$baseline" ]] || {
    echo "FAIL: pinned DB image/source must preserve the old Compose hash." >&2
    printf 'baseline=%s\npinned=%s\ntarget-source=%s\n' "$baseline" "$pinned" "$unfixed" >&2
    exit 1
}
echo "PASS: real Compose DB hash is identical across app release paths with the old DB image and source pin"
if [[ "$unfixed" = "$baseline" ]]; then
    echo "NOTE: installed Compose excludes build context from the service hash; rendered DB context is verified separately"
else
    echo "PASS: target-source build metadata would change this Compose version's DB hash"
fi
echo "PASS: only Compose version/config were invoked; no containers, images or volumes were changed"
