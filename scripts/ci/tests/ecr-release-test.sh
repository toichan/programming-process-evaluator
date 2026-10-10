#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$scripts/../.." && pwd)
cd "$repo"
source "$scripts/image-build-common.sh"
source "$scripts/ecr-release-common.sh"
source "$scripts/../production/source-manifest.sh"
# All tests are local; none invoke the real aws, gh, curl or docker clients.
root="$repo/.ecr-test-$$"
[[ ! -e "$root" ]] && mkdir -m 0700 "$root" "$root/bin" "$root/input" "$root/state"
trap 'rm -rf "$root"' EXIT
export TEST_ROOT="$root" GITHUB_RUN_ID=12345 GITHUB_RUN_ATTEMPT=1
export GITHUB_REPOSITORY=synthetic/ppe GITHUB_REF=refs/heads/main
sha=$(git rev-parse HEAD)
export TEST_SHA="$sha" GITHUB_SHA="$sha" TEST_CASE=success
export PATH="$root/bin:$PATH"
mkdir "$root/mock-archive"
git archive --format=tar "$sha" > "$root/input/source.tar"
tar -xf "$root/input/source.tar" -C "$root/mock-archive"
generate_source_manifest "$root/mock-archive" > "$root/input/source.sha256"
printf 'image\timage_id\trevision\tplatform\tsize_bytes\tbuild_seconds\n' > "$root/input/images.tsv"
for image in app tools db runner broker nginx backup; do
    jq -nc --arg sha "$sha" --arg image "$image" \
        '{architecture:"amd64",os:"linux",config:{Labels:{"org.opencontainers.image.revision":$sha,"test.image":$image}}}' > "$root/state/$image.config"
    id="sha256:$(source_hash "$root/state/$image.config")"
    jq -nc --arg id "$id" --arg sha "$sha" --arg tag "ppe-ci-build-0123456789abcdef-$image:$sha" \
        '[{Id:$id,Os:"linux",Architecture:"amd64",Size:100,Config:{Labels:{"org.opencontainers.image.revision":$sha}},RepoTags:[$tag]}]' > "$root/input/$image.json"
    config_name="${id#sha256:}.json"
    mkdir "$root/state/archive-$image"
    cp "$root/state/$image.config" "$root/state/archive-$image/$config_name"
    layer_name="$(printf 'a%.0s' {1..64})/layer.tar"
    mkdir "$root/state/archive-$image/${layer_name%/layer.tar}"
    printf 'synthetic-layer' > "$root/state/archive-$image/$layer_name"
    jq -nc --arg config "$config_name" --arg layer "$layer_name" --arg tag "ppe-ci-build-0123456789abcdef-$image:$sha" \
        '[{Config:$config,RepoTags:[$tag],Layers:[$layer]}]' > "$root/state/archive-$image/manifest.json"
    tar -cf "$root/input/$image.tar" -C "$root/state/archive-$image" manifest.json "$config_name" "${layer_name%/layer.tar}"
    printf '%s\t%s\t%s\tlinux/amd64\t100\t1\n' "$image" "$id" "$sha" >> "$root/input/images.tsv"
    jq -nc --arg id "$id" --argjson size "$(wc -c < "$root/state/$image.config" | tr -d ' ')" \
        '{schemaVersion:2,mediaType:"application/vnd.docker.distribution.manifest.v2+json",
          config:{digest:$id,size:$size},layers:[{digest:("sha256:"+("a"*64)),size:1}]}' > "$root/state/$image.manifest"
