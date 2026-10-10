#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/ecr-release-common.sh"
source "$scripts/../production/source-manifest.sh"
[[ $# = 3 && "$1" =~ ^[1-9][0-9]*$ && "$2" =~ ^[a-f0-9]{64}$ &&
    "$3" = /* && ! -e "$3" && ! -L "$3" &&
    "${GITHUB_REF:-}" = refs/heads/main &&
    "${GITHUB_REPOSITORY:-}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || exit 1
ecr_release_identity || exit 1
id=$1 digest=$2 output=$3
mkdir -m 0700 "$output" "$output/bundle"
gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$id" > "$output/metadata.json"
jq -e --arg id "$id" --arg digest "sha256:$digest" --arg run "$GITHUB_RUN_ID" --arg sha "$GITHUB_SHA" \
    --arg name "ecr-transfer-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT" '
    (.id|tostring)==$id and .digest==$digest and .name==$name and .expired==false and
    (.workflow_run.id|tostring)==$run and .workflow_run.head_sha==$sha' "$output/metadata.json" >/dev/null
# Hash the actual ZIP against the same build job's immutable upload output.
# A downloader's nonfatal digest warning is not an acceptable trust boundary.
gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$id/zip" > "$output/transfer.zip"
[[ "$(source_hash "$output/transfer.zip")" = "$digest" ]] || {
    echo "Transferred ZIP differs from the immutable build artifact digest." >&2; exit 1;
}
unzip -Z1 "$output/transfer.zip" > "$output/zip-names"
expected=$({ ecr_release_files | sed 's|^|images/|'; printf '%s\n' images/transfer.json ci.json; } |
    LC_ALL=C sort)
actual=$(grep -v '^images/$' "$output/zip-names" | LC_ALL=C sort)
[[ "$actual" = "$expected" && "$(grep -c '^images/$' "$output/zip-names" || true)" -le 1 ]] || {
    echo "Unexpected or duplicate artifact ZIP entries." >&2; exit 1;
}
unzip -q "$output/transfer.zip" -d "$output/bundle"
[[ -z "$(find "$output/bundle" ! -type d ! -type f -print)" ]] || exit 1
rm "$output/transfer.zip"
echo "Same-run artifact metadata and complete ZIP digest verified."
