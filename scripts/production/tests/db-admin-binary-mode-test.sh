#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

repo=$(cd "$(dirname "$0")/../../.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-db-admin-binary-test.XXXXXX")
container="ppe-db-admin-binary-$$"
cleanup() {
    docker rm -f "$container" >/dev/null 2>&1 || true
    rm -f "$test_root/output/arguments" "$test_root/bin/mysql" "$test_root/secrets/db_root_password"
    rmdir "$test_root/output" "$test_root/bin" "$test_root/secrets" "$test_root" 2>/dev/null || true
}
trap cleanup EXIT
chmod 0700 "$test_root"
mkdir -m 0700 "$test_root/bin" "$test_root/secrets" "$test_root/output"
printf 'synthetic-only-password\n' > "$test_root/secrets/db_root_password"
cat > "$test_root/bin/mysql" <<'MYSQL'
#!/bin/sh
printf '%s\n' "$@" > /out/arguments
cat >/dev/null
MYSQL
chmod 0700 "$test_root/bin/mysql"

docker run --rm --name "$container" --network none --read-only --user 0:0 \
    --env "TEST_OUTPUT_UID=$(id -u)" --env "TEST_OUTPUT_GID=$(id -g)" \
    --tmpfs /tmp:rw,noexec,nosuid,size=1m \
    --mount "type=bind,src=$test_root/secrets,dst=/run/secrets,readonly" \
    --mount "type=bind,src=$test_root/bin,dst=/test-bin,readonly" \
    --mount "type=bind,src=$test_root/output,dst=/out" \
    --mount "type=bind,src=$repo/containers/production/db-admin.sh,dst=/test/db-admin.sh,readonly" \
    --entrypoint sh "${PPE_DB_ADMIN_TEST_IMAGE:-mysql:8.0.44}" \
    -c 'PATH="/test-bin:$PATH" sh /test/db-admin.sh --execute "SELECT 1" &&
        chown "$TEST_OUTPUT_UID:$TEST_OUTPUT_GID" /out/arguments'

grep -q -- '--batch' "$test_root/output/arguments"
grep -q -- '--binary-mode' "$test_root/output/arguments"
echo "PASS: db-admin invokes the MySQL client in noninteractive binary mode"
