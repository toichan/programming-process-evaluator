#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
source "$(dirname "$0")/backup-common.sh"
[[ "$#" = 2 ]] || { echo "Usage: bash restore-isolated.sh BACKUP.sql.age AGE_IDENTITY_FILE" >&2; exit 1; }
: "${PPE_SECRETS_DIR:?Set PPE_SECRETS_DIR}"
: "${PPE_PROJECT:?Set the disposable restore project}"
[[ "$PPE_PROJECT" =~ ^ppe-restore-[a-z0-9-]+$ ]] || {
    echo "Restore is restricted to an explicitly named ppe-restore-* project." >&2; exit 1;
}
[[ "${PPE_RESTORE_APPROVED:-}" = isolated-empty-db ]] || {
    echo "Explicit isolated-empty-db restore approval is required." >&2; exit 1;
}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
backup=$1
identity=$2
[[ "$backup" = /* && "$identity" = /* ]] || { echo "Absolute file paths are required." >&2; exit 1; }
[[ -s "$backup" && -s "$identity" && -s "$backup.sha256" ]] || {
    echo "Backup, identity or checksum is missing." >&2; exit 1;
}
[[ ! -L "$identity" && -f "$identity" \
    && -z "$(find "$identity" -prune ! -perm 0600 ! -perm 0400 -print)" ]] || {
    echo "Age identity must be a private regular file (0600/0400)." >&2; exit 1;
}
dc() { docker compose -p "$PPE_PROJECT" -f "$compose" "$@"; }
container=$(dc ps --all -q db)
[[ "$container" =~ ^[a-f0-9]{12,64}$ ]] || { echo "Exactly one existing isolated DB is required." >&2; exit 1; }
metadata=$(docker inspect --format \
    '{{.State.Running}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Type}}:{{index . "Name"}}:{{.RW}};{{end}}{{end}}' "$container")
[[ "$metadata" = "true|$PPE_PROJECT|db|volume:${PPE_PROJECT}_database:true;" ]] || {
    echo "Restore DB identity or volume does not belong to the isolated project." >&2; exit 1;
}
tables=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe';\n" |
    dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)
[[ "$tables" = 0 ]] || { echo "Refusing restore into a nonempty database." >&2; exit 1; }
# Verify authentication before DB writes; never persist a plaintext dump.
stage=$(mktemp -d)
trap 'rm -f "$stage/backup.age"; rmdir "$stage"' EXIT
chmod 0700 "$stage"
cp "$backup" "$stage/backup.age"
expected=$(awk 'NR==1 && $1 ~ /^[a-f0-9]+$/ && length($1)==64 {hash=$1}
    END {if(NR!=1 || hash=="") exit 1; print hash}' "$backup.sha256")
[[ "$(backup_hash "$stage/backup.age")" = "$expected" ]] || {
    echo "Ciphertext checksum mismatch; no restore started." >&2; exit 1;
}
docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
    -v "$identity:/run/identity:ro" --user 0:0 \
    "${PPE_BACKUP_IMAGE:-ppe-backup:local}" --decrypt -i /run/identity < "$stage/backup.age" > /dev/null
docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
    -v "$identity:/run/identity:ro" --user 0:0 \
    "${PPE_BACKUP_IMAGE:-ppe-backup:local}" --decrypt -i /run/identity < "$stage/backup.age" |
    dc exec -T db sh /opt/ppe/db-admin.sh
echo "Restored into an isolated empty database. Validate Flyway and application data before any cutover."
