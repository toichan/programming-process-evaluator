#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
: "${PPE_SECRETS_DIR:?Set PPE_SECRETS_DIR}"
: "${PPE_BACKUP_DIR:?Set a separate private backup directory}"
scripts=$(cd "$(dirname "$0")" && pwd)
source "$scripts/backup-common.sh"
source "$scripts/database-lock.sh"
project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
directory=$PPE_BACKUP_DIR
stage=preflight
temporary=""
recipient_file=""
receipt_tmp=""
finish() {
    local status=$?
    trap - EXIT
    [[ -z "$temporary" ]] || rm -f "$temporary"
    [[ -z "$recipient_file" ]] || rm -f "$recipient_file"
    [[ -z "$receipt_tmp" ]] || rm -f "$receipt_tmp"
    if (( status != 0 )); then
        echo "Encrypted backup failed at stage=$stage exit=$status; completed local files retained." >&2
        if [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes ]]; then
            notify_backup_failure "$stage" || true
        fi
    fi
    exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
case "${PPE_OPERATION_APPROVAL:-}" in
    production-approved) [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes ]] ;;
    local-test)
        [[ "$project" =~ ^ppe-(sim|preparation)-[a-z0-9-]+$ ]] || exit 1
        endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
        [[ "${DOCKER_HOST:-$endpoint}" = unix://* ]] || {
            echo "Local backup tests require a Unix Docker endpoint." >&2; exit 1;
        }
        ;;
    *) echo "Backup requires explicit installation approval or a guarded local-test project." >&2; exit 1 ;;
