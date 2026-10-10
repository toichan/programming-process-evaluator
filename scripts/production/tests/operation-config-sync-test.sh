#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-operation-config-sync-test.XXXXXX")
trap 'status=$?; if ((status != 0)); then printf "FAIL: operation config sync test exit %s\n" "$status" >&2; fi; rm -rf "$test_root"; exit "$status"' EXIT
chmod 0700 "$test_root"
source "$scripts/sync-operation-config.sh"
export PPE_HELPER="$scripts/sync-operation-config.sh"

old_sha=1111111111111111111111111111111111111111
new_sha=2222222222222222222222222222222222222222
third_sha=3333333333333333333333333333333333333333
secret_marker=synthetic-secret-must-not-appear-in-logs

make_release() {
    local sha=$1 directory=$2
    mkdir -p "$directory/source"
    printf '%s\n' "$sha" > "$directory/commit"
    printf '%s\n' "$sha" > "$directory/READY"
    printf '%s\n' 'services: {}' > "$directory/source/compose.production.yml"
}

setup_case() {
    local name=$1 base="$test_root/$1"
    mkdir -p "$base/state" "$base/paths/secrets" "$base/paths/tls" "$base/paths/acme" \
        "$base/paths/backup" "$base/paths/certbot/config" "$base/paths/certbot/work" \
        "$base/paths/certbot/log" "$base/releases/$old_sha" "$base/releases/$new_sha" \
        "$base/releases/$third_sha" "$base/db-old" "$base/db-new" "$base/db-third"
    chmod 0700 "$base/state"
    make_release "$old_sha" "$base/releases/$old_sha"
    make_release "$new_sha" "$base/releases/$new_sha"
    make_release "$third_sha" "$base/releases/$third_sha"
    export PPE_PROJECT=ppe-sim-operation-config
    export PPE_STATE_DIR="$base/state"
    export PPE_BACKUP_OPERATION_ENV="$base/backup-operation.env"
    export PPE_TLS_OPERATION_ENV="$base/tls-operation.env"
    export TEST_BASE="$base"
    release="$base/releases/$old_sha"
    deployment_release=$old_sha
    unset deployment_id
    export PPE_DB_SOURCE_DIR="$base/db-old"
    write_configs "$old_sha" "$old_sha" "$base/db-old"
}

write_configs() {
    local release_sha=$1 db_sha=$2 db_source=$3
    local release_dir="$TEST_BASE/releases/$release_sha"
    cat > "$PPE_BACKUP_OPERATION_ENV" <<EOF
PPE_PROJECT=$PPE_PROJECT
PPE_RELEASE=$release_sha
PPE_DB_RELEASE=$db_sha
PPE_DB_SOURCE_DIR=$db_source
PPE_COMPOSE_FILE=$release_dir/source/compose.production.yml
PPE_BACKUP_IMAGE=ppe-backup:$release_sha
PPE_SECRETS_DIR=$TEST_BASE/paths/secrets
PPE_TLS_DIR=$TEST_BASE/paths/tls
PPE_ACME_DIR=$TEST_BASE/paths/acme
PPE_STATE_DIR=$PPE_STATE_DIR
PPE_BACKUP_DIR=$TEST_BASE/paths/backup
PPE_BACKUP_RECIPIENT_FILE=$TEST_BASE/paths/recipient
PPE_BACKUP_SNS_TOPIC_ARN=arn:aws:sns:ap-northeast-1:123456789012:preserve-this
CUSTOM_UNRELATED=\$(touch $TEST_BASE/injected)  # $secret_marker
EOF
    cat > "$PPE_TLS_OPERATION_ENV" <<EOF
PPE_PROJECT=$PPE_PROJECT
PPE_RELEASE=$release_sha
PPE_DB_RELEASE=$db_sha
PPE_DB_SOURCE_DIR=$db_source
PPE_COMPOSE_FILE=$release_dir/source/compose.production.yml
PPE_SECRETS_DIR=$TEST_BASE/paths/secrets
PPE_TLS_DIR=$TEST_BASE/paths/tls
PPE_ACME_DIR=$TEST_BASE/paths/acme
PPE_CERTBOT_CONFIG_DIR=$TEST_BASE/paths/certbot/config
PPE_CERTBOT_WORK_DIR=$TEST_BASE/paths/certbot/work
PPE_CERTBOT_LOG_DIR=$TEST_BASE/paths/certbot/log
PPE_BIND_ADDRESS=0.0.0.0
PPE_HTTP_PORT=80
PPE_HTTPS_PORT=443
EOF
    : > "$TEST_BASE/paths/recipient"
    chmod 0600 "$PPE_BACKUP_OPERATION_ENV" "$PPE_TLS_OPERATION_ENV"
}

