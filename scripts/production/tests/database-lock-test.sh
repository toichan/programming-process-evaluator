#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

scripts=$(cd "$(dirname "$0")/.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-db-lock-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT
chmod 0700 "$test_root"
state_dir="$test_root/state"
mkdir -m 0700 "$state_dir"
lock_file="$state_dir/database-operation.lock"
helper="$scripts/database-lock.sh"

if ! command -v flock >/dev/null 2>&1 || [[ ! -d /proc/self/fd ]]; then
    echo "SKIP: Linux /proc inherited-descriptor lock tests require flock and /proc."
    exit 0
fi

invoke_helper() {
    PPE_STATE_DIR=$state_dir bash -c '
        source "$1"
        acquire_database_lock
    ' _ "$helper"
}

invoke_helper
[[ "$(stat -Lc '%a' "$lock_file")" = 600 ]]
echo "PASS: standalone helper creates and locks the private state file"

(
    exec 9>> "$lock_file"
    flock -n 9
    invoke_helper
)
echo "PASS: helper accepts inherited FD9 when /proc confirms it is the locked state-file inode"

other_file="$test_root/other.lock"
touch "$other_file"
if (
    exec 9>> "$other_file"
    PPE_STATE_DIR=$state_dir bash -c '
        source "$1"
        acquire_database_lock
    ' _ "$helper"
) > "$test_root/wrong-inode.log" 2>&1; then
    echo "FAIL: helper accepted inherited FD9 for a different inode" >&2
    exit 1
fi
grep -q 'Inherited descriptor does not reference this database lock' "$test_root/wrong-inode.log"
echo "PASS: helper rejects inherited FD9 when /proc reports a different inode"

(
    exec 8>> "$lock_file"
    flock -n 8
    if invoke_helper > "$test_root/contended.log" 2>&1; then
        echo "FAIL: helper accepted a lock held through another open file description" >&2
        exit 1
    fi
    grep -q 'Another backup/deployment owns the database operation lock' "$test_root/contended.log"
)
echo "PASS: helper requires actual flock ownership, not merely an inherited descriptor"
