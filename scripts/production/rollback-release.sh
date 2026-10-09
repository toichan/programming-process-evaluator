#!/usr/bin/env bash
set -Eeuo pipefail
set +x
[[ "$#" = 1 ]] || { echo "Usage: bash rollback-release.sh ABSOLUTE_PREVIOUS_RELEASE_DIRECTORY" >&2; exit 1; }
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/operation-common.sh"
source "$scripts/deployment-state.sh"
source "$scripts/database-preflight.sh"
source "$scripts/sync-operation-config.sh"
[[ "${PPE_SCHEMA_POLICY:-}" = backward-compatible ]] || {
    echo "Rollback requires an explicit compatibility review of the CURRENT database." >&2
    exit 1
}

release=$1
verify_release "$release"
deployment_mode=rollback
deployment_release=$PPE_RELEASE
[[ -f "$PPE_STATE_DIR/current-release" ]] || { echo "Current release marker is missing." >&2; exit 1; }
deployment_previous=$(cat "$PPE_STATE_DIR/current-release")
active_before=$deployment_previous
deployment_phase=prepared
deployment_policy=backward-compatible
migration_baseline=0
target_migrations=$(find "$release/source/src/main/resources/db/migration" -maxdepth 1 \
    -type f -name 'V*__*.sql' -print | wc -l | tr -d ' ')
current_step=preflight

fresh_rollback=true
recovering_failed_update=false
already_active_rollback=false
if [[ -f "$state_file" ]]; then
    [[ "$(state_value format)" = 2 ]] || { echo "Unknown deployment state format; stop for manual review." >&2; exit 1; }
    old_mode=$(state_value mode)
    old_release=$(state_value release)
    old_phase=$(state_value phase)
    if [[ "$old_phase" = published ]]; then
        [[ "$active_before" = "$old_release" ]] || {
            echo "Published deployment state does not match current-release." >&2
            exit 1
        }
        if [[ "$old_mode" = rollback && "$deployment_release" = "$active_before" ]]; then
            already_active_rollback=true
        fi
    elif [[ "$old_mode" = rollback && "$old_release" = "$deployment_release" ]]; then
        deployment_previous=$(state_value previous)
        deployment_phase=$old_phase
        migration_baseline=$(state_value migration_baseline)
        target_migrations=$(state_value target_migrations)
        fresh_rollback=false
    elif [[ "$old_mode" = update && "$active_before" = "$(state_value previous)" \
        && "$deployment_release" = "$active_before" ]]; then
        echo "Recovering the recorded active release after an incomplete update."
        recovering_failed_update=true
    else
        echo "An incomplete operation belongs to another release; resolve it before rollback." >&2
        exit 1
    fi
fi

on_failure() {
    local status=$?
    if (( status != 0 )); then
        record_event "$current_step" failed "$status"
        printf 'Rollback stopped at stage=%s exit=%s; state is preserved in %s.\n' \
            "$current_step" "$status" "$state_file" >&2
    fi
}
trap on_failure ERR

dc config --quiet
verify_source_manifest "$release" "${PPE_SOURCE_MANIFEST_FILE:-$release/source.sha256}"
verify_existing_database
preflight_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
verify_operation_resource_paths
verify_tls_operation_wrapper
if [[ "$already_active_rollback" = true ]]; then
    verify_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
    verify_five_services
    bash "$scripts/smoke.sh"
    echo "This rollback release is already active and verified."
    exit 0
fi
active_context="$(dirname "$release")/$active_before"
if ! verify_operation_configs "$active_context" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}" >/dev/null 2>&1; then
    if [[ "$recovering_failed_update" != true || $(stage_index "$old_phase") -lt 90 ]] \
        || ! verify_operation_configs "$(dirname "$release")/$old_release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"; then
        echo "Operation references disagree with the recorded rollback source." >&2; exit 1
    fi
fi
if [[ "$recovering_failed_update" = true ]]; then
    [[ "$(state_value migration_attempted)" != yes ]] || {
        : "${PPE_ROLLBACK_COMPATIBILITY_REFERENCE:?Review partial/current schema compatibility before app rollback}"
    }
    cp "$state_file" "$PPE_STATE_DIR/failed-update-$(date -u +%Y%m%dT%H%M%SZ)-$$.state"