assert_config_pin() {
    local file=$1 release_sha=$2 db_sha=$3 db_source=$4
    local release_dir="$TEST_BASE/releases/$release_sha"
    grep -Fqx "PPE_RELEASE=$release_sha" "$file"
    grep -Fqx "PPE_COMPOSE_FILE=$release_dir/source/compose.production.yml" "$file"
    grep -Fqx "PPE_DB_RELEASE=$db_sha" "$file"
    grep -Fqx "PPE_DB_SOURCE_DIR=$db_source" "$file"
}

setup_case normal
cp "$PPE_BACKUP_OPERATION_ENV" "$TEST_BASE/backup-before"
grep -E '^(PPE_BACKUP_SNS_TOPIC_ARN|CUSTOM_UNRELATED)=' "$PPE_BACKUP_OPERATION_ENV" \
    > "$TEST_BASE/unrelated-before"
grep -F 'PPE_SECRETS_DIR=' "$PPE_BACKUP_OPERATION_ENV" > "$TEST_BASE/backup-secrets-before"
grep -F 'PPE_SECRETS_DIR=' "$PPE_TLS_OPERATION_ENV" > "$TEST_BASE/tls-secrets-before"
preflight_operation_configs "$TEST_BASE/releases/$old_sha" "$TEST_BASE/db-old" "$old_sha" \
    > "$TEST_BASE/preflight.log" 2>&1
verify_operation_configs "$TEST_BASE/releases/$old_sha" "$TEST_BASE/db-old" "$old_sha" \
    > "$TEST_BASE/verify-before.log" 2>&1
sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/sync.log" 2>&1
assert_config_pin "$PPE_BACKUP_OPERATION_ENV" "$new_sha" "$new_sha" "$TEST_BASE/db-new"
assert_config_pin "$PPE_TLS_OPERATION_ENV" "$new_sha" "$new_sha" "$TEST_BASE/db-new"
verify_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/verify-after.log" 2>&1
grep -E '^(PPE_BACKUP_SNS_TOPIC_ARN|CUSTOM_UNRELATED)=' "$PPE_BACKUP_OPERATION_ENV" \
    > "$TEST_BASE/unrelated-after"
cmp -s "$TEST_BASE/unrelated-before" "$TEST_BASE/unrelated-after"
grep -F 'PPE_SECRETS_DIR=' "$PPE_BACKUP_OPERATION_ENV" > "$TEST_BASE/backup-secrets-after"
grep -F 'PPE_SECRETS_DIR=' "$PPE_TLS_OPERATION_ENV" > "$TEST_BASE/tls-secrets-after"
cmp -s "$TEST_BASE/backup-secrets-before" "$TEST_BASE/backup-secrets-after"
cmp -s "$TEST_BASE/tls-secrets-before" "$TEST_BASE/tls-secrets-after"
[[ ! -e "$TEST_BASE/injected" ]]
journal_count=0
for journal in "$PPE_STATE_DIR"/operation-config-sync/*; do
    [[ ! -d "$journal" ]] || ((journal_count += 1))
done
[[ "$journal_count" = 1 ]]
grep -qx completed "$PPE_STATE_DIR"/operation-config-sync/*/status
grep -q 'Operation-config sync completed' "$TEST_BASE/sync.log"
grep -q "$secret_marker" "$PPE_BACKUP_OPERATION_ENV"
if grep -q "$secret_marker" "$TEST_BASE/preflight.log" "$TEST_BASE/sync.log"; then
    echo "An unrelated config value appeared in logs." >&2
    exit 1
fi
echo "PASS: preflight and sync pin both configs; unrelated backup assignments are byte-preserved and not sourced"

write_configs "$new_sha" "$new_sha" "$TEST_BASE/db-new"
sync_operation_configs "$TEST_BASE/releases/$old_sha" "$TEST_BASE/db-old" "$old_sha" \
    > "$TEST_BASE/rollback.log" 2>&1
assert_config_pin "$PPE_BACKUP_OPERATION_ENV" "$old_sha" "$old_sha" "$TEST_BASE/db-old"
assert_config_pin "$PPE_TLS_OPERATION_ENV" "$old_sha" "$old_sha" "$TEST_BASE/db-old"
echo "PASS: sync to a prior release restores its app and database pins"

