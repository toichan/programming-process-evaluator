#!/usr/bin/env bash

# Caller owns a private work directory/curl-auth file and a fixed registry.
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