fi
: "${PPE_ROLLBACK_COMPATIBILITY_REFERENCE:?Pin a private reviewed CURRENT schema compatibility reference}"
[[ "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE" = /* && -f "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE" \
    && ! -L "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE" \
    && -z "$(find "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE" -prune \( ! -perm 0600 -o ! -user "$(id -u)" \) -print)" ]] || {
    echo "Rollback compatibility reference is missing or unsafe." >&2; exit 1;
}
reference_value() {
    awk -F '=' -v k="$1" '$1==k {n++; value=$2} END {if(n!=1) exit 1; print value}' "$PPE_ROLLBACK_COMPATIBILITY_REFERENCE"
}
current_history_hash=$(dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names <<'SQL' | openssl dgst -sha256 | awk '{print $NF}'
SELECT CONCAT(COALESCE(version,''),'|',script,'|',COALESCE(checksum,''),'|',success)
FROM ppe.flyway_schema_history ORDER BY installed_rank;
SQL
)
[[ "$(reference_value source_release)" = "$active_before" \
    && "$(reference_value target_release)" = "$deployment_release" \
    && "$(reference_value source_container)" = "$PPE_RECOVERY_DB_CONTAINER_ID" \
    && "$(reference_value history_hash)" = "$current_history_hash" \
    && "$(reference_value compatible)" = yes ]] || {
    echo "Rollback review does not match the CURRENT DB and selected release." >&2; exit 1;
}
if [[ -f "$state_file" ]]; then
    state_evidence="$PPE_STATE_DIR/state-before-rollback-$(source_hash "$state_file")"
    [[ ! -L "$state_evidence" ]] || { echo "Unsafe rollback evidence path." >&2; exit 1; }
    cp "$state_file" "$state_evidence"
fi
if [[ "$fresh_rollback" = true ]]; then
    write_state prepared
    record_event preflight started
fi

active=$(cat "$PPE_STATE_DIR/current-release")
if [[ "$active" != "$deployment_previous" ]]; then
    [[ "$active" = "$deployment_release" && $(stage_index "$deployment_phase") -ge 81 ]] || {
        echo "Active release changed outside this rollback journal; refusing to continue." >&2
        exit 1
    }
fi
[[ "$deployment_release" != "$active" || "$fresh_rollback" = false \
    || "$recovering_failed_update" = true ]] || {
    echo "The selected rollback release is already active and no interrupted rollback is recorded." >&2
    exit 1
}

run_failpoint() {
    local point=$1
    [[ "${PPE_DEPLOYMENT_TEST_FAIL_AT:-}" = "$point" ]] || return 0
    [[ "${PPE_OPERATION_APPROVAL:-}" = local-test && "$PPE_PROJECT" =~ ^ppe-(sim|preparation)-[a-z0-9-]+$ ]] || {
        echo "Deployment failpoints are restricted to guarded local test projects." >&2
        return 1
    }
    echo "Injected local rollback failure at $point." >&2
    return 97
}

run_stage() {
    local key=$1 complete="${1}_complete"
    shift
    if stage_complete "$complete"; then
        echo "Rollback stage already complete: $key"
        return 0
    fi
    current_step=$key
    write_state "${key}_started"
    record_event "$key" started
    run_failpoint "before-$key"
    "$@"
    run_failpoint "after-$key"
    write_state "$complete"
    record_event "$key" complete
}

stage_quiesce() { dc stop app python-runner; }
stage_broker() { dc up -d --no-deps --wait --wait-timeout 120 docker-broker; }
stage_app() { dc up -d --no-deps --wait --wait-timeout 100 python-runner app; }
stage_nginx() {
    dc up -d --no-deps --wait --wait-timeout 60 nginx
    dc exec -T nginx nginx -t
    dc exec -T nginx nginx -s reload
}
stage_smoke() { verify_five_services; verify_existing_database; bash "$scripts/smoke.sh"; }

run_stage quiesce stage_quiesce
run_stage broker stage_broker
run_stage app stage_app
run_stage nginx stage_nginx
run_stage smoke stage_smoke
if ! stage_complete published; then
    current_step=publish
    write_state publish_started
    record_event publish started
    run_failpoint before-publish
    sync_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
    verify_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
    publish_state "$deployment_release" backward-compatible
    write_state published
    record_event publish complete
fi
echo "Previous app release restored; DB schema and data were not rolled back or deleted."
