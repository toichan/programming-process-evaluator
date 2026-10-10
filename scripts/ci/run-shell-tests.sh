#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
repo=$(cd "$(dirname "$0")/../.." && pwd)
[[ "$#" = 1 && "$1" = /* && ! -e "$1" && ! -L "$1" ]] || {
    echo "Usage: bash run-shell-tests.sh NEW_ABSOLUTE_RESULTS_DIRECTORY" >&2; exit 1;
}
output=$1
mkdir -m 0700 "$output"
status=0
PPE_HARDENING_TEST_IMAGE=ppe-ci-shell:local \
PPE_DB_ADMIN_TEST_IMAGE=ppe-db:local \
PPE_HARDENING_TEST_OUTPUT_DIR="$output" \
    bash "$repo/scripts/production/tests/deployment-hardening-test.sh" \
    > "$output/runner.log" 2>&1 || status=$?
cat "$output/runner.log" | tee "$output/summary.txt"
if (( status != 0 )); then
    printf 'Shell regressions failed: exit=%s\n' "$status" | tee -a "$output/summary.txt"
    exit "$status"
fi
count=$(grep -c ' PASS exit=0 assertion-groups=' "$output/runner.log" || true)
if [[ "$count" != 20 ]] || grep -Eq '^SKIP:|skipped=[1-9][0-9]*' "$output/"*.log; then
    echo "Shell regressions incomplete: require 20 suites and zero skips." | tee -a "$output/summary.txt"
    exit 1
fi
if ! grep -q '^Deployment hardening regression completed;' "$output/runner.log"; then
    echo "Shell regression completion marker is missing." | tee -a "$output/summary.txt"
    exit 1
fi
echo "Shell CI: 20 suites passed, zero skips." | tee -a "$output/summary.txt"
