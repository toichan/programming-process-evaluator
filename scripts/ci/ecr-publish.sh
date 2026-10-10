#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/image-build-common.sh"
source "$scripts/ecr-release-common.sh"
source "$scripts/../production/source-manifest.sh"
[[ $# = 4 && "$1" =~ ^[a-f0-9]{40}$ && "${GITHUB_REF:-}" = refs/heads/main &&
    "$4" = /* && ! -e "$4" && ! -L "$4" ]] || { echo "Invalid publish request." >&2; exit 1; }
sha=$1 directory=$2 ci=$3 work=$4
ecr_release_identity || exit 1
ecr_release_platform || exit 1
image_build_target "$sha" refs/remotes/origin/main || exit 1
ecr_release_ci_evidence "$ci" "$sha" "$GITHUB_REPOSITORY" || exit 1
ecr_release_verify_transfer "$directory" "$sha" || exit 1
registry=024378233912.dkr.ecr.ap-northeast-1.amazonaws.com
tag="sha-$sha-gha-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
mkdir -m 0700 "$work" "$work/docker-config" "$work/assets"
dc=(docker --config "$work/docker-config")
completed=false
cleanup() {
    local status=$? image local_tag
    trap - EXIT
    [[ "$completed" = true || "$status" != 0 ]] || status=1
    "${dc[@]}" logout "$registry" >/dev/null 2>&1 || true
    for image in app tools db runner broker nginx backup; do
        "${dc[@]}" image rm "$registry/ppe/$image:$tag" >/dev/null 2>&1 || true
        if [[ -f "$directory/$image.json" ]]; then
            local_tag=$(jq -r '.[0].RepoTags[0]' "$directory/$image.json")
            "${dc[@]}" image rm "$local_tag" >/dev/null 2>&1 || true
        fi
    done
    rm -f "$work/curl-auth" "$work/docker-config/config.json" "$work/download-url" \
        "$work/http-headers" "$work/upload-headers"
    if (( status != 0 )); then
        echo "Publishing failed; any partial images remain. No complete release is declared." >&2
    fi
    exit "$status"
}
trap cleanup EXIT
aws_args=(--region ap-northeast-1 --no-cli-pager)
aws sts get-caller-identity "${aws_args[@]}" --output json > "$work/identity.json" || exit 1
expected="arn:aws:sts::024378233912:assumed-role/PPEGitHubECRPublisherRole/ppe-ecr-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
jq -e --arg expected "$expected" '.Account=="024378233912" and .Arn==$expected' "$work/identity.json" >/dev/null || exit 1

# All eight tag checks finish before the first upload using paginated service JSON.
for image in app tools db runner broker nginx backup releases; do
    aws ecr describe-images "${aws_args[@]}" --repository-name "ppe/$image" \
        --filter tagStatus=TAGGED --output json > "$work/preflight.json" || {
        echo "ECR tag absence could not be established: ppe/$image" >&2; exit 1;
    }
    jq -e --arg tag "$tag" '
        .imageDetails | type=="array" and
        all(.imageTags | type=="array" and all(type=="string")) and
        ([.[] | select(.imageTags | index($tag))] | length==0)
    ' "$work/preflight.json" >/dev/null || {
        echo "Tag collision or invalid ECR response: ppe/$image:$tag" >&2; exit 1;
    }
done
password=$(aws ecr get-login-password "${aws_args[@]}") || exit 1
[[ -n "$password" && "$password" != *$'\n'* && "$password" != *'"'* && "$password" != *\\* ]] || exit 1
printf 'user = "AWS:%s"\n' "$password" > "$work/curl-auth"
printf '%s' "$password" | "${dc[@]}" login --username AWS --password-stdin "$registry" >/dev/null || exit 1
unset password

# No redirects, no verbose output, and authentication stays in a private file, not argv.
request() {
    local method=$1 url=$2 output=$3 content=${4:-} media=${5:-}
    local args=(--silent --show-error --fail --proto '=https' --tlsv1.2
        --connect-timeout 20 --max-time 600 --config "$work/curl-auth"
        --request "$method" --output "$output" --dump-header "$work/http-headers")
    [[ "$url" = "https://$registry/v2/"* ]] || return 1
    if [[ -n "$content" ]]; then args+=(--data-binary "@$content" --header "Content-Type: $media"); fi
    local status location
    status=$(curl "${args[@]}" --write-out '%{http_code}' \
        --header 'Accept: application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' "$url") || return 1
    if [[ "$method" = GET && ( "$status" = 307 || "$status" = 302 ) && "$url" = */blobs/* ]]; then
        location=$(awk 'tolower($1)=="location:" {sub("\r$","",$2); print $2}' "$work/http-headers")
        [[ "$location" =~ ^https://[a-z0-9-]+\.s3\.ap-northeast-1\.amazonaws\.com/ &&
            "$location" != *$'\n'* && "$location" != *$'\r'* &&
            "$location" != *'"'* && "$location" != *\\* ]] || return 1
        # ECR layer downloads may redirect to a presigned S3 URL; never forward registry auth.
        printf 'url = "%s"\n' "$location" > "$work/download-url"
        curl --silent --show-error --fail --proto '=https' --tlsv1.2 --connect-timeout 20 --max-time 600 \
            --config "$work/download-url" --output "$output" || return 1
        rm "$work/download-url"
    else
        [[ "$status" = 200 || "$status" = 201 || "$status" = 202 ]]
    fi
}
verify_blob() {
    local repository=$1 digest=$2 expected_file=$3
    [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || return 1
    request GET "https://$registry/v2/ppe/$repository/blobs/$digest" "$work/download" || return 1
    [[ "sha256:$(source_hash "$work/download")" = "$digest" ]] && cmp -s "$expected_file" "$work/download"
}
: > "$work/images.jsonl"
for image in app tools db runner broker nginx backup; do
    id=$(jq -r '.[0].Id' "$directory/$image.json")
    "${dc[@]}" image inspect "$id" > "$work/local.json" || exit 1
    image_build_metadata "$work/local.json" "$sha" || exit 1
    [[ "$(jq -r '.[0].Id' "$work/local.json")" = "$id" ]] || exit 1
    "${dc[@]}" tag "$id" "$registry/ppe/$image:$tag" || exit 1
    "${dc[@]}" push "$registry/ppe/$image:$tag" || exit 1
    aws ecr describe-images "${aws_args[@]}" --repository-name "ppe/$image" \
        --image-ids "imageTag=$tag" --output json > "$work/remote.json" || exit 1
    digest=$(jq -er '.imageDetails|select(length==1)|.[0].imageDigest|
        select(test("^sha256:[a-f0-9]{64}$"))' "$work/remote.json") || exit 1
    request GET "https://$registry/v2/ppe/$image/manifests/$digest" "$work/remote-manifest.json" || exit 1
    [[ "sha256:$(source_hash "$work/remote-manifest.json")" = "$digest" ]] || {
        echo "Remote manifest bytes do not match ECR digest." >&2; exit 1;
    }
    jq -e --arg id "$id" '
      .schemaVersion==2 and
      (.mediaType=="application/vnd.oci.image.manifest.v1+json" or
       .mediaType=="application/vnd.docker.distribution.manifest.v2+json") and
      .config.digest==$id and (.layers|type=="array" and length>0)' "$work/remote-manifest.json" >/dev/null || exit 1
    request GET "https://$registry/v2/ppe/$image/blobs/$id" "$work/config.json" || exit 1
    [[ "sha256:$(source_hash "$work/config.json")" = "$id" &&
        "$(wc -c < "$work/config.json" | tr -d ' ')" = "$(jq -r '.config.size' "$work/remote-manifest.json")" ]] || {
        echo "Remote config bytes do not match Image ID/descriptor size." >&2; exit 1;
    }
    jq -e --arg sha "$sha" '.os=="linux" and .architecture=="amd64" and
        .config.Labels["org.opencontainers.image.revision"]==$sha' "$work/config.json" >/dev/null || exit 1
    # Docker also checks the registry layer/config relationship, not just JSON descriptors.
    "${dc[@]}" pull --platform linux/amd64 "$registry/ppe/$image@$digest" || exit 1
    "${dc[@]}" image inspect "$registry/ppe/$image@$digest" > "$work/pulled.json" || exit 1
    image_build_metadata "$work/pulled.json" "$sha" || exit 1
    [[ "$(jq -r '.[0].Id' "$work/pulled.json")" = "$id" ]] || exit 1
    jq -nc --arg name "$image" --arg repo "$registry/ppe/$image" --arg digest "$digest" --arg id "$id" --arg sha "$sha" \
        '{name:$name,repository:$repo,digest:$digest,digestType:"manifest",configDigest:$id,imageId:$id,
          revision:$sha,platform:"linux/amd64"}' >> "$work/images.jsonl"
done
jq -s --slurpfile ci "$ci" --arg sha "$sha" --arg workflow "$GITHUB_SHA" --arg run "$GITHUB_RUN_ID" --arg attempt "$GITHUB_RUN_ATTEMPT" \
    --arg tag "$tag" --arg archive "$(source_hash "$directory/source.tar")" \
    --arg manifest "$(source_hash "$directory/source.sha256")" --arg transfer "$(source_hash "$directory/transfer.json")" '
    {schema:1,sha:$sha,runId:$run,attempt:$attempt,buildId:("gha-"+$run+"-"+$attempt),tag:$tag,
     workflowSha:$workflow,platform:"linux/amd64",images:.,ci:$ci[0],source:{archiveSha256:$archive,manifestSha256:$manifest},
     reports:{transferSha256:$transfer}}' "$work/images.jsonl" > "$work/assets/release.json"
ecr_release_manifest "$work/assets/release.json" "$sha" || exit 1
# Long-term release assets contain reports and source, but not duplicate Docker image archives.
for file in source.tar source.sha256 images.tsv summary.md disk.tsv docker-disk.txt transfer.json \
    app.json tools.json db.json runner.json broker.json nginx.json backup.json; do
    cp "$directory/$file" "$work/assets/$file"
done
cp "$ci" "$work/assets/ci.json"
tar -cf "$work/release-assets.tar" -C "$work/assets" .
layer="sha256:$(source_hash "$work/release-assets.tar")"
jq -nc --arg layer "$layer" '{architecture:"amd64",os:"linux",config:{},
    rootfs:{type:"layers",diff_ids:[$layer]}}' > "$work/release-config.json"
config="sha256:$(source_hash "$work/release-config.json")"

upload_blob() {
    local file=$1 digest=$2 location
    curl --silent --show-error --fail --proto '=https' --tlsv1.2 --connect-timeout 20 --max-time 600 \
        --config "$work/curl-auth" --request POST --dump-header "$work/upload-headers" \
        --header 'Content-Length: 0' --output "$work/upload-response" \
        "https://$registry/v2/ppe/releases/blobs/uploads/" || return 1
    location=$(awk 'tolower($1)=="location:" {sub("\r$","",$2); print $2}' "$work/upload-headers")
    [[ "$location" != *$'\n'* && -n "$location" ]] || return 1
    if [[ "$location" = /v2/ppe/releases/blobs/uploads/* ]]; then location="https://$registry$location"; fi
    [[ "$location" = "https://$registry/v2/ppe/releases/blobs/uploads/"* && "$location" != *'#'* ]] || return 1
    if [[ "$location" = *'?'* ]]; then location="$location&digest=$digest"; else location="$location?digest=$digest"; fi
    request PUT "$location" "$work/upload-response" "$file" application/octet-stream || return 1
    verify_blob releases "$digest" "$file"
}
upload_blob "$work/release-config.json" "$config" || exit 1
upload_blob "$work/release-assets.tar" "$layer" || exit 1
jq -nc --arg config "$config" --arg layer "$layer" \
    --argjson config_size "$(wc -c < "$work/release-config.json" | tr -d ' ')" \
    --argjson layer_size "$(wc -c < "$work/release-assets.tar" | tr -d ' ')" '
    {schemaVersion:2,mediaType:"application/vnd.oci.image.manifest.v1+json",
     config:{mediaType:"application/vnd.oci.image.config.v1+json",digest:$config,size:$config_size},
     layers:[{mediaType:"application/vnd.oci.image.layer.v1.tar",digest:$layer,size:$layer_size}],
     annotations:{"org.opencontainers.image.title":"PPE complete release assets"}}' > "$work/oci-manifest.json"
release_digest="sha256:$(source_hash "$work/oci-manifest.json")"
# This is the sole complete-release marker, written only after all seven images and asset bytes verify.
request PUT "https://$registry/v2/ppe/releases/manifests/$tag" "$work/put-response" \
    "$work/oci-manifest.json" application/vnd.oci.image.manifest.v1+json || exit 1
request GET "https://$registry/v2/ppe/releases/manifests/$tag" "$work/retrieved-manifest.json" || exit 1
[[ "sha256:$(source_hash "$work/retrieved-manifest.json")" = "$release_digest" ]] || exit 1
request GET "https://$registry/v2/ppe/releases/manifests/$release_digest" "$work/retrieved-manifest.json" || exit 1
cmp "$work/oci-manifest.json" "$work/retrieved-manifest.json" || exit 1
verify_blob releases "$config" "$work/release-config.json" || exit 1
verify_blob releases "$layer" "$work/release-assets.tar" || exit 1
printf '# ECR release publication verified\n\nTag: `%s`\n\nAssets: `%s/ppe/releases@%s`\n\nSeven remote manifests/configs and re-downloaded source/report asset bytes verified.\nNo deploy or migration performed. Unsigned ECR-writer trust only.\n' \
    "$tag" "$registry" "$release_digest" > "$work/summary.md"
completed=true
