#!/usr/bin/env bash

state_file="$PPE_STATE_DIR/deployment.state"
event_file="$PPE_STATE_DIR/deployment-events.log"

state_value() {
    local key=$1
    sed -n "s/^${key}=//p" "$state_file" | head -n 1
}

write_state() {
    local next_phase=$1 temporary="$PPE_STATE_DIR/.deployment-state.$$"
    {
        printf 'format=2\nmode=%s\nrelease=%s\nprevious=%s\npolicy=%s\nphase=%s\n' \
            "$deployment_mode" "$deployment_release" "$deployment_previous" "$deployment_policy" "$next_phase"
        printf 'migration_baseline=%s\ntarget_migrations=%s\n' \
            "$migration_baseline" "$target_migrations"
        printf 'deployment_id=%s\nhistory_hash=%s\ninventory_hash=%s\nreceipt_hash=%s\nmigration_attempted=%s\nrecovery_reference_hash=%s\napps_restored=%s\n' \
            "${deployment_id:-}" "${baseline_history_hash:-}" "${baseline_inventory_hash:-}" \
            "${deployment_receipt_hash:-}" "${migration_attempted:-no}" "${recovery_reference_hash:-}" "${apps_restored:-no}"
        printf 'recovery_attempted=%s\n' "${recovery_attempted:-no}"
        printf 'updated_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$temporary"
    chmod 0600 "$temporary"
    mv -f "$temporary" "$state_file"
    sync
    deployment_phase=$next_phase
}

record_event() {
    local stage=$1 result=$2 code=${3:-0}
    printf '%s\tmode=%s\trelease=%s\tstage=%s\tresult=%s\texit=%s\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$deployment_mode" "$deployment_release" \
        "$stage" "$result" "$code" >> "$event_file"
    chmod 0600 "$event_file"
    sync
}

stage_index() {
    case "$1" in
        prepared) echo 0 ;;
        database_started) echo 10 ;;
        database_complete) echo 11 ;;
        quiesce_started) echo 20 ;;
        quiesce_complete) echo 21 ;;
        backup_started) echo 30 ;;
        backup_complete) echo 31 ;;
        migration_started) echo 40 ;;
        migration_complete) echo 41 ;;
        broker_started) echo 50 ;;
        broker_complete) echo 51 ;;
        app_started) echo 60 ;;
        app_complete) echo 61 ;;
        nginx_started) echo 70 ;;
        nginx_complete) echo 71 ;;
        smoke_started) echo 80 ;;
        smoke_complete) echo 81 ;;
        publish_started) echo 90 ;;
        published) echo 100 ;;
        *) return 1 ;;
    esac
}

stage_complete() {
    local complete=$1 current_index complete_index
    current_index=$(stage_index "$deployment_phase")
    complete_index=$(stage_index "$complete")
    (( current_index >= complete_index ))
}

write_release_file() {
    local path=$1 value=$2 temporary
    temporary="$PPE_STATE_DIR/.$(basename "$path").$$"
    printf '%s\n' "$value" > "$temporary"
    chmod 0600 "$temporary"
    mv -f "$temporary" "$path"
    sync
}

publish_state() {
    local release=$1 policy=${2:-${PPE_SCHEMA_POLICY:-initial}}
    local current=""
    [[ ! -f "$PPE_STATE_DIR/current-release" ]] || current=$(cat "$PPE_STATE_DIR/current-release")
    if [[ "$current" != "$release" && -n "$current" ]]; then
        write_release_file "$PPE_STATE_DIR/previous-release" "$current"
    elif [[ -n "$deployment_previous" && ! -f "$PPE_STATE_DIR/previous-release" ]]; then
        write_release_file "$PPE_STATE_DIR/previous-release" "$deployment_previous"
    fi
    write_release_file "$PPE_STATE_DIR/schema-policy" "$policy"
    write_release_file "$PPE_STATE_DIR/current-release" "$release"
}
