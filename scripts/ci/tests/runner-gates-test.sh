#!/usr/bin/env bash
set -euo pipefail
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
mkdir -p "$root/scripts/ci" "$root/scripts/production/tests"
cp "$scripts/run-shell-tests.sh" "$root/scripts/ci/"
cat > "$root/scripts/production/tests/deployment-hardening-test.sh" <<'FIXTURE'
#!/usr/bin/env bash
set -euo pipefail
count=20
[[ "$CI_SYNTHETIC_RUNNER_CASE" != incomplete ]] || count=19
for ((i = 1; i <= count; i++)); do
    printf 'suite-%s PASS exit=0 assertion-groups=1 skipped=0\n' "$i"
    echo "PASS: synthetic only" > "$PPE_HARDENING_TEST_OUTPUT_DIR/suite-$i.log"
done
case "$CI_SYNTHETIC_RUNNER_CASE" in
    skipped) echo 'SKIP: synthetic missing prerequisite' >> "$PPE_HARDENING_TEST_OUTPUT_DIR/suite-1.log" ;;
    failure) exit 23 ;;
    no-marker) exit 0 ;;
esac
echo 'Deployment hardening regression completed; synthetic only.'
FIXTURE
CI_SYNTHETIC_RUNNER_CASE=success bash "$root/scripts/ci/run-shell-tests.sh" "$root/success" > "$root/result"
grep -q 'Shell CI: 20 suites passed, zero skips.' "$root/result"
for scenario in incomplete skipped failure no-marker; do
    status=0
    CI_SYNTHETIC_RUNNER_CASE="$scenario" bash "$root/scripts/ci/run-shell-tests.sh" "$root/$scenario" \
        > "$root/result" 2>&1 || status=$?
    (( status != 0 )) || { echo "Unsafe runner scenario accepted: $scenario" >&2; exit 1; }
    if [[ "$scenario" = failure ]]; then
        [[ "$status" = 23 ]] || { echo "Runner failure status was hidden." >&2; exit 1; }
    fi
done
echo "PASS: Shell CI rejects incomplete, skipped, failed and missing-completion runs"
