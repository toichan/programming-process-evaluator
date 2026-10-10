#!/usr/bin/env bash

image_build_target() {
    [[ "$1" =~ ^[a-f0-9]{40}$ ]] &&
        [[ "$(git rev-parse --verify --end-of-options "$1^{commit}" 2>/dev/null)" = "$1" ]] &&
        git merge-base --is-ancestor "$1" "$2"
}

image_build_metadata() {
    jq -e --arg sha "$2" '
      length == 1 and
      .[0].Os == "linux" and .[0].Architecture == "amd64" and
      (.[0].Id | test("^sha256:[a-f0-9]{64}$")) and
      .[0].Config.Labels["org.opencontainers.image.revision"] == $sha and
      (.[0].Size | type == "number" and . > 0)
    ' "$1" >/dev/null
}

image_build_source_safety() {
    local directory=$1 files status=0
    files=$(find "$directory" -type f \( -name '.env' -o -name '.env.production' \
        -o -name '*.key' -o -name '*.pem' -o -name '*.age' \)) || return 1
    if [[ -n "$files" ]]; then
        echo "Forbidden secret-bearing filename in build source." >&2
        return 1
    fi
    grep -rEl -- '-----BEGIN ([A-Z]+ )?PRIVATE KEY-----|AGE-SECRET-KEY-1[0-9A-Z]{20,}|AKIA[0-9A-Z]{16}' \
        "$directory/src" "$directory/containers" "$directory/docs/rubric" >/dev/null || status=$?
    if (( status == 0 )); then
        echo "Potential embedded private credential in application build inputs." >&2
        return 1
    fi
    (( status == 1 )) || { echo "Build input credential scan failed." >&2; return 1; }
}

image_build_assets() {
    local source_dir=$1 inspection=$2 result=0
    diff -qr "$source_dir/src" "$inspection/tools-src" >/dev/null || {
        echo "Tools source differs from the archived commit." >&2; return 1;
    }
    cmp "$source_dir/src/main/webapp/WEB-INF/web.xml" "$inspection/tools-war/WEB-INF/web.xml" || return 1
    sed '/<http-only>true<\/http-only>/a\
            <secure>true</secure>
' "$source_dir/src/main/webapp/WEB-INF/web.xml" > "$inspection/expected-web.xml"
    cmp "$inspection/expected-web.xml" "$inspection/app-war/WEB-INF/web.xml" || return 1
    cp "$inspection/tools-war/WEB-INF/web.xml" "$inspection/app-war/WEB-INF/web.xml"
    diff -qr "$inspection/app-war" "$inspection/tools-war" >/dev/null || result=1
    cp "$inspection/expected-web.xml" "$inspection/app-war/WEB-INF/web.xml"
    (( result == 0 )) || { echo "Unexpected app/tools WAR difference." >&2; return 1; }
    local war
    for war in app-war tools-war; do
        diff -qr "$source_dir/src/main/resources/db/migration" \
            "$inspection/$war/WEB-INF/classes/db/migration" >/dev/null || {
            echo "WAR migration inventory or bytes differ from source." >&2; return 1;
        }
    done
}
