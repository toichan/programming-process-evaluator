#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
[[ "$#" = 3 ]] || {
    echo "Usage: download-backup.sh EXACT_MANIFEST_KEY EXACT_VERSION NEW_PRIVATE_DIRECTORY" >&2; exit 1;
}
source "$(dirname "$0")/backup-common.sh"
validate_backup_storage_config
key=$1
version=$2
output=$3
[[ "$key" = "$PPE_BACKUP_S3_PREFIX/"* && "$key" = *.sql.age.manifest \
    && "$key" =~ ^[A-Za-z0-9/_.,-]+$ && "$key" != *..* \
    && "$version" =~ ^[A-Za-z0-9._+/=-]+$ && "$version" != null && "$version" != None ]] || {
    echo "An exact manifest key and non-null version are required." >&2; exit 1;
}
[[ "$output" = /* && "$output" != / && "$output" != *"/../"* && "$output" != *"/./"* \
    && ! -e "$output" && ! -L "$output" ]] || {
    echo "Download requires a new absolute private directory." >&2; exit 1;
}
parent=$(dirname "$output")
[[ -d "$parent" && ! -L "$parent" && -z "$(find "$parent" -prune ! -perm 0700 -print)" ]] || {
    echo "Download parent must already be private (0700)." >&2; exit 1;
}
stage=$(mktemp -d "$parent/.backup-download.XXXXXX")
cleanup() {
    rm -f "$stage/backup.sql.age" "$stage/backup.sql.age.sha256" "$stage/manifest" "$stage/receipt"
    rmdir "$stage"
}
trap cleanup EXIT
metadata=$(backup_aws s3api head-object --bucket "$PPE_BACKUP_S3_BUCKET" --key "$key" \
    --version-id "$version" --checksum-mode ENABLED \
    --query '[Metadata.sha256,ContentLength,ChecksumSHA256]' --output text)
IFS=$'\t' read -r hash size b64 <<< "$metadata"
[[ "$hash" =~ ^[a-f0-9]{64}$ && "$size" =~ ^[1-9][0-9]*$ && "$size" -le 65536 \
    && "$b64" =~ ^[A-Za-z0-9+/]{43}=$ ]] || { echo "Invalid completion manifest metadata." >&2; exit 1; }
verify_backup_object "$key" "$version" "$hash" "$size" "$b64"
backup_aws s3api get-object --bucket "$PPE_BACKUP_S3_BUCKET" --key "$key" \
    --version-id "$version" --checksum-mode ENABLED "$stage/manifest" >/dev/null
[[ "$(backup_hash "$stage/manifest")" = "$hash" && "$(backup_size "$stage/manifest")" = "$size" ]] || {
    echo "Downloaded manifest checksum mismatch." >&2; exit 1;
}
cp "$stage/manifest" "$stage/receipt"
printf 'manifest_key=%s\nmanifest_version=%s\nmanifest_hash=%s\nmanifest_size=%s\nmanifest_base64=%s\n' \
    "$key" "$version" "$hash" "$size" "$b64" >> "$stage/receipt"
unset PPE_BACKUP_RELEASE PPE_BACKUP_BASELINE
export PPE_BACKUP_MAX_AGE_SECONDS=${PPE_BACKUP_MAX_AGE_SECONDS:-2592000}
verify_backup_receipt "$stage/receipt"
for kind in data checksum; do
    object_key=$(receipt_value "$stage/receipt" "${kind}_key")
    object_version=$(receipt_value "$stage/receipt" "${kind}_version")
    file="$stage/backup.sql.age"
    [[ "$kind" = data ]] || file="$file.sha256"
    backup_aws s3api get-object --bucket "$PPE_BACKUP_S3_BUCKET" --key "$object_key" \
        --version-id "$object_version" --checksum-mode ENABLED "$file" >/dev/null
    [[ "$(backup_hash "$file")" = "$(receipt_value "$stage/receipt" "${kind}_hash")" \
        && "$(backup_size "$file")" = "$(receipt_value "$stage/receipt" "${kind}_size")" ]] || {
        echo "Downloaded ciphertext/checksum integrity mismatch." >&2; exit 1;
    }
done
[[ "$(awk 'NR==1 {print $1}' "$stage/backup.sql.age.sha256")" = \
    "$(receipt_value "$stage/receipt" data_hash)" ]] || { echo "Sidecar hash mismatch." >&2; exit 1; }
printf '%s  backup.sql.age\n' "$(receipt_value "$stage/receipt" data_hash)" > "$stage/backup.sql.age.sha256"
mv "$stage" "$output"
trap - EXIT
echo "Exact versioned ciphertext retrieved and verified; no decryption or DB changes performed."
