#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
dash=$(command -v dash) || { echo "dash is required to test Ubuntu /bin/sh compatibility." >&2; exit 2; }
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-shell-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
mkdir -m 0700 "$test_root/bin"
if ! command -v flock >/dev/null 2>&1; then
    cat > "$test_root/bin/flock" <<'FLOCK'
#!/bin/sh
[ "$1" = -n ] && [ "$2" = 9 ] || exit 2
exit 0
FLOCK
    chmod 0700 "$test_root/bin/flock"
fi

while IFS= read -r file; do
    read -r shebang < "$file"
    case "$shebang" in
        '#!/usr/bin/env bash') bash -n "$file" ;;
        '#!/bin/sh') "$dash" -n "$file" ;;
        *) echo "Unknown script interpreter: $file" >&2; exit 1 ;;
    esac
    if [[ "$shebang" = '#!/usr/bin/env bash' ]]; then
        name=$(basename "$file")
        if grep -En "(^|[[:space:]])sh[[:space:]]+.*[/\"]${name//./\\.}([\"[:space:]]|$)" \
            "$scripts/"*.sh "$scripts/tests/"*.sh; then
            echo "Bash script is called through sh: $name" >&2
            exit 1
        fi
    fi
done < <(find "$scripts" -type f -name '*.sh' -print | sort)
echo "PASS: all production/test scripts parse with their declared interpreter; no explicit sh call targets a Bash script"

cat > "$test_root/bin/sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ -f "${1:-}" ]] && head -n 1 "$1" | grep -q 'bash'; then
    echo "Bash script was incorrectly passed to sh: $1" >&2
    exit 92
fi
exec "$PPE_TEST_DASH" "$@"
SH
cat > "$test_root/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
    *"exec -T db sh /opt/ppe/db-admin.sh"*)
        query=$(cat)
        case "$query" in
            *"SHOW GRANTS"*)
                if [[ -f "$PPE_TEST_SUPER" ]]; then
                    printf 'GRANT SUPER ON *.* TO `ppe_migrate`@`%%`\n'
                fi
                printf 'GRANT ALL PRIVILEGES ON `ppe`.* TO `ppe_migrate`@`%%`\n'
                ;;
            *"GRANT SUPER"*) printf 'grant\n' >> "$PPE_TEST_TRACE"; : > "$PPE_TEST_SUPER" ;;
            *"REVOKE SUPER"*)
                printf 'revoke\n' >> "$PPE_TEST_TRACE"
                if [[ "${PPE_TEST_REVOKE_FAIL:-0}" = 1 ]]; then exit 18; fi
                rm -f "$PPE_TEST_SUPER"
                ;;
            *) echo "Unexpected migration SQL in shell test." >&2; exit 2 ;;
        esac
        ;;
    *"run --rm --no-deps migrate")
        [[ -f "$PPE_TEST_SUPER" ]]
        printf 'migration\n' >> "$PPE_TEST_TRACE"
        exit "${PPE_TEST_MIGRATION_EXIT:-0}"
        ;;
    *) echo "Unexpected Docker command in shell test." >&2; exit 2 ;;
esac
DOCKER
chmod 0700 "$test_root/bin/sh" "$test_root/bin/docker"
export PATH="$test_root/bin:$PATH" PPE_TEST_DASH="$dash"
export PPE_PROJECT=ppe-sim-shell PPE_COMPOSE_FILE="$test_root/compose.yml"
export PPE_SECRETS_DIR="$test_root/unused-secrets" PPE_OPERATION_DIR="$test_root/operations"
export PPE_STATE_DIR="$PPE_OPERATION_DIR"
mkdir -m 0700 "$PPE_STATE_DIR"
export PPE_TEST_SUPER="$test_root/super" PPE_TEST_TRACE="$test_root/trace"

for result in 0 17; do
    : > "$PPE_TEST_TRACE"
    status=0
    PPE_TEST_MIGRATION_EXIT=$result sh "$scripts/migrate.sh" > "$test_root/migrate.log" 2>&1 || status=$?
    if [[ "$status" != "$result" ]]; then
        cat "$test_root/migrate.log" >&2
        echo "Unexpected migration exit status: $status (expected $result)" >&2
        exit 1
    fi
    [[ "$(cat "$PPE_TEST_TRACE")" = $'grant\nmigration\nrevoke' ]]
    [[ ! -e "$PPE_TEST_SUPER" && ! -d "$PPE_OPERATION_DIR/migration.lock" ]]
done
echo "PASS: dash migration wrapper calls Bash cleanup, grants/revokes SUPER, preserves success/failure exits and releases its lock"

mkdir "$PPE_OPERATION_DIR/migration.lock"
: > "$PPE_TEST_TRACE"
if sh "$scripts/migrate.sh" > "$test_root/locked.log" 2>&1; then
    echo "A concurrent migration was allowed." >&2; exit 1
fi
[[ ! -s "$PPE_TEST_TRACE" && -d "$PPE_OPERATION_DIR/migration.lock" ]]
rmdir "$PPE_OPERATION_DIR/migration.lock"
echo "PASS: existing migration lock prevents any database operation and is not removed"

: > "$PPE_TEST_TRACE"
status=0
PPE_TEST_REVOKE_FAIL=1 sh "$scripts/migrate.sh" > "$test_root/revoke.log" 2>&1 || status=$?
[[ "$status" = 1 && -f "$PPE_TEST_SUPER" && ! -d "$PPE_OPERATION_DIR/migration.lock" ]]
grep -q 'SUPER revocation failed' "$test_root/revoke.log"
rm -f "$PPE_TEST_SUPER"
echo "PASS: failed SUPER revocation is reported and never returned as success"
