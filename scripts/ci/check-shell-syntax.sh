#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
cd "$repo"
command -v dash >/dev/null
count=0
while IFS= read -r -d '' file; do
    IFS= read -r interpreter < "$file"
    case "$interpreter" in
        '#!/usr/bin/env bash'|'#!/bin/bash') bash -n "$file" ;;
        '#!/bin/sh') dash -n "$file" ;;
        *) echo "Unsupported Shell interpreter: $file" >&2; exit 1 ;;
    esac
    count=$((count + 1))
done < <(git ls-files --cached --others --exclude-standard -z -- '*.sh')
(( count > 0 )) || { echo "No Shell files were checked." >&2; exit 1; }
printf 'Shell syntax: %s files passed (Bash/dash).\n' "$count"