done
for file in summary.md disk.tsv docker-disk.txt; do echo synthetic > "$root/input/$file"; done
ecr_release_transfer "$root/input" "$sha"
cat > "$root/bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
url="${*: -1}"
case "$url" in
  */actions/workflows/ci.yml) echo '{"id":77,"path":".github/workflows/ci.yml","state":"active"}';;
  *'/runs?'*)
    jq -nc --arg sha "$TEST_SHA" --arg case "$TEST_CASE" \
      '[{workflow_runs:[{id:88,workflow_id:77,head_sha:$sha,head_branch:"main",event:"push",
        status:"completed",conclusion:(if $case=="ci-failure" then "failure" else "success" end)}]}]';;
  *'/jobs?'*)
    jq -nc --arg case "$TEST_CASE" --arg sha "$TEST_SHA" \
      '[{jobs:([{name:"Java build and tests",status:"completed",conclusion:"success"},
        {name:(if $case=="missing-check" then "Other shell check" else "Shell syntax and deployment regressions" end),
         status:"completed",conclusion:(if $case=="check-failure" then "failure" else "success" end)}]
         | map(.+{head_sha:(if $case=="wrong-job-sha" then ("0"*40) else $sha end),
                   run_id:88,run_attempt:(if $case=="wrong-job-attempt" then 2 else 1 end)})
         | if $case=="duplicate-check" then .+[.[0]] else . end)}]';;
  */actions/runs/88)
    jq -nc --arg sha "$TEST_SHA" --arg repo "$GITHUB_REPOSITORY" --arg case "$TEST_CASE" \
      '{id:88,run_attempt:1,workflow_id:77,head_sha:$sha,
        head_branch:(if $case=="wrong-run-branch" then "feature" else "main" end),
        event:(if $case=="wrong-event" then "pull_request" else "push" end),
        path:(if $case=="wrong-workflow" then ".github/workflows/other.yml" else ".github/workflows/ci.yml" end),
        repository:{full_name:$repo},status:"completed",conclusion:"success"}';;
  */actions/artifacts/99/zip)
    cat "$TEST_ROOT/artifact.zip"
    [[ "$TEST_CASE" != zip-corrupt ]] || printf corrupt;;
  */actions/artifacts/99)
    jq -nc --arg sha "$GITHUB_SHA" --arg digest "$TEST_ZIP_HASH" --arg case "$TEST_CASE" \
      '{id:99,digest:("sha256:"+$digest),name:"ecr-transfer-12345-1",expired:false,
        workflow_run:{id:(if $case=="wrong-artifact-run" then 987 else 12345 end),head_sha:$sha}}';;
  *) exit 1;;
esac
MOCK
cat > "$root/bin/uname" <<'MOCK'
#!/usr/bin/env bash
case "$1" in -s) echo Linux;; -m) echo x86_64;; *) exit 1;; esac
MOCK
cat > "$root/bin/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
echo "$1 $2 $TEST_CASE" >> "$TEST_ROOT/aws-calls"
if [[ "$1 $2" = "sts get-caller-identity" ]]; then
    [[ "$TEST_CASE" != oidc-failure ]] || exit 1
    if [[ "${TEST_ECR_ACQUIRE:-}" = yes ]]; then
        echo '{"Account":"024378233912","Arn":"arn:aws:sts::024378233912:assumed-role/ProgrammingProcessEvaluatorEC2Role/i-synthetic"}'
    elif [[ "$TEST_CASE" = wrong-account ]]; then
        echo '{"Account":"000000000000","Arn":"arn:aws:sts::024378233912:assumed-role/PPEGitHubECRPublisherRole/ppe-ecr-12345-1"}'
    else
        echo '{"Account":"024378233912","Arn":"arn:aws:sts::024378233912:assumed-role/PPEGitHubECRPublisherRole/ppe-ecr-12345-1"}'
    fi
elif [[ "$2" = get-login-password ]]; then
    echo synthetic-only-password
