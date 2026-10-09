#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
source "$scripts/source-manifest.sh"
root=$(mktemp -d)
cleanup() { find "$root" -type f -delete; find "$root" -type l -delete; find "$root" -depth -type d -exec rmdir '{}' \;; }
trap cleanup EXIT
mkdir -m 700 "$root/state" "$root/source"
export PPE_STATE_DIR="$root/state"
printf 'synthetic\n' > "$root/source/asset with space.txt"
generate_source_manifest "$root/source" > "$root/source.sha256"
export PPE_SOURCE_MANIFEST_SHA256="$(source_hash "$root/source.sha256")"
verify_source_manifest "$root"
echo "PASS: complete source manifest supports spaces and trusted digest"
for condition in changed added missing manifest; do
    cp "$root/source.sha256" "$root/trusted"
    case "$condition" in
        changed) printf 'changed\n' > "$root/source/asset with space.txt" ;;
        added) printf 'added\n' > "$root/source/unexpected" ;;
        missing) mv "$root/source/asset with space.txt" "$root/saved" ;;
        manifest) printf 'forged\n' >> "$root/source.sha256" ;;
    esac
    if verify_source_manifest "$root" >/dev/null 2>&1; then echo "FAIL: accepted $condition" >&2; exit 1; fi
    cp "$root/trusted" "$root/source.sha256"
    [[ "$condition" != missing ]] || mv "$root/saved" "$root/source/asset with space.txt"
    rm -f "$root/source/unexpected"
    printf 'synthetic\n' > "$root/source/asset with space.txt"
    echo "PASS: source manifest rejects $condition assets before deployment"
done
ln -s "$root/trusted" "$root/source/link"
if generate_source_manifest "$root/source" >/dev/null 2>&1; then exit 1; fi
echo "PASS: release symlinks are refused"
