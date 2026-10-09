#!/usr/bin/env bash

source_hash() { openssl dgst -sha256 "$1" | awk '{print $NF}'; }

generate_source_manifest() {
    local directory=$1 path relative hash
    [[ -d "$directory" && ! -L "$directory" ]] || return 1
    [[ -z "$(find "$directory" ! -type d ! -type f -print)" ]] || {
        echo "Release source contains non-regular assets." >&2; return 1;
    }
    find "$directory" -type f -print0 | while IFS= read -r -d '' path; do
        relative=${path#"$directory/"}
        [[ "$relative" != *\\* && "$relative" != *$'\r'* && "$relative" != *$'\n'* ]] || {
            echo "Unsupported release source filename." >&2; return 1;
        }
        hash=$(source_hash "$path") || return 1
        printf '%s  %s\n' "$hash" "$relative"
    done | LC_ALL=C sort
}

verify_source_manifest() {
    local directory=$1 manifest=${2:-$1/source.sha256} actual
    [[ -f "$manifest" && ! -L "$manifest" \
        && "${PPE_SOURCE_MANIFEST_SHA256:-}" =~ ^[a-f0-9]{64}$ \
        && "$(source_hash "$manifest")" = "$PPE_SOURCE_MANIFEST_SHA256" ]] || {
        echo "Pin the trusted source manifest SHA-256 before deployment." >&2; return 1;
    }
    actual=$(mktemp "$PPE_STATE_DIR/.source-manifest.XXXXXX") || return 1
    if ! generate_source_manifest "$directory/source" > "$actual" || ! cmp -s "$actual" "$manifest"; then
        rm -f "$actual"
        echo "Release source differs from its complete hash manifest." >&2
        return 1
    fi
    rm -f "$actual"
}
