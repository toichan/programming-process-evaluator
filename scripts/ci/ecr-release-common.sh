#!/usr/bin/env bash

ecr_release_identity() {
    [[ "${GITHUB_RUN_ID:-}" =~ ^[1-9][0-9]*$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[1-9][0-9]*$ &&
        "${GITHUB_SHA:-}" =~ ^[a-f0-9]{40}$ ]] ||
        { echo "Invalid Actions build identity." >&2; return 1; }
}

ecr_release_platform() {
    [[ "$(uname -s)" = Linux && "$(uname -m)" = x86_64 &&
        "$(docker info --format '{{.OSType}}/{{.Architecture}}')" = linux/x86_64 ]] ||
        { echo "Publishing requires a native Linux amd64 daemon and runner." >&2; return 1; }
    [[ "$(docker context inspect --format '{{.Endpoints.docker.Host}}')" = unix://* &&
        "${DOCKER_HOST:-unix://local}" = unix://* ]] ||
        { echo "Remote Docker daemons are forbidden." >&2; return 1; }
}

ecr_release_files() {
    printf '%s\n' source.tar source.sha256 images.tsv summary.md disk.tsv docker-disk.txt
    local image
    for image in app tools db runner broker nginx backup; do printf '%s\n' "$image.json" "$image.tar"; done
}

ecr_release_transfer() {
    local directory=$1 sha=$2 file
    : > "$directory/file-hashes.jsonl"
    while IFS= read -r file; do
        jq -nc --arg name "$file" --arg hash "$(source_hash "$directory/$file")" \
            --argjson size "$(wc -c < "$directory/$file" | tr -d ' ')" \
            '{name:$name,sha256:$hash,size:$size}' >> "$directory/file-hashes.jsonl"
    done < <(ecr_release_files)
    jq -s --arg sha "$sha" --arg workflow "$GITHUB_SHA" --arg run "$GITHUB_RUN_ID" --arg attempt "$GITHUB_RUN_ATTEMPT" \
        '{schema:1,sha:$sha,runId:$run,attempt:$attempt,buildId:("gha-"+$run+"-"+$attempt),
          workflowSha:$workflow,platform:"linux/amd64",files:.}' "$directory/file-hashes.jsonl" > "$directory/transfer.json"
    rm "$directory/file-hashes.jsonl"
}

ecr_release_ci_evidence() {
    jq -e --arg sha "$2" --arg repo "$3" '
      keys == ["attempt","branch","checks","event","repository","runId","sha","workflow","workflowId"] and
      .sha == $sha and .repository == $repo and .event == "push" and .branch == "main" and
      .workflow == ".github/workflows/ci.yml" and
      ([.runId,.attempt,.workflowId] | all(test("^[1-9][0-9]*$"))) and
      (.checks | sort_by(.name) | map(.name)) ==
        ["Java build and tests","Shell syntax and deployment regressions"] and
      (.checks | all(keys == ["conclusion","name","status"] and
        .conclusion == "success" and .status == "completed"))
    ' "$1" >/dev/null
}

ecr_release_verify_transfer() {
    local directory=$1 sha=$2 file names expected id
    ecr_release_identity || return 1
    expected=$(ecr_release_files | jq -R . | jq -sc 'sort')
    jq -e --arg sha "$sha" --arg workflow "$GITHUB_SHA" --arg run "$GITHUB_RUN_ID" --arg attempt "$GITHUB_RUN_ATTEMPT" \
        --argjson names "$expected" '
      keys == ["attempt","buildId","files","platform","runId","schema","sha","workflowSha"] and
      .schema == 1 and .sha == $sha and .runId == $run and .attempt == $attempt and
      .workflowSha == $workflow and
      .buildId == ("gha-"+$run+"-"+$attempt) and .platform == "linux/amd64" and
      (.files | map(.name) | sort) == $names and
      (.files | all(keys == ["name","sha256","size"] and
        (.sha256|test("^[a-f0-9]{64}$")) and (.size|type=="number" and .>0 and floor==.)))
    ' "$directory/transfer.json" >/dev/null || return 1
    [[ -z "$(find "$directory" ! -type d ! -type f -print)" ]] || return 1
    names=$(find "$directory" -type f | sed "s|^$directory/||" | jq -R . | jq -sc 'sort')
    [[ "$names" = "$(jq -nc --argjson names "$expected" '$names+["transfer.json"]|sort')" ]] || return 1
    while IFS= read -r file; do
        [[ "$(source_hash "$directory/$file")" = "$(jq -r --arg file "$file" '.files[]|select(.name==$file)|.sha256' "$directory/transfer.json")" &&
            "$(wc -c < "$directory/$file" | tr -d ' ')" = "$(jq -r --arg file "$file" '.files[]|select(.name==$file)|.size' "$directory/transfer.json")" ]] || return 1
    done < <(ecr_release_files)
    [[ "$(wc -l < "$directory/images.tsv" | tr -d ' ')" = 8 ]] || return 1
    [[ "$(head -n 1 "$directory/images.tsv")" = $'image\timage_id\trevision\tplatform\tsize_bytes\tbuild_seconds' ]] || return 1
    for file in app tools db runner broker nginx backup; do
        image_build_metadata "$directory/$file.json" "$sha" || return 1
        jq -e --arg image "$file" --arg sha "$sha" '
          .[0].RepoTags | length==1 and
          (.[0] | test("^ppe-ci-build-[a-f0-9]{16}-"+$image+":"+$sha+"$"))
        ' "$directory/$file.json" >/dev/null || return 1
        id=$(jq -r '.[0].Id' "$directory/$file.json")
        [[ "$(awk -F '\t' -v image="$file" -v id="$id" -v sha="$sha" \
            '$1==image && $2==id && $3==sha && $4=="linux/amd64" {n++} END{print n+0}' "$directory/images.tsv")" = 1 ]] || return 1
    done
}

ecr_release_manifest() {
    jq -e --arg sha "$2" --arg workflow "$GITHUB_SHA" --arg run "$GITHUB_RUN_ID" --arg attempt "$GITHUB_RUN_ATTEMPT" \
        --arg registry "024378233912.dkr.ecr.ap-northeast-1.amazonaws.com" '
      keys == ["attempt","buildId","ci","images","platform","reports","runId","schema","sha","source","tag","workflowSha"] and
      .schema == 1 and .sha == $sha and .runId == $run and .attempt == $attempt and
      .workflowSha == $workflow and
      .buildId == ("gha-"+$run+"-"+$attempt) and
      .tag == ("sha-"+$sha+"-gha-"+$run+"-"+$attempt) and .platform == "linux/amd64" and
      (.images|map(.name)|sort) == ["app","backup","broker","db","nginx","runner","tools"] and
      (.images|all(
        keys == ["configDigest","digest","digestType","imageId","name","platform","repository","revision"] and
        .repository == ($registry+"/ppe/"+.name) and .revision == $sha and
        .platform == "linux/amd64" and .digestType == "manifest" and
        (.digest|test("^sha256:[a-f0-9]{64}$")) and
        (.imageId|test("^sha256:[a-f0-9]{64}$")) and .configDigest == .imageId)) and
      (.source|keys == ["archiveSha256","manifestSha256"]) and
      (.source|all(test("^[a-f0-9]{64}$"))) and
      (.reports|keys == ["transferSha256"]) and
      (.reports.transferSha256|test("^[a-f0-9]{64}$"))
    ' "$1" >/dev/null &&
        jq '.ci' "$1" | ecr_release_ci_evidence /dev/stdin "$2" "$GITHUB_REPOSITORY"
}
