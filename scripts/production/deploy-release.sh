#!/usr/bin/env bash
set -Eeuo pipefail
set +x
[[ "$#" = 2 && ( "$1" = initial || "$1" = update ) ]] || {
    echo "Usage: bash deploy-release.sh initial|update ABSOLUTE_RELEASE_DIRECTORY" >&2
    exit 1
}
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/operation-common.sh"
source "$scripts/deployment-state.sh"
source "$scripts/migration-preflight.sh"
source "$scripts/sync-operation-config.sh"
source "$scripts/backup-common.sh"

deployment_mode=$1
release=$2
verify_release "$release"
export PPE_DB_SOURCE_DIR="$release/source"
deployment_release=$PPE_RELEASE
deployment_policy=${PPE_SCHEMA_POLICY:-initial}
deployment_previous=""
migration_baseline=-1
target_migrations=$(find "$release/source/src/main/resources/db/migration" -maxdepth 1 \
    -type f -name 'V*__*.sql' -print | wc -l | tr -d ' ')
deployment_phase=prepared
current_step=preflight
deployment_id=""
baseline_history_hash=""
baseline_inventory_hash=""
deployment_receipt_hash=""
recovery_reference_hash=""
migration_attempted=no
quiesced_this_run=no
apps_restored=no
recovery_attempted=no

on_failure() {
    local status=$?
    if (( status != 0 )); then
        trap - ERR
        record_event "$current_step" failed "$status"
        if [[ "$deployment_mode" = update && "$quiesced_this_run" = yes \
            && "$migration_attempted" = no ]] && (( $(stage_index "$deployment_phase") <= 40 )); then
            if inspect_migration_inventory && [[ "$migration_history_hash" = "$baseline_history_hash" ]]; then
                recovery_attempted=yes
                write_state "$deployment_phase"
                if restore_previous_app; then
                    apps_restored=yes
                    write_state "$deployment_phase"
                    record_event recovery complete
                else
                    record_event recovery failed 1
                    echo "Old application recovery failed; maintenance and evidence must be retained." >&2
                fi
            else
                record_event recovery failed 1
                echo "DB baseline changed; old application recovery was not attempted." >&2
            fi
        fi
        printf 'Deployment stopped at stage=%s exit=%s; state is preserved in %s.\n' \
            "$current_step" "$status" "$state_file" >&2
        exit "$status"
    fi
}
trap on_failure ERR

dc config --quiet

database_table_count() {
    printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe';\n" |
        dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names
}

source "$scripts/database-preflight.sh"

history_counts() {
    local tables
    tables=$(database_table_count)
    if [[ "$tables" = 0 ]]; then
        printf '0 0 0\n'
        return
    fi
    dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names <<'SQL'
SELECT
  COALESCE(SUM(CASE WHEN success = 1 AND version IS NOT NULL THEN 1 ELSE 0 END), 0),
  COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0),
  COUNT(*)
FROM ppe.flyway_schema_history;
SQL
}

validate_release_migrations() {
    local output counts applied failed total
    counts=$(history_counts)
    read -r applied failed total <<< "$counts"
    applied=${applied:-0}; failed=${failed:-0}; total=${total:-0}
    if (( failed != 0 )); then
        echo "Flyway contains failed migration history; stop for reviewed manual recovery." >&2
        return 1
    fi
    if (( total == 0 )); then
        [[ "$applied" = 0 ]] || { echo "Invalid empty Flyway history." >&2; return 1; }
        return 0
    fi
    output=$(mktemp "$PPE_STATE_DIR/.flyway-info.XXXXXX")
    if ! dc run --rm --no-deps migrate gradle --offline --no-daemon \
        '-Pflyway.ignoreMigrationPatterns=*:pending' flywayValidate flywayInfo > "$output" 2>&1; then
        rm -f "$output"
        echo "Flyway validation failed; no migration was retried." >&2
        return 1
    fi
    if grep -Eiq 'validation failed|failed migration' "$output"; then
        rm -f "$output"
        echo "Flyway reports failed or pending migrations; inspect history before recovery." >&2
        return 1
    fi
    rm -f "$output"
    [[ "$applied" -le "$target_migrations" ]] || {
        echo "Database schema is newer than the selected release." >&2
        return 1
    }
}

run_failpoint() {
    local point=$1
    [[ "${PPE_DEPLOYMENT_TEST_FAIL_AT:-}" = "$point" ]] || return 0
    [[ "${PPE_OPERATION_APPROVAL:-}" = local-test && "$PPE_PROJECT" =~ ^ppe-(sim|preparation)-[a-z0-9-]+$ ]] || {
        echo "Deployment failpoints are restricted to guarded local test projects." >&2
        return 1
    }
    echo "Injected local deployment failure at $point." >&2
    return 97
}

