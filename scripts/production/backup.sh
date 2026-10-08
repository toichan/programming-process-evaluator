#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
: "${PPE_SECRETS_DIR:?Set PPE_SECRETS_DIR}"
: "${PPE_BACKUP_DIR:?Set a separate private backup directory}"
: "${PPE_BACKUP_RECIPIENT_FILE:?Set the age public recipient file}"
project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
directory=$PPE_BACKUP_DIR
[[ "$directory" = /* && "$directory" != / && "$directory" != "$HOME" ]] || {
    echo "A dedicated absolute backup directory is required." >&2; exit 1;
}
[[ ! -L "$directory" ]] || { echo "Backup directory cannot be a symlink." >&2; exit 1; }
mkdir -p -m 700 "$directory"
[[ -z "$(find "$directory" -prune ! -perm 0700 -print)" ]] || {
    echo "Backup directory must be private (0700)." >&2; exit 1;
}
retention=${PPE_BACKUP_RETENTION_DAYS:-14}
minimum=${PPE_BACKUP_MIN_FREE_MIB:-1024}
[[ "$retention" =~ ^[1-9][0-9]*$ && "$minimum" =~ ^[1-9][0-9]*$ ]] || {
    echo "Retention/free-space settings must be positive integers." >&2; exit 1;
}
free=$(df -kP "$directory" | awk 'END {print $4}')
(( free >= minimum * 1024 )) || { echo "Insufficient free space for backup." >&2; exit 1; }
recipient=$(cat "$PPE_BACKUP_RECIPIENT_FILE")
[[ "$recipient" =~ ^age1[a-z0-9]{58}$ ]] || { echo "Invalid age public recipient." >&2; exit 1; }
stamp=$(date -u +%Y%m%dT%H%M%SZ)-$$
temporary="$directory/.ppe-$stamp.sql.age.tmp"
target="$directory/ppe-$stamp.sql.age"
trap 'rm -f "$temporary"' EXIT
docker compose -p "$project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh --dump |
    docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
        "${PPE_BACKUP_IMAGE:-ppe-backup:local}" -r "$recipient" > "$temporary"
[[ -s "$temporary" ]] || { echo "Empty encrypted backup." >&2; exit 1; }
mv "$temporary" "$target"
if command -v sha256sum >/dev/null; then
    sha256sum "$target" > "$target.sha256"
else
    shasum -a 256 "$target" > "$target.sha256"
fi
find "$directory" -maxdepth 1 -type f -name 'ppe-*.sql.age' -mtime +"$retention" -exec rm -f '{}' '{}.sha256' \;
printf 'Encrypted backup created: %s\n' "$target"
