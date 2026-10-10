#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/image-build-common.sh"
source "$scripts/ecr-release-common.sh"
[[ $# = 3 && "${GITHUB_REF:-}" = refs/heads/main &&
    "${GITHUB_REPOSITORY:-}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ &&
    "$3" = /* && ! -e "$3" && ! -L "$3" ]] || { echo "Invalid main-only CI gate request." >&2; exit 1; }
sha=$1
output=$3
image_build_target "$sha" "$2" || { echo "Invalid SHA or main ancestry." >&2; exit 1; }
mkdir -m 0700 "$output"
gh api "repos/$GITHUB_REPOSITORY/actions/workflows/ci.yml" > "$output/workflow.json"
workflow_id=$(jq -er 'select(.path==".github/workflows/ci.yml" and .state=="active")|.id' "$output/workflow.json")
gh api --paginate --slurp \
    "repos/$GITHUB_REPOSITORY/actions/workflows/$workflow_id/runs?head_sha=$sha&event=push&branch=main&per_page=100" > "$output/runs.json"
run=$(jq -er --arg sha "$sha" --argjson workflow "$workflow_id" '
    [.[].workflow_runs[] | select(.head_sha==$sha and .head_branch=="main" and
      .event=="push" and .workflow_id==$workflow and .status=="completed" and .conclusion=="success")]
    | sort_by(.id) | last | .id // error("No exact successful main CI run")' "$output/runs.json")
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$run" > "$output/run.json"
jq -e --arg sha "$sha" --arg repo "$GITHUB_REPOSITORY" --argjson id "$run" --argjson workflow "$workflow_id" '
    .id==$id and .head_sha==$sha and .head_branch=="main" and .event=="push" and
    .repository.full_name==$repo and .workflow_id==$workflow and .path==".github/workflows/ci.yml" and
    .status=="completed" and .conclusion=="success"' "$output/run.json" >/dev/null
gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/actions/runs/$run/jobs?filter=latest&per_page=100" > "$output/jobs.json"
jq -e --arg sha "$sha" --argjson run "$run" --argjson attempt "$(jq -r '.run_attempt' "$output/run.json")" '
    [.[].jobs[]|select(.name=="Java build and tests" or .name=="Shell syntax and deployment regressions")] |
    length==2 and all(.status=="completed" and .conclusion=="success" and
      .head_sha==$sha and .run_id==$run and .run_attempt==$attempt) and
    (map(.name)|unique|length)==2' "$output/jobs.json" >/dev/null
jq --slurpfile jobs "$output/jobs.json" --arg repo "$GITHUB_REPOSITORY" '
    {sha:.head_sha,repository:$repo,event:.event,branch:.head_branch,workflow:.path,workflowId:(.workflow_id|tostring),
     runId:(.id|tostring),attempt:(.run_attempt|tostring),
     checks:[$jobs[0][].jobs[]|select(.name=="Java build and tests" or .name=="Shell syntax and deployment regressions")|
       {name,status,conclusion}]}' "$output/run.json" > "$output/ci.json"
ecr_release_ci_evidence "$output/ci.json" "$sha" "$GITHUB_REPOSITORY"
echo "Exact successful main CI run and both required checks verified."
