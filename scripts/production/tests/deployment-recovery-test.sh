#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-deploy-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
chmod 0700 "$test_root"
release="$test_root/releases/1111111111111111111111111111111111111111"
mkdir -p "$release/source/src/main/resources/db/migration" "$release/source/scripts/production/tests"
cp "$repo/compose.production.yml" "$release/source/compose.production.yml"
cp "$scripts/"*.sh "$release/source/scripts/production/"
cp "$scripts/tests/"*.sh "$release/source/scripts/production/tests/"
for helper in ensure-migration-privileges.sh migrate.sh; do
    printf '#!/usr/bin/env bash\necho "Immutable release helper must not run during tooling recovery." >&2\nexit 88\n' \
        > "$release/source/scripts/production/$helper"
done
for version in $(seq 1 23); do
    printf '%s\n' '-- test-only migration inventory' \
        > "$release/source/src/main/resources/db/migration/V${version}__test.sql"
done
printf '%s\n' '1111111111111111111111111111111111111111' > "$release/commit"
printf '%s\n' '1111111111111111111111111111111111111111' > "$release/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    printf 'sha256:%064d\n' 1 > "$release/$image.id"
done
find "$release" -type f -exec shasum -a 256 '{}' \; > "$test_root/release-before.sha256"

mkdir -p "$test_root/bin"
cat > "$test_root/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
all="$*"
printf '%s\n' "$all" >> "$PPE_FAKE_DOCKER_TRACE"
case "$all" in
    "context inspect "*) printf 'unix:///var/run/docker.sock\n'; exit 0 ;;
    "image inspect "*) image=${*: -1}; name=${image%%:*}; cat "$PPE_FAKE_RELEASE/$name.id"; exit 0 ;;
esac
if [[ "$all" = *"exec -T db sh /opt/ppe/db-admin.sh"* ]]; then
    query=$(cat)
    case "$query" in
        *"FROM information_schema.tables"*)
            if [[ -f "$PPE_FAKE_DB_STATE" ]]; then printf '1\n'; else printf '0\n'; fi
            ;;
        *"FROM ppe.flyway_schema_history"*)
            if [[ -f "$PPE_FAKE_DB_STATE" ]]; then
                read -r applied failed < "$PPE_FAKE_DB_STATE"
                printf '%s %s %s\n' "$applied" "$failed" "$applied"
            else
                printf '0 0 0\n'
            fi
            ;;
        *"SHOW GRANTS FOR"*) printf "USAGE ON *.*\n" ;;
    esac
    exit 0
fi
case "$all" in
    *"run --rm --no-deps migrate gradle"*)
        printf 'Flyway validation successful.\n'
        ;;
    *"run --rm --no-deps migrate"*)
        if [[ "${PPE_FAKE_MIGRATION_MODE:-success}" = partial ]]; then
            printf '3 1\n' > "$PPE_FAKE_DB_STATE"
            echo "Injected partial DDL and failed Flyway record." >&2
            exit 1
        fi
        printf '%s 0\n' "$PPE_FAKE_TARGET_MIGRATIONS" > "$PPE_FAKE_DB_STATE"
        ;;
    *"exec -T nginx nginx -t"*) ;;
    *"ps --status running -q nginx"*) ;;
    "compose "*) ;;
esac
exit 0
DOCKER
cat > "$test_root/bin/curl" <<'CURL'
#!/bin/sh
case " $* " in
    *" -w "*) printf '200' ;;
    *) printf '{"status":"ok"}' ;;
esac
CURL
chmod 0700 "$test_root/bin/docker" "$test_root/bin/curl"
dash=$(command -v dash) || { echo "dash is required for deployment recovery tests." >&2; exit 2; }
cat > "$test_root/bin/sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ -f "${1:-}" ]] && head -n 1 "$1" | grep -q 'bash'; then
    echo "Bash script incorrectly invoked through sh: $1" >&2
    exit 92
fi
exec "$PPE_TEST_DASH" "$@"
SH
chmod 0700 "$test_root/bin/sh"
export PPE_TEST_DASH="$dash"
export PATH="$test_root/bin:$PATH"
export PPE_FAKE_DOCKER_TRACE="$test_root/docker-trace"
export PPE_FAKE_RELEASE="$release"
export PPE_FAKE_TARGET_MIGRATIONS=23
export PPE_FAKE_MIGRATION_MODE=success

run_deploy() {
    local project=$1 point=${2:-}
    export PPE_PROJECT=$project
    export PPE_OPERATION_APPROVAL=local-test
    export PPE_MAINTENANCE_APPROVED=yes
    export PPE_SCHEMA_POLICY=initial
    export PPE_STATE_DIR="$test_root/$project/state"
    export PPE_OPERATION_DIR="$PPE_STATE_DIR"
    export PPE_SECRETS_DIR="$test_root/secrets"
    export PPE_TLS_DIR="$test_root/tls"
    export PPE_ACME_DIR="$test_root/acme"
    export PPE_PROXY_SUBNET=10.239.10.0/24
    export PPE_PROXY_IP=10.239.10.10
    export PPE_TRUSTED_PROXY_REGEX='10[.]239[.]10[.]10'
    export PPE_BIND_ADDRESS=127.0.0.1
    export PPE_HTTP_PORT=18180
    export PPE_HTTPS_PORT=18443
    export PPE_SMOKE_CA_FILE="$test_root/dummy-ca.pem"
    export PPE_COMPOSE_FILE="$release/source/compose.production.yml"
    export PPE_FAKE_DB_STATE="$test_root/$project/database.state"
    export PPE_DEPLOYMENT_TEST_FAIL_AT=$point
    mkdir -p "$PPE_STATE_DIR" "$PPE_SECRETS_DIR" "$PPE_TLS_DIR" "$PPE_ACME_DIR"
    : > "$PPE_SMOKE_CA_FILE"
    chmod 0700 "$PPE_STATE_DIR"
    bash "$scripts/deploy-release.sh" initial "$release"
}

