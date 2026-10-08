#!/bin/sh
set -eu
set +x
root=$(mktemp -d "${TMPDIR:-/tmp}/ppe-secret-test.XXXXXX")
cleanup() {
    for snapshot in success failure; do
        for name in gemini-api-key db-password db-root-password db-migration-password student-credential-key runner-token; do
            rm -f "$root/$snapshot/$name"
        done
        rmdir "$root/$snapshot" 2>/dev/null || true
    done
    rm -f "$root/aws" "$root/output"
    rmdir "$root"
}
trap cleanup EXIT
cat > "$root/aws" <<'MOCK'
#!/bin/sh
set -eu
case "$*" in
    *db-root-password*) test "${FAIL_FETCH:-0}" != 1 || exit 1 ;;
esac
case "$*" in
    *student-credential-key*) printf '%s\n' 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' ;;
    *gemini-api-key*) printf '%s\n' 'synthetic-mock-api-key' ;;
    *) printf '%s\n' 'synthetic-test-only-never-a-real-secret-1234567890' ;;
esac
MOCK
chmod 0700 "$root/aws"
PATH="$root:$PATH" sh scripts/production/fetch-secrets.sh "$root/success" > "$root/output"
test "$(find "$root/success" -type f | wc -l | tr -d ' ')" = 6
! grep -q 'synthetic-mock-api-key' "$root/output"
if PATH="$root:$PATH" FAIL_FETCH=1 sh scripts/production/fetch-secrets.sh "$root/failure" > "$root/output" 2>&1; then
    echo "Expected retrieval failure." >&2; exit 1
fi
test ! -e "$root/failure"
test -z "$(find "$root" -name '.ppe-secrets.*' -print)"
if PATH="$root:$PATH" sh scripts/production/fetch-secrets.sh "$root/success" > "$root/output" 2>&1; then
    echo "Expected existing snapshot rejection." >&2; exit 1
fi
! grep -q 'synthetic-test-only' "$root/output"
echo "Secret retrieval, atomic failure, overwrite rejection and non-disclosure: PASS"