(
    unset PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT
    load_operation_network_config
    [[ "$PPE_BIND_ADDRESS:$PPE_HTTP_PORT:$PPE_HTTPS_PORT" = 0.0.0.0:80:443 ]]
    [[ ! -e "$TEST_BASE/injected" ]]
)
echo "PASS: missing caller/backup network settings inherit reviewed TLS public bind without sourcing config"
for scenario in caller-conflict backup-conflict missing-bind duplicate-port invalid-port unsafe-bind; do
    setup_case "network-$scenario"
    (
        unset PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT
        case "$scenario" in
            caller-conflict) export PPE_BIND_ADDRESS=127.0.0.1 ;;
            backup-conflict) printf 'PPE_HTTPS_PORT=8443\n' >> "$PPE_BACKUP_OPERATION_ENV" ;;
            missing-bind) sed '/^PPE_BIND_ADDRESS=/d' "$PPE_TLS_OPERATION_ENV" > "$TEST_BASE/changed"; mv "$TEST_BASE/changed" "$PPE_TLS_OPERATION_ENV" ;;
            duplicate-port) printf 'PPE_HTTP_PORT=80\n' >> "$PPE_TLS_OPERATION_ENV" ;;
            invalid-port) sed 's/PPE_HTTPS_PORT=443/PPE_HTTPS_PORT=65536/' "$PPE_TLS_OPERATION_ENV" > "$TEST_BASE/changed"; mv "$TEST_BASE/changed" "$PPE_TLS_OPERATION_ENV" ;;
            unsafe-bind) printf 'PPE_BIND_ADDRESS=$(touch /tmp/injected)\n' >> "$PPE_TLS_OPERATION_ENV" ;;
        esac
        chmod 0600 "$PPE_TLS_OPERATION_ENV"
        if load_operation_network_config > "$TEST_BASE/network.log" 2>&1; then
            echo "Accepted unsafe network config: $scenario" >&2; exit 1
        fi
    )
    echo "PASS: network config rejects $scenario before Compose lifecycle actions"
done

setup_case tls_hook_lock
mkdir "$TEST_BASE/paths/.ppe-tls-renew-lock"
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/hook-lock.log" 2>&1; then
    echo "Preflight accepted an active TLS deploy hook." >&2
    exit 1
fi
grep -q 'TLS renewal deploy hook is active' "$TEST_BASE/hook-lock.log"
echo "PASS: active TLS renewal hook lock blocks config preflight"

setup_case shared_flock
if command -v flock >/dev/null 2>&1; then
    exec 8>>"$TEST_BASE/paths/.ppe-tls-operation.lock"
    flock -n 8
    preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
        > "$TEST_BASE/shared-flock.log" 2>&1
    sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
        > "$TEST_BASE/shared-flock-sync.log" 2>&1
    exec 8>&-
    echo "PASS: helper works while caller holds the shared FD8 TLS operation lock"
fi

setup_case deployment_id
deployment_id=deployment-test-0001
sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/deployment-id.log" 2>&1
[[ -d "$PPE_STATE_DIR/operation-config-sync/$deployment_id" ]]
verify_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" >/dev/null
echo "PASS: deployment identifier names the private journal and verification confirms the installed pins"

setup_case missing
sed '/^PPE_TLS_DIR=/d' "$PPE_BACKUP_OPERATION_ENV" > "$TEST_BASE/bad"
mv "$TEST_BASE/bad" "$PPE_BACKUP_OPERATION_ENV"
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/missing.log" 2>&1; then
    echo "Preflight accepted a missing required variable." >&2
    exit 1
fi
unset PPE_TLS_OPERATION_ENV
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/missing-path.log" 2>&1; then
    echo "Preflight accepted an unpinned TLS operation environment path." >&2
    exit 1
fi
setup_case unsafe_mode
chmod 0644 "$PPE_BACKUP_OPERATION_ENV"
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/mode.log" 2>&1; then
    echo "Preflight accepted an unsafe operation environment mode." >&2
    exit 1
fi
setup_case symlink
mv "$PPE_TLS_OPERATION_ENV" "$TEST_BASE/tls-real"
ln -s "$TEST_BASE/tls-real" "$PPE_TLS_OPERATION_ENV"
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/symlink.log" 2>&1; then
    echo "Preflight accepted a symlinked operation environment." >&2
    exit 1
fi
echo "PASS: missing required variable, group/world-readable file, and symlink are refused"

