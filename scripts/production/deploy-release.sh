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

deployment_mode=$1
release=$2
verify_release "$release"
deployment_release=$PPE_RELEASE
deployment_policy=${PPE_SCHEMA_POLICY:-initial}
deployment_previous=""
migration_baseline=-1
target_migrations=$(find "$release/source/src/main/resources/db/migration" -maxdepth 1 \
    -type f -name 'V*__*.sql' -print | wc -l | tr -d ' ')
deployment_phase=prepared
current_step=preflight

on_failure() {
    local status=$?
    if (( status != 0 )); then
        record_event "$current_step" failed "$status"
        printf 'Deployment stopped at stage=%s exit=%s; state is preserved in %s.\n' \
            "$current_step" "$status" "$state_file" >&2
    fi
}
trap on_failure ERR

dc config --quiet

database_table_count() {
    printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe';\n" |
        dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names
}

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
    dc stop app python-runner
}

stage_backup() {
    bash "$scripts/backup.sh"
}

stage_migration() {
    local counts applied failed total
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
    if (( applied == target_migrations )); then
        echo "All release migrations are already applied and checksum-validated."
        return 0
    fi
    if (( applied != migration_baseline )); then
        echo "Flyway history is neither the recorded baseline nor the complete target; stop for manual recovery." >&2
        return 1
    fi
    sh "$release/source/scripts/production/ensure-migration-privileges.sh"
    sh "$release/source/scripts/production/migrate.sh"
    sh "$release/source/scripts/production/ensure-migration-privileges.sh"
    validate_release_migrations
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
    bash "$scripts/smoke.sh"
}

stage_publish() {
    run_failpoint before-publish
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
    resuming=true
else
    if [[ -f "$state_file" ]]; then
        [[ "$(state_value format)" = 2 && -f "$PPE_STATE_DIR/current-release" \
            && "$(cat "$PPE_STATE_DIR/current-release")" = "$(state_value release)" ]] || {
            echo "Published deployment state does not match current-release." >&2
            exit 1
        }
        if [[ "$(state_value mode)" = "$deployment_mode" && "$(state_value release)" = "$deployment_release" ]]; then
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
    write_state prepared
    record_event preflight started
fi

if [[ "$deployment_mode" = update && "$deployment_policy" != backward-compatible \
    && "$deployment_policy" != requires-downtime ]]; then
    echo "An explicit migration compatibility policy is required to resume this update." >&2
    exit 1
fi

dc up -d --no-deps --wait --wait-timeout 120 db
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
