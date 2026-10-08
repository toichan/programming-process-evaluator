#!/usr/bin/env bash
set -Eeuo pipefail
set +x
[[ "$#" = 1 ]] || { echo "Usage: bash rollback-release.sh ABSOLUTE_PREVIOUS_RELEASE_DIRECTORY" >&2; exit 1; }
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/operation-common.sh"
source "$scripts/deployment-state.sh"
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
            echo "This rollback release is already active."
            exit 0
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
stage_smoke() { bash "$scripts/smoke.sh"; }

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
    publish_state "$deployment_release" backward-compatible
    write_state published
    record_event publish complete
fi
echo "Previous app release restored; DB schema and data were not rolled back or deleted."