setup_case second_move
source_backup_before=$(cat "$PPE_BACKUP_OPERATION_ENV")
source_tls_before=$(cat "$PPE_TLS_OPERATION_ENV")
_OPCFG_MOVE_COUNT=0
_operation_config_move() {
    ((_OPCFG_MOVE_COUNT += 1))
    [[ "$_OPCFG_MOVE_COUNT" != 2 ]] || return 71
    mv -f "$1" "$2"
}
if sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/second-move.log" 2>&1; then
    echo "Sync accepted an injected second rename failure." >&2
    exit 1
fi
[[ "$(cat "$PPE_BACKUP_OPERATION_ENV")" = "$source_backup_before" ]]
[[ "$(cat "$PPE_TLS_OPERATION_ENV")" = "$source_tls_before" ]]
grep -q 'backup environment was restored' "$TEST_BASE/second-move.log"
echo "PASS: second rename failure restores the first file and returns failure"

setup_case post_rename_failure
source_backup_before=$(cat "$PPE_BACKUP_OPERATION_ENV")
source_tls_before=$(cat "$PPE_TLS_OPERATION_ENV")
_OPCFG_MOVE_COUNT=0
_operation_config_move() {
    ((_OPCFG_MOVE_COUNT += 1))
    mv -f "$1" "$2" || return 1
    [[ "$_OPCFG_MOVE_COUNT" != 2 ]] || return 73
}
if sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/post-rename.log" 2>&1; then exit 1; fi
[[ "$(cat "$PPE_BACKUP_OPERATION_ENV")" = "$source_backup_before" \
    && "$(cat "$PPE_TLS_OPERATION_ENV")" = "$source_tls_before" ]]
grep -qx rolled_back "$PPE_STATE_DIR"/operation-config-sync/*/status
echo "PASS: failure after successful TLS rename restores both generations and remains nonzero"

setup_case recovery_failure
_OPCFG_MOVE_COUNT=0
_operation_config_move() {
    ((_OPCFG_MOVE_COUNT += 1))
    if [[ "$_OPCFG_MOVE_COUNT" = 2 || "$_OPCFG_MOVE_COUNT" = 3 || "$_OPCFG_MOVE_COUNT" = 4 ]]; then return 72; fi
    mv -f "$1" "$2"
}
if sync_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/recovery-failure.log" 2>&1; then
    echo "Sync accepted an injected rollback-recovery failure." >&2
    exit 1
fi
grep -Fqx "PPE_RELEASE=$new_sha" "$PPE_BACKUP_OPERATION_ENV"
grep -q 'restoration both failed' "$TEST_BASE/recovery-failure.log"
grep -qx recovery_failed "$PPE_STATE_DIR"/operation-config-sync/*/status
if preflight_operation_configs "$TEST_BASE/releases/$third_sha" "$TEST_BASE/db-third" "$third_sha" \
    > "$TEST_BASE/recovery-refusal.log" 2>&1; then
    echo "Preflight ignored a failed recovery journal." >&2
    exit 1
fi
grep -q 'operator review' "$TEST_BASE/recovery-refusal.log"
echo "PASS: failed restoration is reported, evidence remains, and later preflight refuses"

setup_case interruption
set +e
(
    trap - ERR
    ulimit -c 0
    bash -c '
        source "$PPE_HELPER"
        _OPCFG_MOVE_COUNT=0
        _operation_config_move() {
            ((_OPCFG_MOVE_COUNT += 1))
            if [[ "$_OPCFG_MOVE_COUNT" = 2 ]]; then exit 99; fi
            mv -f "$1" "$2"
        }
        sync_operation_configs "$1" "$2" "$3"
    ' bash "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
        > "$TEST_BASE/interrupted-child.log" 2>&1
)
child_status=$?
set -e
[[ "$child_status" != 0 ]]
if preflight_operation_configs "$TEST_BASE/releases/$new_sha" "$TEST_BASE/db-new" "$new_sha" \
    > "$TEST_BASE/interrupted-restart.log" 2>&1; then
    echo "Preflight ignored an interrupted sync." >&2
    exit 1
fi
grep -q 'unfinished operation-config sync' "$TEST_BASE/interrupted-restart.log"
echo "PASS: restart detects and refuses an interrupted two-file update"

while IFS= read -r -d '' log; do
    if grep -Fq "$secret_marker" "$log"; then
        echo "A synthetic secret marker appeared in command logs." >&2
        exit 1
    fi
done < <(find "$test_root" -type f -name '*.log' -print0)
echo "PASS: no config value was written to logs"
