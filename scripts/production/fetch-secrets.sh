#!/bin/sh
set -eu
set +x
umask 077

test "$#" = 1 || { echo "Usage: sh fetch-secrets.sh NEW_ABSOLUTE_SECRET_DIRECTORY" >&2; exit 1; }
output=$1
case "$output" in /*) ;; *) echo "An absolute output directory is required." >&2; exit 1 ;; esac
case "$output" in *'/../'*|*'/./'*|*/..|*/.|/) echo "Invalid output directory." >&2; exit 1 ;; esac
test ! -e "$output" && test ! -L "$output" || {
    echo "Refusing to overwrite an existing secret snapshot." >&2; exit 1;
}
parent=$(dirname "$output")
mkdir -p -m 700 "$parent"
test ! -L "$parent" && test -z "$(find "$parent" -prune ! -perm 0700 -print)" || {
    echo "The secret parent directory must be private (0700), not a symlink." >&2; exit 1;
}
stage=$(mktemp -d "$parent/.ppe-secrets.XXXXXX")
locked=0
names="gemini-api-key db-password db-root-password db-migration-password student-credential-key runner-token"
cleanup() {
    for name in $names; do rm -f "$stage/$name"; done
    rmdir "$stage" 2>/dev/null || true
    if [ "$locked" = 1 ]; then rmdir "$output.lock"; fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
mkdir "$output.lock" 2>/dev/null || { echo "This secret snapshot is already being prepared." >&2; exit 1; }
locked=1

for name in $names; do
    if ! aws ssm get-parameter --region "${AWS_REGION:-ap-northeast-1}" \
        --name "/programming-process-evaluator/prod/$name" \
        --with-decryption --query Parameter.Value --output text \
        > "$stage/$name" 2>/dev/null; then
        echo "Parameter retrieval failed: $name. No secret snapshot was published." >&2
        exit 1
    fi
    if ! LC_ALL=C awk 'NR != 1 || length($0) < 1 || length($0) > 4096 || /[^ -~]/ || $0 == "None" {exit 1}
        END {if (NR != 1) exit 1}' "$stage/$name"; then
        echo "Parameter validation failed: $name. No secret snapshot was published." >&2
        exit 1
    fi
done
grep -Eq '^[A-Za-z0-9+/]{43}=$' "$stage/student-credential-key" || {
    echo "The credential key must be a base64-encoded 256-bit AES key." >&2; exit 1;
}
LC_ALL=C awk 'length($0) < 32 || /[[:space:]]/ {exit 1}' "$stage/runner-token" || {
    echo "The runner token must contain at least 32 non-whitespace characters." >&2; exit 1;
}
for name in db-password db-root-password db-migration-password; do
    LC_ALL=C awk 'length($0) < 20 {exit 1}' "$stage/$name" || {
        echo "Database credentials must be at least 20 characters." >&2; exit 1;
    }
done
for name in $names; do chmod 0444 "$stage/$name"; done
# The private host parent protects the files; Docker's nonroot secret mounts
# require read permission. Publish only after every required value is valid.
mv "$stage" "$output"
echo "Complete secret snapshot published. Secret values were not printed."
