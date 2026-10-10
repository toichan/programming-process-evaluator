#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$scripts/../.." && pwd)
source "$scripts/image-build-common.sh"
source "$repo/scripts/production/source-manifest.sh"
[[ $# = 3 ]] || { echo "Usage: build-production-images.sh FULL_SHA MAIN_REF NEW_ABSOLUTE_OUTPUT" >&2; exit 1; }
commit=$1
main_ref=$2
output=$3
cd "$repo"
image_build_target "$commit" "$main_ref" || {
    echo "Build requires an exact full commit SHA reachable from the trusted main ref." >&2; exit 1;
}
[[ "$output" = /* && ! -e "$output" && ! -L "$output" ]] || {
    echo "A new absolute output directory is required." >&2; exit 1;
}
for tool in docker jq git tar unzip openssl; do
    command -v "$tool" >/dev/null || { echo "Missing build tool: $tool" >&2; exit 1; }
done
for variable in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_PROFILE \
    AWS_WEB_IDENTITY_TOKEN_FILE AWS_ROLE_ARN AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
    AWS_CONTAINER_CREDENTIALS_FULL_URI GEMINI_API_KEY DB_PASSWORD STUDENT_CREDENTIAL_KEY; do
    value=$(printenv "$variable" || true)
    [[ -z "$value" ]] || { echo "Credential environment is not allowed: $variable" >&2; exit 1; }
done
unset value
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
endpoint=${DOCKER_HOST:-$endpoint}
[[ "$endpoint" = unix://* ]] || { echo "Only a local Unix Docker daemon is allowed." >&2; exit 1; }
mkdir -m 0700 "$output" "$output/docker-config" "$output/source" "$output/inspection"
plugin=""
for candidate in /usr/libexec/docker/cli-plugins/docker-buildx /usr/lib/docker/cli-plugins/docker-buildx \
    /usr/local/lib/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx \
    /Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx; do
    if [[ -x "$candidate" ]]; then plugin=$candidate; break; fi
done
[[ -n "$plugin" ]] || { echo "A system buildx plugin is required; legacy builder is not allowed." >&2; exit 1; }
mkdir "$output/docker-config/cli-plugins"
ln -s "$plugin" "$output/docker-config/cli-plugins/docker-buildx"
dc=(docker --host "$endpoint" --config "$output/docker-config")
"${dc[@]}" info >/dev/null
"${dc[@]}" buildx version
start=$(date +%s)
container=""
completed=false
tags=()
cleanup() {
    local status=$?
    trap - EXIT
    if [[ "$completed" != true && "$status" = 0 ]]; then status=1; fi
    if [[ -n "$container" ]]; then
        "${dc[@]}" rm -v "$container" >/dev/null || status=1
    fi
    if (( ${#tags[@]} > 0 )); then
        for tag in "${tags[@]}"; do
            if "${dc[@]}" image inspect "$tag" >/dev/null 2>&1; then
                "${dc[@]}" image rm "$tag" >/dev/null || status=1
            fi
        done
    fi
    if (( status != 0 )); then
        printf '\n**FAIL**: exit=%s. No deployment or registry push was performed.\n' "$status" >> "$output/summary.md"
    fi
    exit "$status"
}
trap cleanup EXIT
printf '# Production image build validation\n\nCommit: `%s`\n\n' "$commit" > "$output/summary.md"
printf 'stage\tavailable_kib\tused_kib\n' > "$output/disk.tsv"
disk() {
    local stage=$1 available used
    read -r used available < <(df -Pk "$output" | awk 'END {print $3, $4}')
    printf '%s\t%s\t%s\n' "$stage" "$available" "$used" >> "$output/disk.tsv"
    (( available >= 1048576 )) || { echo "Less than 1 GiB available; stopping build." >&2; return 1; }
}
disk before
"${dc[@]}" system df > "$output/docker-disk.txt"
git archive --format=tar "$commit" | tar -x -C "$output/source"
image_build_source_safety "$output/source"
generate_source_manifest "$output/source" > "$output/source.sha256"
printf 'image\timage_id\trevision\tplatform\tsize_bytes\tbuild_seconds\n' > "$output/images.tsv"
namespace="ppe-ci-build-$(openssl rand -hex 8)"
build() {
    local image=$1 context=$2 dockerfile=$3 target=${4:-}
    local tag="$namespace-$image:$commit" begin seconds
    local arguments=(buildx build --load --platform linux/amd64 --build-arg "PPE_GIT_COMMIT=$commit"
        --tag "$tag" --file "$context/$dockerfile")
    [[ -z "$target" ]] || arguments+=(--target "$target")
    disk "before-$image"
    tags+=("$tag")
    begin=$(date +%s)
    "${dc[@]}" "${arguments[@]}" "$context"
    seconds=$(( $(date +%s) - begin ))
    "${dc[@]}" image inspect "$tag" > "$output/inspection/$image.json"
    image_build_metadata "$output/inspection/$image.json" "$commit" || {
        echo "Image platform, revision, ID or size validation failed: $image" >&2; return 1;
    }
    jq -r --arg image "$image" --arg seconds "$seconds" \
        '.[0] | [$image,.Id,.Config.Labels["org.opencontainers.image.revision"],
        (.Os+"/"+.Architecture),(.Size|tostring),$seconds] | @tsv' \
        "$output/inspection/$image.json" >> "$output/images.tsv"
    disk "after-$image"
}
source_dir="$output/source"
build app "$source_dir" containers/production/Dockerfile app
build tools "$source_dir" containers/production/Dockerfile tools
build db "$source_dir" containers/production/Dockerfile.db
build runner "$source_dir/containers/python-runner" Dockerfile
build broker "$source_dir/containers/docker-broker" Dockerfile
build nginx "$source_dir/containers/nginx" Dockerfile
build backup "$source_dir/containers/backup" Dockerfile
[[ "$(wc -l < "$output/images.tsv" | tr -d ' ')" = 8 ]] || exit 1

copy_asset() {
    local image=$1 path=$2 destination=$3
    container=$("${dc[@]}" create --platform linux/amd64 --network none --read-only "$namespace-$image:$commit")
    "${dc[@]}" cp "$container:$path" "$destination"
    "${dc[@]}" rm -v "$container" >/dev/null
    container=""
}
copy_asset tools /workspace/src "$output/inspection/tools-src"
copy_asset tools /workspace/build/libs/ROOT.war "$output/inspection/tools.war"
copy_asset app /usr/local/tomcat/webapps/ROOT.war "$output/inspection/app.war"
mkdir "$output/inspection/app-war" "$output/inspection/tools-war"
unzip -q "$output/inspection/app.war" -d "$output/inspection/app-war"
unzip -q "$output/inspection/tools.war" -d "$output/inspection/tools-war"
image_build_assets "$source_dir" "$output/inspection"
copy_asset tools /workspace/build/test-results/test "$output/inspection/junit"
bash "$scripts/summarize-junit.sh" "$output/inspection/junit" >> "$output/summary.md"
for image in app tools db runner broker nginx backup; do
    if jq -e '.[0].Config.Env[]? | test("^(AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|AWS_SESSION_TOKEN|DB_PASSWORD|GEMINI_API_KEY|STUDENT_CREDENTIAL_KEY)=")' \
        "$output/inspection/$image.json" >/dev/null; then
        echo "Unexpected credential variable in image config: $image" >&2; exit 1
    fi
done
disk complete
"${dc[@]}" system df >> "$output/docker-disk.txt"
{
    printf '\n| Image | Image ID | Revision | Platform | Bytes | Build seconds |\n|---|---|---|---|---:|---:|\n'
    tail -n +2 "$output/images.tsv" | while IFS=$'\t' read -r image id revision platform size seconds; do
        printf '| %s | `%s` | `%s` | %s | %s | %s |\n' "$image" "$id" "$revision" "$platform" "$size" "$seconds"
    done
    printf '\nTotal seconds: %s\n\n' "$(( $(date +%s) - start ))"
    printf 'Source manifest SHA256: `%s`\n\n' "$(source_hash "$output/source.sha256")"
    printf 'Disk snapshots (KiB):\n\n```text\n'
    cat "$output/disk.tsv"
    printf '```\n\n**PASS**: seven amd64 images; revision, source/migration, WAR transformation and JUnit verified.\n'
    printf 'Credential checks cover source patterns and image configuration, not exhaustive layer secret scanning.\n'
} >> "$output/summary.md"
completed=true