for point in before-migration after-migration before-app before-nginx before-smoke before-publish; do
    project="ppe-sim-${point}"
    : > "$PPE_FAKE_DOCKER_TRACE"
    if run_deploy "$project" "$point" >"$test_root/$project.first.log" 2>&1; then
        echo "FAIL: failpoint $point unexpectedly succeeded" >&2
        exit 1
    fi
    [[ -f "$test_root/$project/state/deployment.state" ]]
    [[ ! -f "$test_root/$project/state/current-release" ]]
    if [[ "$point" = before-migration ]]; then
        [[ ! -f "$test_root/$project/database.state" ]]
        grep -qx 'format=2' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'mode=initial' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'previous=' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'policy=initial' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'phase=migration_started' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'migration_baseline=0' "$PPE_STATE_DIR/deployment.state"
        grep -qx 'target_migrations=23' "$PPE_STATE_DIR/deployment.state"
    else
        [[ -f "$test_root/$project/database.state" ]]
    fi
    if run_deploy "$project" "" >"$test_root/$project.resume.log" 2>&1; then
        [[ "$(cat "$test_root/$project/state/current-release")" = \
            '1111111111111111111111111111111111111111' ]]
        [[ "$(sed -n 's/^phase=//p' "$test_root/$project/state/deployment.state")" = published ]]
    else
        cat "$test_root/$project.resume.log" >&2
        echo "FAIL: same-release recovery did not complete after $point" >&2
        exit 1
    fi
    echo "PASS: same-release recovery after $point preserves DB state and publishes only after smoke"
    [[ "$(grep -c 'run --rm --no-deps migrate$' "$PPE_FAKE_DOCKER_TRACE")" = 1 ]]
done
echo "PASS: repaired operational tooling resumes an immutable old release under dash without running migration twice"

project=ppe-sim-other-release
if run_deploy "$project" before-migration > "$test_root/other-release.first.log" 2>&1; then
    echo "FAIL: setup failpoint unexpectedly succeeded" >&2; exit 1
fi
cp "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
other="$test_root/releases/2222222222222222222222222222222222222222"
mkdir "$other"
ln -s "$release/source" "$other/source"
printf '%s\n' '2222222222222222222222222222222222222222' > "$other/commit"
cp "$other/commit" "$other/READY"
for image in ppe-app ppe-tools ppe-db ppe-runner ppe-broker ppe-nginx ppe-backup; do
    cp "$release/$image.id" "$other/$image.id"
done
: > "$PPE_FAKE_DOCKER_TRACE"
if bash "$scripts/deploy-release.sh" initial "$other" > "$test_root/other-release.log" 2>&1; then
    echo "FAIL: incomplete deployment switched releases" >&2; exit 1
fi
grep -q 'refusing to switch' "$test_root/other-release.log"
cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
[[ ! -f "$PPE_FAKE_DB_STATE" ]]
if grep -Eq 'exec |run |up |stop |down ' "$PPE_FAKE_DOCKER_TRACE"; then
    echo "FAIL: refused release switch mutated services or database" >&2; exit 1
fi
echo "PASS: changing target releases is refused without modifying the recorded state or database"

: > "$PPE_FAKE_DOCKER_TRACE"
if PPE_OPERATION_APPROVAL=none bash "$scripts/deploy-release.sh" initial "$release" \
    > "$test_root/approval.log" 2>&1; then
    echo "FAIL: deployment without operation approval was accepted" >&2; exit 1
fi
[[ ! -s "$PPE_FAKE_DOCKER_TRACE" ]]
grep -q 'approval is required' "$test_root/approval.log"
if PPE_MAINTENANCE_APPROVED=no bash "$scripts/deploy-release.sh" initial "$release" \
    > "$test_root/maintenance.log" 2>&1; then
    echo "FAIL: deployment without maintenance approval was accepted" >&2; exit 1
fi
grep -q 'Approve a maintenance window' "$test_root/maintenance.log"
cmp -s "$PPE_STATE_DIR/deployment.state" "$test_root/state-before"
[[ ! -d "$PPE_STATE_DIR/deployment.lock" ]]
echo "PASS: operation and maintenance approval checks reject recovery without mutating the journal"

project=ppe-sim-mid-migration
export PPE_FAKE_MIGRATION_MODE=partial
if run_deploy "$project" after-migration >"$test_root/$project.first.log" 2>&1; then
    echo "FAIL: partial migration unexpectedly succeeded" >&2
    exit 1
fi
export PPE_FAKE_MIGRATION_MODE=success
if run_deploy "$project" "" >"$test_root/$project.resume.log" 2>&1; then
    echo "FAIL: partial Flyway history was retried automatically" >&2
    exit 1
fi
[[ "$(cat "$test_root/$project/database.state")" = '3 1' ]]
[[ ! -f "$test_root/$project/state/current-release" ]]
grep -q 'Flyway has a failed record; automatic migration retry is forbidden' \
    "$test_root/$project.resume.log" || {
        cat "$test_root/$project.resume.log" >&2
        echo "FAIL: partial Flyway history did not fail closed" >&2
        exit 1
    }
echo "PASS: partial migration remains intact and resume fails closed for manual recovery"

find "$release" -type f -exec shasum -a 256 '{}' \; > "$test_root/release-after.sha256"
cmp -s "$test_root/release-before.sha256" "$test_root/release-after.sha256"
echo "PASS: old release scripts, SQL, Compose, READY and image-ID records remain byte-for-byte unchanged"
echo "PASS: test harness used no Docker daemon resources, development data, or production secrets"