elif [[ "$2" = describe-images ]]; then
    image=""
    preflight=false
    while (( $# )); do
        if [[ "$1" = --repository-name ]]; then image=${2#ppe/}; fi
        if [[ "$1" = --filter ]]; then preflight=true; fi
        shift
    done
    if [[ "$TEST_CASE" = duplicate-tag || "$TEST_CASE" = completed-tag ]]; then
        jq -nc --arg tag "sha-$TEST_SHA-gha-12345-1" '{imageDetails:[{imageTags:[$tag]}]}'
    elif [[ "$TEST_CASE" = access-denied ]]; then
        echo 'An error occurred (AccessDeniedException) when calling the DescribeImages operation: synthetic' >&2; exit 254
    elif [[ "$preflight" = true ]]; then
        if [[ "$TEST_CASE" = malformed-preflight ]]; then echo '{"imageDetails":null}'
        else echo '{"imageDetails":[{"imageTags":["other-existing-release"]}]}'; fi
    elif [[ -f "$TEST_ROOT/state/$image.pushed" ]]; then
        digest=$(openssl dgst -sha256 "$TEST_ROOT/state/$image.manifest" | awk '{print $NF}')
        if [[ "$TEST_CASE" = digest-mismatch ]]; then digest=$(printf '%064d' 0); fi
        jq -nc --arg digest "sha256:$digest" '{imageDetails:[{imageDigest:$digest}]}'
    else
        printf '\n' >&2
        echo 'An error occurred (ImageNotFoundException) when calling the DescribeImages operation: synthetic' >&2; exit 254
    fi
else exit 1
fi
MOCK
cat > "$root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" != --config ]] || shift 2
case "$1 $2" in
  "context inspect") echo unix:///synthetic.sock;;
  "info --format")
    if [[ "$3" = '{{json .DriverStatus}}' ]]; then
      if [[ "${TEST_CONTAINERD:-no}" = yes ]]; then echo '[["driver-type","io.containerd.snapshotter.v1"]]'
      else echo '[]'; fi
    else echo linux/x86_64; fi;;
  "image inspect")
    if [[ "$3" = --format && "$4" = '{{.Id}}' && "$5" = ppe-*:* ]]; then
      image=${5#ppe-}; image=${image%%:*}
      [[ -f "$TEST_ROOT/state/tag-$image" ]] || exit 1
      if [[ "${TEST_CONTAINERD:-no}" = yes ]]; then
        printf 'sha256:'; openssl dgst -sha256 "$TEST_ROOT/state/$image.manifest" | awk '{print $NF}'
      else jq -r '.[0].Id' "$TEST_ROOT/input/$image.json"; fi
      exit 0
    fi
    if [[ "$3" = ppe-*:* ]]; then
      image=${3#ppe-}; image=${image%%:*}
      if [[ "$TEST_CASE" = acquire-tag-collision ]]; then
        jq '.[0].Id="sha256:"+("0"*64)' "$TEST_ROOT/input/$image.json"
      elif [[ -f "$TEST_ROOT/state/tag-$image" ]]; then
        if [[ "${TEST_CONTAINERD:-no}" = yes ]]; then
          digest="sha256:$(openssl dgst -sha256 "$TEST_ROOT/state/$image.manifest" | awk '{print $NF}')"
          jq --arg digest "$digest" '.[0].Id=$digest' "$TEST_ROOT/input/$image.json"
        else cat "$TEST_ROOT/input/$image.json"; fi
      else exit 1; fi
      exit 0
    fi
    for image in app tools db runner broker nginx backup; do
      id=$(jq -r '.[0].Id' "$TEST_ROOT/input/$image.json")
      if [[ "$3" = "$id" || "$3" = *"/ppe/$image@sha256:"* ]]; then
        if [[ ( "$TEST_CASE" = pulled-id-mismatch || "$TEST_CASE" = acquire-id-mismatch ) && "$3" = *"@sha256:"* ]]; then
          jq '.[0].Id="sha256:"+("0"*64)' "$TEST_ROOT/input/$image.json"
        elif [[ "${TEST_CONTAINERD:-no}" = yes ]]; then
          digest="sha256:$(openssl dgst -sha256 "$TEST_ROOT/state/$image.manifest" | awk '{print $NF}')"
          [[ "$TEST_CASE" != acquire-containerd-descriptor-mismatch ]] || digest="sha256:$(printf '%064d' 0)"
          jq --arg digest "$digest" --arg ref "$3" \
            '.[0].Id=$digest|.[0].Descriptor={digest:$digest}|.[0].RepoDigests=[$ref]' "$TEST_ROOT/input/$image.json"
        else cat "$TEST_ROOT/input/$image.json"; fi
        exit 0
      fi
    done
    exit 1;;
  "image rm"|"load -i"|"logout "*) ;;
  "pull --platform")
    if [[ "$TEST_CASE" = acquire-partial-pull && "${*: -1}" = *'/ppe/db@'* ]]; then exit 43; fi;;
  "login --username") cat >/dev/null; echo login >> "$TEST_ROOT/docker-calls";;
  "image ls")
    if [[ "$TEST_CASE" = acquire-tag-collision ]]; then echo "ppe-app:$TEST_SHA"; fi
    for image in app tools db runner broker nginx backup; do
      if [[ -f "$TEST_ROOT/state/tag-$image" ]]; then echo "ppe-$image:$TEST_SHA"; fi
    done;;
  "tag "*)
    if [[ "$3" = ppe-*:* ]]; then
      image=${3#ppe-}; image=${image%%:*}
      touch "$TEST_ROOT/state/tag-$image"
    fi;;
  "push "*)
    image=${2#*/ppe/}; image=${image%%:*}
    echo "$image" >> "$TEST_ROOT/pushes"
    [[ "$TEST_CASE" != partial-push || "$image" != db ]] || exit 43
    touch "$TEST_ROOT/state/$image.pushed";;
  *) exit 1;;
