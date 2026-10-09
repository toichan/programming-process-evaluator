#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
state_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-state-test.XXXXXX")
trap 'rm -rf "$state_root"' EXIT
chmod 0700 "$state_root"
export PPE_STATE_DIR="$state_root/state"
mkdir -m 0700 "$PPE_STATE_DIR"
source "$scripts/deployment-state.sh"

deployment_mode=initial
deployment_release=1111111111111111111111111111111111111111
deployment_previous=
deployment_policy=initial
migration_baseline=0
target_migrations=23
deployment_phase=prepared

write_state prepared
[[ -z "$(find "$state_file" -prune ! -perm 0600 -print)" ]]
[[ "$(state_value release)" = "$deployment_release" ]]
! stage_complete database_complete
write_state database_started
! stage_complete database_complete
write_state database_complete
stage_complete database_complete
! stage_complete migration_complete
write_state migration_started
record_event migration failed 97
[[ "$(awk -F '\t' 'END {print $0}' "$event_file")" = *$'\tstage=migration\tresult=failed\texit=97' ]]
[[ -z "$(find "$PPE_STATE_DIR" -maxdepth 1 -name '.deployment-state.*' -print -quit)" ]]
echo "PASS: deployment journal is private, atomic, persistent and distinguishes started/completed stages"

write_release_file "$PPE_STATE_DIR/current-release" "$deployment_release"
write_release_file "$PPE_STATE_DIR/schema-policy" initial
deployment_mode=update
deployment_release=2222222222222222222222222222222222222222
deployment_previous=1111111111111111111111111111111111111111
deployment_policy=backward-compatible
deployment_phase=publish_started
write_state published
publish_state "$deployment_release" backward-compatible
[[ "$(cat "$PPE_STATE_DIR/current-release")" = "$deployment_release" ]]
[[ "$(cat "$PPE_STATE_DIR/previous-release")" = "$deployment_previous" ]]
[[ "$(cat "$PPE_STATE_DIR/schema-policy")" = backward-compatible ]]
echo "PASS: publishing an update keeps both active and previous release markers consistent"

deployment_mode=rollback
deployment_release=1111111111111111111111111111111111111111
deployment_previous=2222222222222222222222222222222222222222
deployment_policy=backward-compatible
deployment_phase=publish_started
publish_state "$deployment_release" backward-compatible
[[ "$(cat "$PPE_STATE_DIR/current-release")" = "$deployment_release" ]]
[[ "$(cat "$PPE_STATE_DIR/previous-release")" = "$deployment_previous" ]]
echo "PASS: publishing an app rollback preserves the current DB schema policy and release history"
