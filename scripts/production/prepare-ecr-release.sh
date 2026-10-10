#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
[[ $# = 3 && "$1" =~ ^[a-f0-9]{40}$ && "$2" =~ ^sha256:[a-f0-9]{64}$ &&
    "$3" = /* && "$3" != / && "$3" != "$HOME" && ! -L "$3" ]] || {
    echo "Usage: prepare-ecr-release.sh FULL_SOURCE_SHA OCI_DIGEST ABSOLUTE_RELEASE_ROOT" >&2; exit 1;
}
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/source-manifest.sh"
source "$scripts/../ci/image-build-common.sh"
source "$scripts/../ci/ecr-release-common.sh"
source "$scripts/../ci/ecr-registry-common.sh"
sha=$1 digest=$2 root=$3
registry=024378233912.dkr.ecr.ap-northeast-1.amazonaws.com
[[ "${PPE_ECR_ACQUISITION_APPROVAL:-}" = yes ]] || {
    echo "Explicit ECR acquisition approval is required; this does not approve deployment." >&2; exit 1;
}
ecr_release_platform
[[ -z "${AWS_ACCESS_KEY_ID:-}${AWS_SECRET_ACCESS_KEY:-}${AWS_SESSION_TOKEN:-}${AWS_PROFILE:-}${AWS_DEFAULT_PROFILE:-}${AWS_WEB_IDENTITY_TOKEN_FILE:-}${AWS_CONTAINER_CREDENTIALS_RELATIVE_URI:-}${AWS_CONTAINER_CREDENTIALS_FULL_URI:-}" ]] || {
    echo "Use the EC2 host instance role, not supplied credentials or another profile." >&2; exit 1;
}
export AWS_SHARED_CREDENTIALS_FILE=/dev/null AWS_CONFIG_FILE=/dev/null AWS_PAGER=""
export AWS_EC2_METADATA_DISABLED=false AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true
aws_args=(--region ap-northeast-1 --no-cli-pager)
[[ -d "$root" && -z "$(find "$root" -prune \( ! -perm 0700 -o ! -user "$(id -u)" \) -print)" ]] || {
    echo "Release root must already exist, owned by this user with mode 0700." >&2; exit 1;
}
root=$(cd "$root" && pwd -P)
release="$root/$sha"
[[ ! -e "$release" && ! -L "$release" ]] || { echo "Existing immutable release cannot be overwritten." >&2; exit 1; }
mkdir "$root/.ecr-acquire.lock" || { echo "Another acquisition or stale lock needs review." >&2; exit 1; }
work=""
cleanup() {
    local status=$?
    trap - EXIT
    if [[ -n "$work" && "$work" = "$root"/.ecr-stage.* ]]; then
        rm -rf "$work"
    fi
    rmdir "$root/.ecr-acquire.lock"
    if (( status != 0 )); then echo "ECR preparation failed; no deployment was performed. Pulled images are retained." >&2; fi
    exit "$status"
}
trap cleanup EXIT
trap 'echo "ECR preparation failed at line $LINENO." >&2' ERR
work=$(mktemp -d "$root/.ecr-stage.XXXXXX")
mkdir "$work/docker-config" "$work/assets" "$work/release" "$work/release/source"
dc=(docker --config "$work/docker-config")
aws sts get-caller-identity "${aws_args[@]}" --output json > "$work/identity.json"
jq -e '.Account=="024378233912" and
    (.Arn|test("^arn:aws:sts::024378233912:assumed-role/ProgrammingProcessEvaluatorEC2Role/[^/]+$"))' "$work/identity.json" >/dev/null
password=$(aws ecr get-login-password "${aws_args[@]}")
[[ -n "$password" && "$password" != *$'\n'* && "$password" != *'"'* && "$password" != *\\* ]] || exit 1
printf 'user = "AWS:%s"\n' "$password" > "$work/curl-auth"
printf '%s' "$password" | "${dc[@]}" login --username AWS --password-stdin "$registry" >/dev/null
unset password

download_blob() {
    local repository=$1 blob=$2 size=$3 output=$4
    [[ "$blob" =~ ^sha256:[a-f0-9]{64}$ && "$size" =~ ^[1-9][0-9]*$ ]] || return 1
    request GET "https://$registry/v2/ppe/$repository/blobs/$blob" "$output" || return 1
    [[ "sha256:$(source_hash "$output")" = "$blob" &&
        "$(wc -c < "$output" | tr -d ' ')" = "$size" ]]
}
extract_archive() {
    local archive=$1 output=$2
    # BSD tar otherwise escapes existing Japanese filenames as backslash octals.
    LC_ALL=en_US.UTF-8 tar -tf "$archive" > "$work/archive-names" || return 1
    ! grep -Eq '(^/|(^|/)\.\.(/|$)|\\|[[:cntrl:]])' "$work/archive-names" || return 1
    [[ "$(wc -l < "$work/archive-names")" = "$(sed 's|^\./||' "$work/archive-names" | LC_ALL=C sort -u | wc -l)" ]] || return 1
    tar -tvf "$archive" | awk 'substr($1,1,1)!="-" && substr($1,1,1)!="d" {bad=1} END{exit bad}' || return 1
    tar --no-same-owner --no-same-permissions -xf "$archive" -C "$output"
}
request GET "https://$registry/v2/ppe/releases/manifests/$digest" "$work/oci.json"
[[ "sha256:$(source_hash "$work/oci.json")" = "$digest" ]] || { echo "Release OCI digest mismatch." >&2; exit 1; }
jq -e '.schemaVersion==2 and .mediaType=="application/vnd.oci.image.manifest.v1+json" and
    .config.mediaType=="application/vnd.oci.image.config.v1+json" and
    (.layers|length==1 and .[0].mediaType=="application/vnd.oci.image.layer.v1.tar")' "$work/oci.json" >/dev/null
download_blob releases "$(jq -r '.config.digest' "$work/oci.json")" "$(jq -r '.config.size' "$work/oci.json")" "$work/config.json"
download_blob releases "$(jq -r '.layers[0].digest' "$work/oci.json")" "$(jq -r '.layers[0].size' "$work/oci.json")" "$work/assets.tar"
jq -e --arg layer "$(jq -r '.layers[0].digest' "$work/oci.json")" '
    .os=="linux" and .architecture=="amd64" and .rootfs.diff_ids==[$layer]' "$work/config.json" >/dev/null
extract_archive "$work/assets.tar" "$work/assets"
manifest="$work/assets/release.json"
ecr_release_manifest "$manifest" "$sha" \
    "$(jq -r '.workflowSha' "$manifest")" "$(jq -r '.runId' "$manifest")" \
    "$(jq -r '.attempt' "$manifest")" toichan/programming-process-evaluator
[[ "$(source_hash "$work/assets/source.tar")" = "$(jq -r '.source.archiveSha256' "$manifest")" &&
    "$(source_hash "$work/assets/source.sha256")" = "$(jq -r '.source.manifestSha256' "$manifest")" ]] || {
    echo "Source archive/manifest mismatch." >&2; exit 1;
}
extract_archive "$work/assets/source.tar" "$work/release/source"
generate_source_manifest "$work/release/source" > "$work/release/source.sha256"
cmp "$work/release/source.sha256" "$work/assets/source.sha256"
[[ -f "$work/release/source/compose.production.yml" ]] || exit 1
if find "$work/release/source" -type f \( -name '.env' -o -name '.env.production' -o -name '*.key' -o -name '*.pem' \) |
    grep -q .; then
    echo "Sensitive file names found in release source; review before preparation." >&2; exit 1
fi

for image in app tools db runner broker nginx backup; do
    image_digest=$(jq -r --arg name "$image" '.images[]|select(.name==$name)|.digest' "$manifest")
    id=$(jq -r --arg name "$image" '.images[]|select(.name==$name)|.imageId' "$manifest")
    request GET "https://$registry/v2/ppe/$image/manifests/$image_digest" "$work/image-manifest.json"
    [[ "sha256:$(source_hash "$work/image-manifest.json")" = "$image_digest" ]] || { echo "Image manifest digest mismatch: $image" >&2; exit 1; }
    jq -e --arg id "$id" '.schemaVersion==2 and .config.digest==$id and
        (.mediaType=="application/vnd.docker.distribution.manifest.v2+json" or
         .mediaType=="application/vnd.oci.image.manifest.v1+json")' "$work/image-manifest.json" >/dev/null
    download_blob "$image" "$id" "$(jq -r '.config.size' "$work/image-manifest.json")" "$work/config.json"
    jq -e --arg sha "$sha" '.os=="linux" and .architecture=="amd64" and
        .config.Labels["org.opencontainers.image.revision"]==$sha' "$work/config.json" >/dev/null
    "${dc[@]}" pull --platform linux/amd64 "$registry/ppe/$image@$image_digest"
    "${dc[@]}" image inspect "$registry/ppe/$image@$image_digest" > "$work/image.json"
    image_build_metadata "$work/image.json" "$sha"
    [[ "$(jq -r '.[0].Id' "$work/image.json")" = "$id" ]] || { echo "Pulled Image ID mismatch: $image" >&2; exit 1; }
    printf '%s\n' "$id" > "$work/release/ppe-$image.id"
done

# Check every old tag before creating any new deployment tag.
"${dc[@]}" image ls --format '{{.Repository}}:{{.Tag}}' > "$work/tags"
for image in app tools db runner broker nginx backup; do
    if grep -Fxq "ppe-$image:$sha" "$work/tags"; then
        "${dc[@]}" image inspect "ppe-$image:$sha" > "$work/existing.json"
        [[ "$(jq -r '.[0].Id' "$work/existing.json")" = "$(cat "$work/release/ppe-$image.id")" ]] || {
            echo "Existing production tag collision: ppe-$image:$sha" >&2; exit 1;
        }
    fi
done
for image in app tools db runner broker nginx backup; do
    if ! grep -Fxq "ppe-$image:$sha" "$work/tags"; then
        "${dc[@]}" tag "$(cat "$work/release/ppe-$image.id")" "ppe-$image:$sha"
    fi
    "${dc[@]}" image inspect "ppe-$image:$sha" > "$work/tagged.json"
    [[ "$(jq -r '.[0].Id' "$work/tagged.json")" = "$(cat "$work/release/ppe-$image.id")" ]] || exit 1
done
cp "$manifest" "$work/release/ecr-release.json"
printf '%s\n' "$digest" > "$work/release/ecr-release.digest"
printf '%s\n' "$sha" > "$work/release/commit"
# Exclusive directory creation refuses a concurrent local-build release too.
mkdir "$release"
mv "$work/release/"* "$release/"
printf '%s\n' "$sha" > "$release/READY"
echo "Verified ECR release prepared: $release"
echo "Source manifest SHA-256: $(source_hash "$release/source.sha256")"
echo "No containers, database, deployment state or active release were changed. Run deployment separately after approval."
