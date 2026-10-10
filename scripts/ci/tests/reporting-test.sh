#!/usr/bin/env bash
set -euo pipefail
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
trap 'rm -f "$root/TEST-fixture.xml" "$root/result"; rmdir "$root"' EXIT
printf '<testsuite name="synthetic" tests="5" failures="0" errors="0" skipped="2">\n</testsuite>\n' > "$root/TEST-fixture.xml"
bash "$scripts/summarize-junit.sh" "$root" > "$root/result"
grep -q '5 tests, 3 passed, 0 failures, 0 errors, 2 skipped' "$root/result"
printf '<testsuite name="synthetic" tests="5" failures="1" errors="0" skipped="2">\n</testsuite>\n' > "$root/TEST-fixture.xml"
if bash "$scripts/summarize-junit.sh" "$root" > "$root/result"; then
    echo "A failing JUnit report must not pass." >&2; exit 1
fi
printf '<testsuite name="synthetic" tests="invalid" failures="0" errors="0" skipped="0">\n</testsuite>\n' > "$root/TEST-fixture.xml"
if bash "$scripts/summarize-junit.sh" "$root" > "$root/result"; then
    echo "Invalid JUnit counts must not pass." >&2; exit 1
fi
printf '<testsuite name="synthetic" tests="5" failures="0" errors="0" skipped="5">\n</testsuite>\n' > "$root/TEST-fixture.xml"
if bash "$scripts/summarize-junit.sh" "$root" > "$root/result"; then
    echo "An entirely skipped JUnit run must not pass." >&2; exit 1
fi
rm "$root/TEST-fixture.xml"
if bash "$scripts/summarize-junit.sh" "$root" > "$root/result" 2>&1; then
    echo "Missing JUnit reports must not pass." >&2; exit 1
fi
echo "PASS: CI report counts, skips, all-skipped, failure, malformed and missing report gates"