esac
MOCK
cat > "$root/bin/ctr" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "${*:1:6}" = "--address /run/containerd/containerd.sock --namespace moby content get" ]] || exit 1
for image in app tools db runner broker nginx backup; do
  if [[ "${*: -1}" = "$(jq -r '.[0].Id' "$TEST_ROOT/input/$image.json")" ]]; then
    cat "$TEST_ROOT/state/$image.config"
    [[ "$TEST_CASE" != acquire-containerd-config-mismatch ]] || printf 'corrupt'
    exit 0
  fi
done
exit 1
MOCK
cat > "$root/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
method=GET output="" headers="" file="" url="" write=false auth_config=""
while (( $# )); do
    case "$1" in
      --request) method=$2; shift;;
      --output) output=$2; shift;;
      --dump-header) headers=$2; shift;;
      --data-binary) file=${2#@}; shift;;
      --write-out) write=true; shift;;
      --config) auth_config=$2; shift;;
      --header|--proto|--tlsv1.2|--connect-timeout|--max-time)
        [[ "$1" != --tlsv1.2 ]] || { shift; continue; }; shift;;
      https://*) url=$1;;
    esac
    shift
done
if [[ -z "$url" && "$auth_config" = *download-url ]]; then
    url=$(sed -n 's/^url = "\(.*\)"$/\1/p' "$auth_config")
    ! grep -q 'AWS:\|^user' "$auth_config"
    echo no-registry-auth >> "$TEST_ROOT/redirects"
