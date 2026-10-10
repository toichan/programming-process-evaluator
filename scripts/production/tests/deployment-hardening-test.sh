#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
[[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
    echo "Hardening tests require a local Unix Docker endpoint." >&2; exit 1;
}
docker info >/dev/null
image=${PPE_HARDENING_TEST_IMAGE:-mcr.microsoft.com/playwright:v1.51.1-noble}
docker image inspect "$image" ppe-db:local ppe-backup:local >/dev/null
output=${PPE_HARDENING_TEST_OUTPUT_DIR:-}
temporary=no
identity_directory=""
completed=no
if [[ -z "$output" ]]; then output=$(mktemp -d); temporary=yes; fi
[[ "$output" = /* && -d "$output" && ! -L "$output" \
    && -z "$(find "$output" -prune \( ! -perm 0700 -o ! -user "$(id -u)" \) -print)" ]] || {
    echo "Test output must be an owned private directory." >&2; exit 1;
}
cleanup() {
    local status=$?
    trap - EXIT
    if [[ "$completed" != yes && "$status" = 0 ]]; then status=1; fi
    if [[ -n "$identity_directory" ]]; then
        rm -f "$identity_directory/identity" "$identity_directory/recipient" || status=1
        rmdir "$identity_directory" || status=1
    fi
    if [[ "$temporary" = yes ]]; then
        find "$output" -type f -delete || status=1
        find "$output" -depth -type d -exec rmdir '{}' \; || status=1
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
user_arguments=(--user 0:0)
if [[ -n "${PPE_HARDENING_TEST_IMAGE:-}" ]]; then
    user_arguments=(--user "$(id -u):$(id -g)")
fi
docker run --rm --pull never --network none --read-only "${user_arguments[@]}" \
    --tmpfs /tmp:rw,exec,nosuid,nodev,size=128m \
    --mount "type=bind,source=$repo,target=/work,readonly" \
    --mount "type=bind,source=$output,target=/output" \
    -w /work --entrypoint /bin/bash "$image" -c '
set -Eeuo pipefail
for suite in source-manifest-test migration-preflight-test operation-config-sync-test \
    deployment-state-test deployment-recovery-test backup-test database-lock-test \
    migration-privilege-cleanup-test shell-invocation-test tls-workflow-test \
    restore-test backup-systemd-test; do
    code=0
    bash "scripts/production/tests/$suite.sh" > "/output/$suite.log" 2>&1 || code=$?
    if (( code != 0 )); then
        echo "$suite FAIL exit=$code"
        tail -n 25 "/output/$suite.log"
        exit "$code"
    fi
    passed=$(grep -c PASS "/output/$suite.log" || true)
    skipped=$(grep -c "^SKIP:" "/output/$suite.log" || true)
    echo "$suite PASS exit=0 assertion-groups=$passed skipped=$skipped"
done'
for suite in deployment-db-config-hash-test backup-operation-config-test age-recipient-test \
    db-admin-binary-mode-test backup-roundtrip-test nginx-startup-test; do
    code=0
    if [[ "$suite" = age-recipient-test ]]; then
        command -v age >/dev/null
        command -v age-keygen >/dev/null
        identity_directory=$(mktemp -d "$output/.synthetic-recipient.XXXXXX")
        age-keygen -o "$identity_directory/identity" >/dev/null 2>&1
        age-keygen -y "$identity_directory/identity" > "$identity_directory/recipient"
        chmod 0600 "$identity_directory/identity" "$identity_directory/recipient"
        bash "$scripts/tests/$suite.sh" "$identity_directory/recipient" \
            "$identity_directory/identity" > "$output/$suite.log" 2>&1 || code=$?
    else
        bash "$scripts/tests/$suite.sh" > "$output/$suite.log" 2>&1 || code=$?
    fi
    if (( code != 0 )); then
        echo "$suite FAIL exit=$code"
        tail -n 25 "$output/$suite.log"
        exit "$code"
    fi
    passed=$(grep -c PASS "$output/$suite.log" || true)
    skipped=$(grep -c '^SKIP:' "$output/$suite.log" || true)
    echo "$suite PASS exit=0 assertion-groups=$passed skipped=$skipped"
    if [[ -n "$identity_directory" ]]; then
        rm -f "$identity_directory/identity" "$identity_directory/recipient"
        rmdir "$identity_directory"
        identity_directory=""
    fi
done
completed=yes
echo "Deployment hardening regression completed; no AWS or production access."