run_stage() {
    local key=$1 complete="${1}_complete"
    shift
    if stage_complete "$complete"; then
        echo "Deployment stage already complete: $key"
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

stage_database() {
    if [[ "$deployment_mode" = initial ]]; then
        [[ "$(database_table_count)" = 0 ]] || {
            echo "Initial deployment requires an empty database; no data was changed." >&2
            return 1
        }
        migration_baseline=0
        write_state database_started
    elif (( migration_baseline < 0 )); then
        local counts failed total
        counts=$(history_counts)
        read -r migration_baseline failed total <<< "$counts"
        migration_baseline=${migration_baseline:-0}
        [[ "${failed:-0}" = 0 ]] || { echo "Existing failed Flyway history requires manual review." >&2; return 1; }
        write_state database_started
    fi
}

stage_quiesce() {
    quiesced_this_run=yes
    dc stop app python-runner
}

backup_context() {
    [[ "$(source_hash "$PPE_BACKUP_RECOVERY_REFERENCE")" = "$recovery_reference_hash" ]] || {
        echo "Reviewed recovery reference changed during deployment." >&2; return 1;
    }
    export PPE_BACKUP_REQUIRE_REMOTE=yes
    export PPE_BACKUP_RECEIPT_FILE="$PPE_STATE_DIR/deployment-backup.receipt"
    export PPE_BACKUP_RELEASE="$deployment_previous"
    export PPE_BACKUP_BASELINE="$migration_baseline"
    export PPE_BACKUP_DEPLOYMENT_ID="$deployment_id"
    export PPE_BACKUP_SOURCE_RELEASE="$deployment_previous" PPE_BACKUP_TARGET_RELEASE="$deployment_release"
    export PPE_BACKUP_SOURCE_CONTAINER="$PPE_RECOVERY_DB_CONTAINER_ID"
    export PPE_BACKUP_SOURCE_VOLUME="${PPE_PROJECT}_database"
    export PPE_BACKUP_SOURCE_VOLUME_CREATED="$PPE_RECOVERY_DB_VOLUME_CREATED_AT"
    export PPE_BACKUP_HISTORY_HASH="$baseline_history_hash"
    export PPE_BACKUP_INVENTORY_HASH="$baseline_inventory_hash"
    export PPE_BACKUP_RECOVERY_REFERENCE_HASH="$recovery_reference_hash"
    export PPE_BACKUP_RECOVERY_AGE_RECIPIENT="$(receipt_value "$PPE_BACKUP_RECOVERY_REFERENCE" age_recipient)"
}

verify_deployment_backup() {
    backup_context
    [[ -n "$deployment_id" && -f "$PPE_BACKUP_RECEIPT_FILE" \
        && "$deployment_receipt_hash" =~ ^[a-f0-9]{64}$ \
        && "$(source_hash "$PPE_BACKUP_RECEIPT_FILE")" = "$deployment_receipt_hash" ]] || {
        echo "Recorded deployment receipt hash is missing or changed." >&2; return 1;
    }
    bash "$scripts/verify-backup-receipt.sh" "$PPE_BACKUP_RECEIPT_FILE"
}

stage_backup() {
    backup_context
    [[ ! -e "$PPE_BACKUP_RECEIPT_FILE" ]] || {
        echo "An earlier receipt exists for this attempt; inspect it before retrying backup." >&2; return 1;
    }
    bash "$scripts/backup.sh"
    deployment_receipt_hash=$(source_hash "$PPE_BACKUP_RECEIPT_FILE")
    bash "$scripts/verify-backup-receipt.sh" "$PPE_BACKUP_RECEIPT_FILE"
    mkdir -m 700 "$PPE_STATE_DIR/backup-$deployment_id"
    cp "$PPE_BACKUP_RECEIPT_FILE" "$PPE_STATE_DIR/backup-$deployment_id/receipt"
    printf '%s\n' "$deployment_receipt_hash" > "$PPE_STATE_DIR/backup-$deployment_id/receipt.sha256"
}

stage_migration() {
    local counts applied failed total
    if [[ "$deployment_mode" = update ]]; then
        verify_existing_database
        verify_deployment_backup
    fi
    inspect_migration_inventory
    counts=$(history_counts)
    read -r applied failed total <<< "$counts"
    applied=${applied:-0}; failed=${failed:-0}; total=${total:-0}
    if (( failed != 0 )); then
        echo "Flyway has a failed record; automatic migration retry is forbidden." >&2
        return 1
    fi
    if (( total > 0 )); then
        validate_release_migrations
        counts=$(history_counts)
        read -r applied failed total <<< "$counts"
        applied=${applied:-0}; failed=${failed:-0}; total=${total:-0}
    fi
    if (( applied == target_migrations && migration_pending_count == 0 )); then
        echo "All release migrations are already applied and checksum-validated."
        return 0
    fi
    [[ "$migration_attempted" = no ]] || {
        echo "A migration was attempted without reaching the exact target; retry/repair requires manual review." >&2
        return 1
    }
    if [[ "$deployment_mode" = update ]]; then
        [[ "$migration_history_hash" = "$baseline_history_hash" \
            && "$migration_inventory_hash" = "$baseline_inventory_hash" ]] || {
            echo "Migration history or pending inventory changed after preflight." >&2; return 1;
        }
    fi
    if (( applied != migration_baseline )); then
        echo "Flyway history is neither the recorded baseline nor the complete target; stop for manual recovery." >&2
        return 1
    fi
    # Repairable host tooling must still use the verified target release's Compose/images.
    bash "$scripts/ensure-migration-privileges.sh"
    migration_attempted=yes
    write_state migration_started
    sh "$scripts/migrate.sh"
    bash "$scripts/ensure-migration-privileges.sh"
    validate_release_migrations
    inspect_migration_inventory
    counts=$(history_counts)
    read -r applied failed total <<< "$counts"
    applied=${applied:-0}; failed=${failed:-0}; total=${total:-0}
    (( failed == 0 && applied == target_migrations )) || {
        echo "Migration did not reach the exact release version; deployment stopped." >&2
        return 1
    }
}

stage_broker() {
    dc up -d --no-deps --wait --wait-timeout 120 docker-broker
}

stage_app() {
    dc up -d --no-deps --wait --wait-timeout 100 python-runner app
}

stage_nginx() {
    dc up -d --no-deps --wait --wait-timeout 60 nginx
    dc exec -T nginx nginx -t
    dc exec -T nginx nginx -s reload
}

stage_smoke() {
    verify_five_services
    if [[ "$deployment_mode" = update ]]; then verify_existing_database; fi
    bash "$scripts/smoke.sh"
}

stage_publish() {
    run_failpoint before-publish
    if [[ "$deployment_mode" = update ]]; then
        sync_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
        verify_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"
    fi
    if [[ "$deployment_mode" = update ]]; then
        cp "$state_file" "$PPE_STATE_DIR/backup-$deployment_id/deployment.state"
    fi
    publish_state "$deployment_release" "$deployment_policy"
    write_state published
    record_event publish complete
}

resuming=false
if [[ -f "$state_file" && "$(state_value phase)" != published ]]; then
    [[ "$(state_value format)" = 2 ]] || { echo "Unknown deployment state format; stop for manual review." >&2; exit 1; }
    [[ "$(state_value mode)" = "$deployment_mode" && "$(state_value release)" = "$deployment_release" ]] || {
        echo "An incomplete deployment belongs to another mode or release; refusing to switch." >&2
        exit 1
    }
    deployment_previous=$(state_value previous)
    deployment_phase=$(state_value phase)
    saved_policy=$(state_value policy)
    if [[ "$deployment_mode" = update ]]; then
        if [[ "${PPE_SCHEMA_POLICY:-}" && "${PPE_SCHEMA_POLICY}" != "$saved_policy" ]]; then
            echo "The migration policy differs from the recorded deployment." >&2
            exit 1
        fi
        deployment_policy=$saved_policy
    fi
    migration_baseline=$(state_value migration_baseline)
    target_migrations=$(state_value target_migrations)
    deployment_id=$(state_value deployment_id)
    baseline_history_hash=$(state_value history_hash)
    baseline_inventory_hash=$(state_value inventory_hash)
    deployment_receipt_hash=$(state_value receipt_hash)
    recovery_reference_hash=$(state_value recovery_reference_hash)
    migration_attempted=$(state_value migration_attempted)
    migration_attempted=${migration_attempted:-no}
    apps_restored=$(state_value apps_restored)
    apps_restored=${apps_restored:-no}
    recovery_attempted=$(state_value recovery_attempted)
    recovery_attempted=${recovery_attempted:-no}
    if [[ "$apps_restored" = yes || "$recovery_attempted" = yes ]]; then
        [[ "${PPE_RESTART_RECOVERED_UPDATE:-}" = yes && "$deployment_mode" = update ]] || {
            echo "Old app was restored; explicitly approve a NEW attempt and backup with PPE_RESTART_RECOVERED_UPDATE=yes." >&2; exit 1;
        }
        cp "$state_file" "$PPE_STATE_DIR/recovered-$deployment_id.state"
        deployment_phase=prepared
        migration_baseline=-1
        deployment_receipt_hash=""
        apps_restored=no
        recovery_attempted=no
    fi
    [[ "$target_migrations" =~ ^[0-9]+$ && "$migration_baseline" =~ ^-?[0-9]+$ ]] || {
        echo "Deployment state is incomplete; stop for manual review." >&2
        exit 1
    }
    if [[ "$deployment_mode" = initial ]]; then
        [[ -z "$deployment_previous" ]] || { echo "Initial deployment state has an unexpected previous release." >&2; exit 1; }
        if [[ -f "$PPE_STATE_DIR/current-release" ]]; then
            [[ "$(cat "$PPE_STATE_DIR/current-release")" = "$deployment_release" ]] &&
                stage_complete smoke_complete || {
                    echo "Initial deployment state conflicts with current-release." >&2
                    exit 1
                }
        fi
    else
        [[ -f "$PPE_STATE_DIR/current-release" ]] || { echo "Previous release marker is missing." >&2; exit 1; }
        active=$(cat "$PPE_STATE_DIR/current-release")
        [[ "$active" = "$deployment_previous" || ( "$active" = "$deployment_release" && $(stage_index "$deployment_phase") -ge 81 ) ]] || {
            echo "Active release changed outside this recorded update; refusing to resume." >&2
            exit 1
        }
    fi
    if [[ "$(state_value apps_restored)" = yes || "$(state_value recovery_attempted)" = yes ]]; then
        resuming=false
    else
        resuming=true
    fi
else
    if [[ -f "$state_file" ]]; then
        [[ "$(state_value format)" = 2 && -f "$PPE_STATE_DIR/current-release" \
            && "$(cat "$PPE_STATE_DIR/current-release")" = "$(state_value release)" ]] || {
            echo "Published deployment state does not match current-release." >&2
            exit 1
        }
        if [[ "$(state_value mode)" = "$deployment_mode" && "$(state_value release)" = "$deployment_release" ]]; then
            if [[ "$deployment_mode" = update ]]; then
                verify_existing_database || exit 1
                verify_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}" || exit 1
                verify_operation_resource_paths || exit 1
                verify_five_services || exit 1
                bash "$scripts/smoke.sh" || exit 1
            fi
            echo "This exact release is already deployed."
            exit 0
        fi
    fi
    if [[ "$deployment_mode" = initial ]]; then
        [[ ! -f "$PPE_STATE_DIR/current-release" ]] || {
            echo "An initial release is already recorded." >&2
            exit 1
        }
    else
        [[ -f "$PPE_STATE_DIR/current-release" ]] || { echo "No previous release is recorded." >&2; exit 1; }
        deployment_previous=$(cat "$PPE_STATE_DIR/current-release")
        [[ "$deployment_previous" != "$deployment_release" ]] || { echo "Release is already active." >&2; exit 1; }
        case "${PPE_SCHEMA_POLICY:-}" in
            backward-compatible|requires-downtime) ;;
            *) echo "Review migration compatibility; set an explicit schema policy." >&2; exit 1 ;;
        esac
        deployment_policy=$PPE_SCHEMA_POLICY
        current_step=backup-preflight
    fi
