#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-migration-privilege-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
chmod 0700 "$test_root"
mkdir -m 0700 "$test_root/bin"
mkdir -m 0700 "$test_root/state"

cat > "$test_root/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
all="$*"
if [[ "$all" = *"exec -T db sh /opt/ppe/db-admin.sh --skip-column-names"* ]]; then
    query=$(cat)
    if [[ "$query" = *"SHOW GRANTS"* ]]; then
        if [[ -f "$PPE_TEST_REVOKED" ]]; then
            if grep -Fq 'GRANT ALL PRIVILEGES ON *.*' "$PPE_TEST_GRANTS"; then
                cat "$PPE_TEST_GRANTS"
            else
                printf 'GRANT USAGE ON *.* TO `ppe_migrate`@`%%`\n'
                printf 'GRANT ALL PRIVILEGES ON `ppe`.* TO `ppe_migrate`@`%%`\n'
            fi
        else
            cat "$PPE_TEST_GRANTS"
        fi
        exit 0
    fi
fi
if [[ "$all" = *"exec -T db sh /opt/ppe/db-admin.sh"* ]]; then
    query=$(cat)
    [[ "$query" = *"REVOKE SUPER"* ]] || exit 2
    : > "$PPE_TEST_REVOKED"
    exit 0
fi
echo "Unexpected Docker command in migration privilege test." >&2
exit 2
DOCKER
chmod 0700 "$test_root/bin/docker"

export PATH="$test_root/bin:$PATH"
export PPE_PROJECT=ppe-sim-migration-privilege
export PPE_STATE_DIR="$test_root/state"
export PPE_COMPOSE_FILE="$test_root/compose.yml"
export PPE_TEST_GRANTS="$test_root/grants"
export PPE_TEST_REVOKED="$test_root/revoked"

for quote in backtick single; do
    rm -f "$PPE_TEST_REVOKED"
    if [[ "$quote" = backtick ]]; then
        printf 'GRANT SUPER ON *.* TO `ppe_migrate`@`%%`\n' > "$PPE_TEST_GRANTS"
    else
        printf "GRANT SUPER ON *.* TO 'ppe_migrate'@'%%'\n" > "$PPE_TEST_GRANTS"
    fi
    bash "$scripts/ensure-migration-privileges.sh" >/dev/null
    [[ -f "$PPE_TEST_REVOKED" ]]
    echo "PASS: residual SUPER was detected and revoked from $quote-quoted MySQL grants"
done

rm -f "$PPE_TEST_REVOKED"
printf 'GRANT USAGE ON *.* TO `ppe_migrate`@`%%`\nGRANT ALL PRIVILEGES ON `ppe`.* TO `ppe_migrate`@`%%`\n' \
    > "$PPE_TEST_GRANTS"
bash "$scripts/ensure-migration-privileges.sh" >/dev/null
[[ ! -f "$PPE_TEST_REVOKED" ]]
echo "PASS: schema-scoped migration privileges do not trigger global cleanup"

printf 'GRANT ALL PRIVILEGES ON *.* TO `ppe_migrate`@`%%`\n' > "$PPE_TEST_GRANTS"
if bash "$scripts/ensure-migration-privileges.sh" >/dev/null 2>&1; then
    echo "FAIL: global ALL PRIVILEGES was accepted as a safe migration state" >&2
    exit 1
fi
echo "PASS: unmanageable global ALL PRIVILEGES fails closed for manual review"
