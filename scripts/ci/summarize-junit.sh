#!/usr/bin/env bash
set -euo pipefail
[[ "$#" = 1 && -d "$1" ]] || { echo "JUnit results directory is missing." >&2; exit 1; }
shopt -s nullglob
files=("$1"/TEST-*.xml)
(( ${#files[@]} > 0 )) || { echo "No JUnit XML reports found." >&2; exit 1; }
awk '
function attribute(line, key, value) {
    value = line
    sub(".* " key "=\"", "", value)
    sub("\".*", "", value)
    if (value !~ /^[0-9]+$/) { invalid = 1; return 0 }
    return value + 0
}
/^<testsuite / {
    suites++
    tests += attribute($0, "tests")
    failures += attribute($0, "failures")
    errors += attribute($0, "errors")
    skipped += attribute($0, "skipped")
}
END {
    printf "JUnit: %d suites, %d tests, %d passed, %d failures, %d errors, %d skipped.\n",
        suites, tests, tests - failures - errors - skipped, failures, errors, skipped
    print "Skipped tests require opt-in DB, HTTP, browser fixture or real API environments; they are not covered by this CI job."
    if (invalid || suites != ARGC - 1 || tests - skipped <= 0 || failures != 0 || errors != 0) exit 1
}' "${files[@]}"