fi

if [[ "$deployment_mode" = update && "$deployment_policy" != backward-compatible \
    && "$deployment_policy" != requires-downtime ]]; then
    echo "An explicit migration compatibility policy is required to resume this update." >&2
    exit 1
fi

if [[ "$deployment_mode" = update ]]; then
    current_step=database-update-preflight
    if ! verify_existing_database; then
        exit 1
    fi
    verify_source_manifest "$release" "${PPE_SOURCE_MANIFEST_FILE:-$release/source.sha256}" || exit 1
    preflight_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}" || exit 1
    verify_operation_resource_paths || exit 1
    previous_context="$(dirname "$release")/$deployment_previous"
    if ! verify_operation_configs "$previous_context" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}" >/dev/null 2>&1; then
        if (( $(stage_index "$deployment_phase") < 90 )) \
            || ! verify_operation_configs "$release" "$PPE_DB_SOURCE_DIR" "${PPE_DB_RELEASE:-$deployment_release}"; then
            echo "Operation settings do not match the recorded active/publishing release." >&2; exit 1
        fi
    fi
    verify_tls_operation_wrapper || exit 1
    verify_previous_release || exit 1
    load_backup_config || exit 1
    validate_release_migrations || exit 1
    inspect_migration_inventory || exit 1
    [[ "${PPE_BACKUP_RECOVERY_REFERENCE:-}" = /* && -f "$PPE_BACKUP_RECOVERY_REFERENCE" \
        && ! -L "$PPE_BACKUP_RECOVERY_REFERENCE" \
        && -z "$(find "$PPE_BACKUP_RECOVERY_REFERENCE" -prune \( ! -perm 0600 -o ! -user "$(id -u)" \) -print)" ]] || {
        echo "Pin a private reviewed offline decryption/restore reference before updating." >&2; exit 1;
    }
    [[ "$(receipt_value "$PPE_BACKUP_RECOVERY_REFERENCE" age_recipient)" = "$PPE_BACKUP_AGE_RECIPIENT" \
        && "$(receipt_value "$PPE_BACKUP_RECOVERY_REFERENCE" restore_verified)" = yes \
        && -n "$(receipt_value "$PPE_BACKUP_RECOVERY_REFERENCE" custody_reference)" ]] || {
        echo "Offline recovery reference does not confirm the configured age identity and restore rehearsal." >&2; exit 1;
    }
    if [[ "$resuming" = false ]]; then
        deployment_id=$(openssl rand -hex 16)
        baseline_history_hash=$migration_history_hash
        baseline_inventory_hash=$migration_inventory_hash
        recovery_reference_hash=$(source_hash "$PPE_BACKUP_RECOVERY_REFERENCE")
        counts=$(history_counts)
        read -r migration_baseline failed total <<< "$counts"
        if [[ -f "$PPE_STATE_DIR/deployment-backup.receipt" ]]; then
            old_receipt_hash=$(source_hash "$PPE_STATE_DIR/deployment-backup.receipt")
            [[ ! -e "$PPE_STATE_DIR/receipt-$old_receipt_hash" ]] || {
                echo "Receipt archive collision; stop for manual review." >&2; exit 1;
            }
            mv "$PPE_STATE_DIR/deployment-backup.receipt" "$PPE_STATE_DIR/receipt-$old_receipt_hash"
        fi
    else
        [[ "$deployment_id" =~ ^[a-f0-9]{32}$ \
            && "$recovery_reference_hash" = "$(source_hash "$PPE_BACKUP_RECOVERY_REFERENCE")" \
            && "$baseline_inventory_hash" = "$migration_inventory_hash" ]] || {
            echo "Incomplete update lacks matching attempt/recovery evidence; manual review is required." >&2; exit 1;
        }
        if ! stage_complete migration_complete && [[ "$migration_attempted" = yes ]] \
            && (( migration_pending_count != 0 )); then
            echo "Previous migration attempt is incomplete; no automatic retry or old-app recovery." >&2; exit 1
        fi
    fi
    if [[ "$resuming" = true ]] && stage_complete backup_complete; then
        current_step=backup-receipt-preflight
        if ! verify_deployment_backup; then
            exit 1
        fi
    fi
fi

if [[ "$resuming" = false ]]; then
    write_state prepared
    record_event preflight started
fi

if [[ "$deployment_mode" = initial && "$resuming" = true ]] \
    && stage_complete database_complete; then
    current_step=database-recovery-preflight
    if ! verify_existing_database; then
        exit 1
    fi
elif [[ "$deployment_mode" = initial ]]; then
    dc up -d --no-deps --wait --wait-timeout 120 db
fi
run_stage database stage_database
if [[ "$deployment_mode" = update ]]; then
    run_stage quiesce stage_quiesce
    run_stage backup stage_backup
fi
run_stage migration stage_migration
if [[ "$resuming" = true ]] && (( $(stage_index "$deployment_phase") >= 40 )); then
    counts=$(history_counts)
    read -r applied failed total <<< "$counts"
    applied=${applied:-0}; failed=${failed:-0}; total=${total:-0}
    if (( applied == target_migrations && failed == 0 )); then
        validate_release_migrations
    elif [[ "$deployment_phase" != migration_started || "$applied" != "$migration_baseline" || "$failed" != 0 ]]; then
        echo "Interrupted migration is neither at baseline nor at the complete target; stop for manual recovery." >&2
        exit 1
    fi
fi
run_stage broker stage_broker
run_stage app stage_app
run_stage nginx stage_nginx
run_stage smoke stage_smoke
if ! stage_complete published; then
    current_step=publish
    write_state publish_started
    record_event publish started
    stage_publish
fi
echo "Release deployed and verified; database volume retained: $PPE_RELEASE"