fi
[[ -n "$output" ]] || exit 1
: > "$output"
[[ -z "$headers" ]] || : > "$headers"
if [[ "$url" = https://synthetic.s3.ap-northeast-1.amazonaws.com/* ]]; then
    image=${url#https://synthetic.s3.ap-northeast-1.amazonaws.com/}; image=${image%%/*}
else
    image=${url#*/v2/ppe/}; image=${image%%/*}
fi
if [[ "$method" = GET && "$url" = *'/v2/ppe/'*'/blobs/sha256:'* &&
    ( "$TEST_CASE" = s3-redirect || "$TEST_CASE" = redirect-host-reject ) ]]; then
    digest=${url##*/}
    if [[ "$TEST_CASE" = s3-redirect ]]; then
        printf 'Location: https://synthetic.s3.ap-northeast-1.amazonaws.com/%s/%s\n' "$image" "$digest" > "$headers"
    else printf 'Location: https://untrusted.invalid/redirect\n' > "$headers"; fi
    [[ "$write" = false ]] || printf '307'
    exit 0
fi
if [[ "$method" = POST ]]; then
    echo 'Location: /v2/ppe/releases/blobs/uploads/synthetic' > "$headers"
elif [[ "$method" = PUT && "$url" = *blobs/uploads/* ]]; then
    [[ "$TEST_CASE" != asset-upload-failure ]] || exit 22
    digest=${url##*digest=}
    cp "$file" "$TEST_ROOT/state/blob-${digest#sha256:}"
elif [[ "$method" = PUT ]]; then
    echo release >> "$TEST_ROOT/pushes"
    cp "$file" "$TEST_ROOT/state/releases.manifest"
elif [[ "$url" = *'/manifests/'* ]]; then
    cp "$TEST_ROOT/state/$image.manifest" "$output"
elif [[ "$image" = releases ]]; then
    digest=${url##*/}
    cp "$TEST_ROOT/state/blob-${digest#sha256:}" "$output"
else
    cp "$TEST_ROOT/state/$image.config" "$output"
    [[ "$TEST_CASE" != config-mismatch ]] || printf 'corrupt' >> "$output"
fi
if [[ "$TEST_CASE" = asset-digest-mismatch && "$image" = releases && "$url" = *'/blobs/sha256:'* ]]; then
    printf 'corrupt' >> "$output"
fi
[[ "$write" = false ]] || printf '200'
MOCK
chmod 0700 "$root/bin/"*
count=0
expect_fail() {
    if "$@" > "$root/negative.log" 2>&1; then
        echo "Expected rejection ($TEST_CASE): $*" >&2
        cat "$root/negative.log" >&2
        [[ ! -f "$root/aws-calls" ]] || cat "$root/aws-calls" >&2
        [[ ! -f "$root/pushes" ]] || cat "$root/pushes" >&2
        [[ ! -f "$root/publish-$count/remote.json" ]] || cat "$root/publish-$count/remote.json" >&2
        exit 1
    fi
    count=$((count + 1))
}
gate() { bash "$scripts/ecr-ci-gate.sh" "$1" "$2" "$root/gate-$count"; }
expect_fail gate short HEAD
expect_fail gate "$(printf '%040d' 0)" HEAD
GITHUB_REF=refs/heads/feature expect_fail gate "$sha" HEAD
# A synthetic parent boundary requires no checkout history or author configuration.
parent=$(printf 'Synthetic ancestry fixture\n' |
    GIT_AUTHOR_NAME=Fixture GIT_AUTHOR_EMAIL=fixture@example.invalid \
    GIT_COMMITTER_NAME=Fixture GIT_COMMITTER_EMAIL=fixture@example.invalid \
    git commit-tree "$(git rev-parse 'HEAD^{tree}')")
expect_fail gate "$sha" "$parent"
git clone --quiet --depth 1 --no-checkout "file://$repo" "$root/shallow"
(
    cd "$root/shallow"
    [[ "$(git rev-parse --is-shallow-repository)" = true ]]
    image_build_target "$sha" HEAD
    parent=$(printf 'Synthetic shallow ancestry fixture\n' |
        GIT_AUTHOR_NAME=Fixture GIT_AUTHOR_EMAIL=fixture@example.invalid \
        GIT_COMMITTER_NAME=Fixture GIT_COMMITTER_EMAIL=fixture@example.invalid \
        git commit-tree "$(git rev-parse 'HEAD^{tree}')")
    if bash "$scripts/ecr-ci-gate.sh" "$sha" "$parent" "$root/shallow-gate" > "$root/shallow.log" 2>&1; then
        echo "Shallow checkout accepted a nonmain SHA." >&2; exit 1
    fi
    grep -q 'Invalid SHA or main ancestry' "$root/shallow.log"
)
TEST_CASE=ci-failure expect_fail gate "$sha" HEAD
TEST_CASE=missing-check expect_fail gate "$sha" HEAD
TEST_CASE=check-failure expect_fail gate "$sha" HEAD
for scenario in wrong-job-sha wrong-job-attempt duplicate-check wrong-event wrong-workflow wrong-run-branch; do
    TEST_CASE=$scenario expect_fail gate "$sha" HEAD
done
bash "$scripts/ecr-ci-gate.sh" "$sha" HEAD "$root/good-gate" > "$root/gate.log"
ecr_release_ci_evidence "$root/good-gate/ci.json" "$sha" "$GITHUB_REPOSITORY"
ecr_release_verify_transfer "$root/input" "$sha"
cp "$root/input/transfer.json" "$root/transfer.good"
cp "$root/input/app.json" "$root/app.good"
cp "$root/input/images.tsv" "$root/images.good"
# Fully verify the artifact ZIP, rather than relying on a warning-only downloader.
mkdir "$root/artifact" "$root/artifact/images"
cp "$root/input/"* "$root/artifact/images/"
cp "$root/good-gate/ci.json" "$root/artifact/ci.json"
(cd "$root/artifact" && zip -q -r "$root/artifact.zip" images ci.json)
export TEST_ZIP_HASH
TEST_ZIP_HASH=$(source_hash "$root/artifact.zip")
bash "$scripts/ecr-download-transfer.sh" 99 "$TEST_ZIP_HASH" "$root/download-good" > "$root/download.log"
ecr_release_verify_transfer "$root/download-good/bundle/images" "$sha"
TEST_CASE=zip-corrupt expect_fail bash "$scripts/ecr-download-transfer.sh" 99 "$TEST_ZIP_HASH" "$root/download-bad-$count"
TEST_CASE=wrong-artifact-run expect_fail bash "$scripts/ecr-download-transfer.sh" 99 "$TEST_ZIP_HASH" "$root/download-bad-$count"
cp "$root/artifact.zip" "$root/artifact.good.zip"
echo unexpected > "$root/artifact/unexpected"
(cd "$root/artifact" && zip -q "$root/artifact.zip" unexpected)
TEST_ZIP_HASH=$(source_hash "$root/artifact.zip")
expect_fail bash "$scripts/ecr-download-transfer.sh" 99 "$TEST_ZIP_HASH" "$root/download-bad-$count"
cp "$root/artifact.good.zip" "$root/artifact.zip"
TEST_ZIP_HASH=$(source_hash "$root/artifact.zip")
tail -n +2 "$root/images.good" > "$root/input/images.tsv"
ecr_release_transfer "$root/input" "$sha"
expect_fail ecr_release_verify_transfer "$root/input" "$sha"
cp "$root/images.good" "$root/input/images.tsv"
jq '.buildId="gha-999-1"' "$root/transfer.good" > "$root/input/transfer.json"
expect_fail ecr_release_verify_transfer "$root/input" "$sha"
jq '.files |= .[:-1]' "$root/transfer.good" > "$root/input/transfer.json"
expect_fail ecr_release_verify_transfer "$root/input" "$sha"
cp "$root/transfer.good" "$root/input/transfer.json"
echo tamper >> "$root/input/summary.md"
expect_fail ecr_release_verify_transfer "$root/input" "$sha"
echo synthetic > "$root/input/summary.md"
jq '.[0] |= del(.Id)' "$root/app.good" > "$root/input/app.json"
ecr_release_transfer "$root/input" "$sha"
expect_fail ecr_release_verify_transfer "$root/input" "$sha"
cp "$root/app.good" "$root/input/app.json"
ecr_release_transfer "$root/input" "$sha"
# Native source and Docker save/config validation, all Docker effects mocked.
# The tested repo checkout may not contain an origin/main ref; use a test-only git wrapper.
real_git=$(command -v git)
cat > "$root/bin/git" <<MOCK
#!/usr/bin/env bash
if [[ "\$1 \$2 \$4" = "merge-base --is-ancestor refs/remotes/origin/main" ]]; then exit 0; fi
exec "$real_git" "\$@"
MOCK
chmod 0700 "$root/bin/git"
bash "$scripts/ecr-verify-inputs.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/verify" > "$root/verify.log"
# Docker deduplicates blob files but may reference the same empty layer twice.
cp "$root/input/app.tar" "$root/app.tar.good"
jq '.[0].Layers += [.[0].Layers[0]]' "$root/state/archive-app/manifest.json" > "$root/repeated-manifest.json"
cp "$root/repeated-manifest.json" "$root/state/archive-app/manifest.json"
tar -cf "$root/input/app.tar" -C "$root/state/archive-app" manifest.json \
    "$(jq -r '.[0].Config' "$root/repeated-manifest.json")" \
    "$(jq -r '.[0].Layers[0]' "$root/repeated-manifest.json" | sed 's|/layer.tar$||')"
ecr_release_transfer "$root/input" "$sha"
bash "$scripts/ecr-verify-inputs.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/verify-repeated" > "$root/repeated.log"
tar -rf "$root/input/app.tar" -C "$root/state/archive-app" manifest.json
ecr_release_transfer "$root/input" "$sha"
expect_fail bash "$scripts/ecr-verify-inputs.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/verify-duplicate-$count"
cp "$root/app.tar.good" "$root/input/app.tar"
ecr_release_transfer "$root/input" "$sha"
cp "$root/input/source.tar" "$root/source.good"
printf corrupt >> "$root/input/source.tar"
ecr_release_transfer "$root/input" "$sha"
expect_fail bash "$scripts/ecr-verify-inputs.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/verify-bad-$count"
cp "$root/source.good" "$root/input/source.tar"
cp "$root/input/app.tar" "$root/app.tar.good"
ln -s manifest.json "$root/state/archive-app/link"
tar -rf "$root/input/app.tar" -C "$root/state/archive-app" link
ecr_release_transfer "$root/input" "$sha"
expect_fail bash "$scripts/ecr-verify-inputs.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/verify-bad-$count"
cp "$root/app.tar.good" "$root/input/app.tar"
ecr_release_transfer "$root/input" "$sha"
publish() {
    rm -f "$root/state/"*.pushed "$root/pushes" "$root/aws-calls" "$root/docker-calls"
    bash "$scripts/ecr-publish.sh" "$sha" "$root/input" "$root/good-gate/ci.json" "$root/publish-$count"
}
for scenario in oidc-failure wrong-account duplicate-tag completed-tag access-denied malformed-preflight digest-mismatch config-mismatch pulled-id-mismatch partial-push asset-upload-failure asset-digest-mismatch redirect-host-reject; do
    TEST_CASE=$scenario expect_fail publish
    [[ ! -f "$root/pushes" ]] || ! grep -q '^release$' "$root/pushes"
    if [[ "$scenario" = oidc-failure || "$scenario" = wrong-account || "$scenario" = duplicate-tag || "$scenario" = completed-tag || "$scenario" = access-denied || "$scenario" = malformed-preflight ]]; then
        [[ ! -e "$root/pushes" && ! -e "$root/docker-calls" ]]
    fi
done
publish > "$root/success.log" 2>&1
[[ "$(wc -l < "$root/pushes" | tr -d ' ')" = 8 && "$(tail -1 "$root/pushes")" = release ]]
manifest="$root/publish-$count/assets/release.json"
ecr_release_manifest "$manifest" "$sha"
count=$((count + 1))
TEST_CASE=s3-redirect publish > "$root/redirect-success.log" 2>&1
[[ "$(grep -c '^no-registry-auth$' "$root/redirects")" = 11 &&
    "$(tail -1 "$root/pushes")" = release ]]
count=$((count - 1))
jq '.images|=.[0:6]' "$manifest" > "$root/bad-manifest"
expect_fail ecr_release_manifest "$root/bad-manifest" "$sha"
jq '.images[0].configDigest="sha256:"+("0"*64)' "$manifest" > "$root/bad-manifest"
expect_fail ecr_release_manifest "$root/bad-manifest" "$sha"
jq '.images[0]|=del(.imageId)' "$manifest" > "$root/bad-manifest"
expect_fail ecr_release_manifest "$root/bad-manifest" "$sha"
workflow="$repo/.github/workflows/ecr-release-publish.yml"
# Check credential and job boundaries, and default success-only publishing after OIDC.
grep -Fq "if: \${{ github.ref == 'refs/heads/main' }}" "$workflow"
grep -Fq "needs: build" "$workflow"
[[ "$(grep -c 'id-token: write' "$workflow")" = 1 ]]
verify_line=$(grep -n 'name: Recheck exact CI' "$workflow" | cut -d: -f1)
auth_line=$(grep -n 'name: Configure ECR-only' "$workflow" | cut -d: -f1)
publish_line=$(grep -n 'name: Publish and independently' "$workflow" | cut -d: -f1)
(( verify_line < auth_line && auth_line < publish_line ))
! sed -n "${auth_line},${publish_line}p" "$workflow" | grep -q 'continue-on-error\|always()'
! sed -n "${publish_line},/name: Remove local/p" "$workflow" | grep -q 'always()\|continue-on-error'
grep -Fq 'ARTIFACT_ID: ${{ needs.build.outputs.artifact_id }}' "$workflow"
grep -Fq 'bash scripts/ci/ecr-download-transfer.sh "$ARTIFACT_ID" "$ARTIFACT_DIGEST"' "$workflow"
grep -Fq 'bash scripts/ci/tests/ecr-release-test.sh' "$repo/.github/workflows/ci.yml"
echo "ECR release: $count negative scenarios; exact CI, transfer/source/config, complete 7-image publish/OCI byte roundtrip and OIDC wiring PASS (mock only)."

# Consume the existing producer fixture through the small host adapter.
export TEST_ECR_ACQUIRE=yes PPE_ECR_ACQUISITION_APPROVAL=yes
layer=$(jq -r '.layers[0].digest' "$root/state/releases.manifest")
mkdir "$root/acquire-assets" "$root/acquired"
tar -xf "$root/state/blob-${layer#sha256:}" -C "$root/acquire-assets"
# Small committed-source stand-in keeps acquisition failures cheap and deterministic.
mkdir "$root/acquire-source"
cp "$repo/compose.production.yml" "$root/acquire-source/compose.production.yml"
printf 'synthetic source\n' > "$root/acquire-source/検証.txt"
tar -cf "$root/acquire-assets/source.tar" -C "$root/acquire-source" .
generate_source_manifest "$root/acquire-source" > "$root/acquire-assets/source.sha256"
jq --arg archive "$(source_hash "$root/acquire-assets/source.tar")" \
    --arg manifest "$(source_hash "$root/acquire-assets/source.sha256")" \
    '.ci.repository="toichan/programming-process-evaluator" |
     .source={archiveSha256:$archive,manifestSha256:$manifest}' \
    "$root/acquire-assets/release.json" > "$root/acquire-real-repo.json"
cp "$root/acquire-real-repo.json" "$root/acquire-assets/release.json"
cp "$root/acquire-assets/release.json" "$root/acquire-manifest.good"
acquire_fixture() {
    tar -cf "$root/acquire.tar" -C "$root/acquire-assets" .
    layer="sha256:$(source_hash "$root/acquire.tar")"
    cp "$root/acquire.tar" "$root/state/blob-${layer#sha256:}"
    jq -nc --arg layer "$layer" '{architecture:"amd64",os:"linux",config:{},
        rootfs:{type:"layers",diff_ids:[$layer]}}' > "$root/acquire.config"
    config="sha256:$(source_hash "$root/acquire.config")"
    cp "$root/acquire.config" "$root/state/blob-${config#sha256:}"
    jq -nc --arg config "$config" --arg layer "$layer" \
        --argjson config_size "$(wc -c < "$root/acquire.config" | tr -d ' ')" \
        --argjson layer_size "$(wc -c < "$root/acquire.tar" | tr -d ' ')" \
        '{schemaVersion:2,mediaType:"application/vnd.oci.image.manifest.v1+json",
          config:{mediaType:"application/vnd.oci.image.config.v1+json",digest:$config,size:$config_size},
          layers:[{mediaType:"application/vnd.oci.image.layer.v1.tar",digest:$layer,size:$layer_size}]}' > "$root/state/releases.manifest"
    acquire_digest="sha256:$(source_hash "$root/state/releases.manifest")"
}
acquire() {
    bash "$scripts/../production/prepare-ecr-release.sh" "$sha" "$1" "$root/acquired"
}
acquire_fixture
TEST_CASE=success acquire "$acquire_digest" > "$root/acquire-success.log"
[[ "$(cat "$root/acquired/$sha/commit")" = "$sha" && "$(cat "$root/acquired/$sha/READY")" = "$sha" ]]
for image in app tools db runner broker nginx backup; do
    [[ "$(cat "$root/acquired/$sha/ppe-$image.id")" = "$(jq -r '.[0].Id' "$root/input/$image.json")" ]]
done
verify_hash=$(source_hash "$root/acquired/$sha/source.sha256")
PPE_STATE_DIR="$root" PPE_SOURCE_MANIFEST_SHA256="$verify_hash" \
    verify_source_manifest "$root/acquired/$sha"
sed -n '/^verify_release() {/,/^}/p' "$scripts/../production/operation-common.sh" > "$root/verify-release.sh"
source "$root/verify-release.sh"
project=ppe-sim-ecr-contract
PPE_STATE_DIR="$root" PPE_SOURCE_MANIFEST_SHA256="$verify_hash" verify_release "$root/acquired/$sha"
[[ "$PPE_RELEASE" = "$sha" && "$PPE_COMPOSE_FILE" = "$root/acquired/$sha/source/compose.production.yml" ]]
# Repeat must preserve an immutable existing directory.
expect_fail acquire "$acquire_digest"
[[ "$(source_hash "$root/acquired/$sha/source.sha256")" = "$verify_hash" ]]
rm -rf "$root/acquired/$sha"
TEST_CASE=success acquire "$acquire_digest" > "$root/acquire-reuse.log"
rm -rf "$root/acquired/$sha"
rm "$root/state/tag-"*
expect_fail acquire "sha256:$(printf '%064d' 0)"
for scenario in acquire-id-mismatch acquire-tag-collision acquire-partial-pull oidc-failure; do
    TEST_CASE=$scenario expect_fail acquire "$acquire_digest"
    [[ ! -e "$root/acquired/$sha" && ! -e "$root/state/tag-app" ]]
done
jq '.images|=.[0:6]' "$root/acquire-manifest.good" > "$root/acquire-assets/release.json"
acquire_fixture
expect_fail acquire "$acquire_digest"
cp "$root/acquire-manifest.good" "$root/acquire-assets/release.json"
acquire_fixture
ln -s release.json "$root/acquire-assets/unsafe-link"
acquire_fixture
expect_fail acquire "$acquire_digest"
rm "$root/acquire-assets/unsafe-link"
acquire_fixture
PPE_ECR_ACQUISITION_APPROVAL=no expect_fail acquire "$acquire_digest"
TEST_CONTAINERD=yes TEST_CASE=success acquire "$acquire_digest" > "$root/acquire-containerd.log"
for image in app tools db runner broker nginx backup; do
    [[ "$(cat "$root/acquired/$sha/ppe-$image.id")" = "sha256:$(source_hash "$root/state/$image.manifest")" ]]
done
TEST_CONTAINERD=yes PPE_STATE_DIR="$root" PPE_SOURCE_MANIFEST_SHA256="$verify_hash" verify_release "$root/acquired/$sha"
rm -rf "$root/acquired/$sha"
rm "$root/state/tag-"*
for scenario in acquire-containerd-config-mismatch acquire-containerd-descriptor-mismatch; do
    TEST_CONTAINERD=yes TEST_CASE=$scenario expect_fail acquire "$acquire_digest"
    [[ ! -e "$root/acquired/$sha" && ! -e "$root/state/tag-app" ]]
done
[[ -z "$(find "$root/acquired" -mindepth 1 -print)" ]]
! grep -Eq 'compose|restart|prune' "$root/docker-calls"
echo "ECR acquisition: classic/containerd/reused tags -> legacy verify_release/READY/IDs/source contract PASS; 11 rejection scenarios PASS; no deploy/DB/compose calls (mock only)."