esac
[[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes || "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = no ]] || exit 1
if [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes ]]; then
    stage=parameter-config
    load_backup_config
    [[ -z "${DOCKER_CONTEXT:-}" ]] || { echo "An explicit Docker context is not allowed for cloud backup." >&2; exit 1; }
    export DOCKER_HOST=${DOCKER_HOST:-unix:///var/run/docker.sock}
    [[ "$DOCKER_HOST" = unix://* ]] || { echo "Cloud backup requires the host Unix Docker socket." >&2; exit 1; }
fi
stage=database-lock
acquire_database_lock
if [[ -f "$PPE_STATE_DIR/project" ]]; then
    [[ "$(cat "$PPE_STATE_DIR/project")" = "$project" ]] || {
        echo "Backup state belongs to another Compose project." >&2; exit 1;
    }
fi
if [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes && -z "${PPE_BACKUP_RELEASE:-}" ]]; then
    [[ -f "$PPE_STATE_DIR/current-release" && "${PPE_RELEASE:-}" = "$(cat "$PPE_STATE_DIR/current-release")" ]] || {
        echo "Scheduled backup release configuration differs from current-release; review operation.env." >&2; exit 1;
    }
fi
backup_database_identity() {
    local container metadata id running health db_project service mount started
    container=$(docker compose -p "$project" -f "$compose" ps --all -q db) || return 1
    [[ "$container" =~ ^[a-f0-9]{12,64}$ ]] || { echo "Exactly one existing backup DB is required." >&2; return 1; }
    metadata=$(docker inspect --format \
        '{{.Id}}|{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Type}}:{{index . "Name"}}:{{.RW}};{{end}}{{end}}|{{.State.StartedAt}}' \
        "$container") || return 1
    IFS='|' read -r id running health db_project service mount started <<< "$metadata"
    [[ "$id" =~ ^[a-f0-9]{64}$ && "$running" = true && "$health" = healthy \
        && "$db_project" = "$project" && "$service" = db \
        && "$mount" = "volume:${project}_database:true;" && -n "$started" ]] || {
        echo "Backup source DB health, project or persistent volume mismatch." >&2; return 1;
    }
    printf '%s\n' "$metadata"
}
stage=database-source
source_identity=$(backup_database_identity)
nontransactional=$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ppe' AND engine IS NOT NULL AND engine <> 'InnoDB';\n" |
    docker compose -p "$project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)
[[ "$nontransactional" = 0 ]] || {
    echo "Single-transaction backup requires all ppe base tables to use InnoDB." >&2; exit 1;
}
[[ "$directory" = /* && "$directory" != / && "$directory" != "$HOME" ]] || {
    echo "A dedicated absolute backup directory is required." >&2; exit 1;
}
[[ ! -L "$directory" ]] || { echo "Backup directory cannot be a symlink." >&2; exit 1; }
mkdir -p -m 700 "$directory"
[[ -z "$(find "$directory" -prune \( ! -perm 0700 -o ! -user "$(id -u)" \) -print)" ]] || {
    echo "Backup directory must be owned by this user and private (0700)." >&2; exit 1;
}
minimum=${PPE_BACKUP_MIN_FREE_MIB:-1024}
[[ "$minimum" =~ ^[1-9][0-9]*$ ]] || {
    echo "Free-space setting must be a positive integer." >&2; exit 1;
}
free=$(df -kP "$directory" | awk 'END {print $4}')
(( free >= minimum * 1024 )) || { echo "Insufficient free space for backup." >&2; exit 1; }
if [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = yes ]]; then
    recipient=$PPE_BACKUP_AGE_RECIPIENT
else
    : "${PPE_BACKUP_RECIPIENT_FILE:?Set the age public recipient file for local tests}"
    recipient=$(cat "$PPE_BACKUP_RECIPIENT_FILE")
fi
[[ "$recipient" =~ ^age1[a-z0-9]{58}$ ]] || { echo "Invalid age public recipient." >&2; exit 1; }
recipient_file=$(mktemp "$PPE_STATE_DIR/.age-recipient.XXXXXX")
printf '%s\n' "$recipient" > "$recipient_file"
chmod 0444 "$recipient_file"
created=$(date -u +%s)
temporary=$(mktemp "$directory/.ppe-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")
stamp=$(basename "$temporary")
stamp=${stamp#.ppe-}
target="$directory/ppe-$stamp.sql.age"
stage=dump-encrypt
docker compose -p "$project" -f "$compose" exec -T db sh /opt/ppe/db-admin.sh --dump |
    docker run --rm -i --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
        -v "$recipient_file:/run/recipient:ro" \
        "${PPE_BACKUP_IMAGE:-ppe-backup:local}" -R /run/recipient > "$temporary"
[[ -s "$temporary" ]] || { echo "Empty encrypted backup." >&2; exit 1; }
mv "$temporary" "$target"
temporary=""
[[ "$(backup_database_identity)" = "$source_identity" ]] || {
    echo "DB identity changed during dump; backup not published." >&2; exit 1;
}
stage=local-checksum
hash=$(backup_hash "$target")
printf '%s  %s\n' "$hash" "$(basename "$target")" > "$target.sha256"
[[ "$(backup_hash "$target")" = "$hash" ]] || { echo "Local checksum mismatch." >&2; exit 1; }
if [[ "${PPE_BACKUP_REQUIRE_REMOTE:-yes}" = no ]]; then
    echo "Local-test encrypted backup created; not a production recovery point."
    exit 0
fi
stage=s3-data
data_key="$PPE_BACKUP_S3_PREFIX/$(basename "$target")"
data_size=$(backup_size "$target")
data_version=$(upload_backup_object "$target" "$data_key" "$hash" "$data_size")
checksum_key="$data_key.sha256"
checksum_hash=$(backup_hash "$target.sha256")
checksum_size=$(backup_size "$target.sha256")
stage=s3-checksum
checksum_version=$(upload_backup_object "$target.sha256" "$checksum_key" "$checksum_hash" "$checksum_size")
manifest="$target.manifest"
release=${PPE_BACKUP_RELEASE:-${PPE_RELEASE:?Set the immutable application release}}
baseline=${PPE_BACKUP_BASELINE:-unspecified}
[[ "$release" =~ ^[a-f0-9]{40}$ && ( "$baseline" = unspecified || "$baseline" =~ ^[0-9]+$ ) \
    && "$project" =~ ^ppe-[a-z0-9-]+$ ]] || { echo "Invalid backup provenance." >&2; exit 1; }
{
    printf 'format=1\nproject=%s\nrelease=%s\nbaseline=%s\ncreated=%s\nbucket=%s\nprefix=%s\nkms=%s\n' \
        "$project" "$release" "$baseline" "$created" "$PPE_BACKUP_S3_BUCKET" "$PPE_BACKUP_S3_PREFIX" "$PPE_BACKUP_KMS_KEY_ARN"
    printf 'source_container=%s\n' "${source_identity%%|*}"
    printf 'age_recipient=%s\n' "$recipient"
    printf 'data_key=%s\ndata_version=%s\ndata_hash=%s\ndata_size=%s\ndata_base64=%s\n' \
        "$data_key" "$data_version" "$hash" "$data_size" "$(openssl dgst -sha256 -binary "$target" | openssl base64 -A)"
    printf 'checksum_key=%s\nchecksum_version=%s\nchecksum_hash=%s\nchecksum_size=%s\nchecksum_base64=%s\n' \
        "$checksum_key" "$checksum_version" "$checksum_hash" "$checksum_size" "$(openssl dgst -sha256 -binary "$target.sha256" | openssl base64 -A)"
} > "$manifest"
stage=s3-manifest
manifest_key="$data_key.manifest"
manifest_hash=$(backup_hash "$manifest")
manifest_size=$(backup_size "$manifest")
manifest_version=$(upload_backup_object "$manifest" "$manifest_key" "$manifest_hash" "$manifest_size")
receipt=${PPE_BACKUP_RECEIPT_FILE:-$PPE_STATE_DIR/last-successful-backup.receipt}
[[ "$receipt" = "$PPE_STATE_DIR/"* && ! -L "$receipt" && "$receipt" != *"/../"* \
    && "$receipt" != *"/./"* ]] || { echo "Receipt must be in the private state directory." >&2; exit 1; }
receipt_tmp=$(mktemp "$PPE_STATE_DIR/.backup-receipt.XXXXXX")
cat "$manifest" > "$receipt_tmp"
printf 'manifest_key=%s\nmanifest_version=%s\nmanifest_hash=%s\nmanifest_size=%s\nmanifest_base64=%s\n' \
    "$manifest_key" "$manifest_version" "$manifest_hash" "$manifest_size" \
    "$(openssl dgst -sha256 -binary "$manifest" | openssl base64 -A)" >> "$receipt_tmp"
stage=receipt-verification
verify_backup_receipt "$receipt_tmp"
stage=monitoring
backup_metric BackupSuccess 1
mv "$receipt_tmp" "$receipt"
receipt_tmp=""
if [[ "$receipt" != "$PPE_STATE_DIR/last-successful-backup.receipt" ]]; then
    [[ ! -L "$PPE_STATE_DIR/last-successful-backup.receipt" ]] || exit 1
    receipt_tmp=$(mktemp "$PPE_STATE_DIR/.backup-receipt.XXXXXX")
    cp "$receipt" "$receipt_tmp"
    mv "$receipt_tmp" "$PPE_STATE_DIR/last-successful-backup.receipt"
    receipt_tmp=""
fi
echo "Encrypted backup, checksum and completion manifest verified in versioned S3."
echo "Local ciphertext retained; retention deletion is not performed by this script."
