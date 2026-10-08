#!/usr/bin/env bash
set -euo pipefail
set +x
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
if command -v sha256sum >/dev/null; then sha256sum -c "$backup.sha256"; else shasum -a 256 -c "$backup.sha256"; fi
dc() { docker compose -p "$PPE_PROJECT" -f "$compose" "$@"; }
tables=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe';\n" |
    dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)
[[ "$tables" = 0 ]] || { echo "Refusing restore into a nonempty database." >&2; exit 1; }
# Verify authentication before DB writes; never persist a plaintext dump.
stage=$(mktemp -d)
trap 'rm -f "$stage/backup.age"; rmdir "$stage"' EXIT
chmod 0700 "$stage"
cp "$backup" "$stage/backup.age"
docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
    -v "$identity:/run/identity:ro" --user 0:0 \
    "${PPE_BACKUP_IMAGE:-ppe-backup:local}" --decrypt -i /run/identity < "$stage/backup.age" > /dev/null
docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
    -v "$identity:/run/identity:ro" --user 0:0 \
    "${PPE_BACKUP_IMAGE:-ppe-backup:local}" --decrypt -i /run/identity < "$stage/backup.age" |
    dc exec -T db sh /opt/ppe/db-admin.sh
echo "Restored into an isolated empty database. Validate Flyway and application data before any cutover."
