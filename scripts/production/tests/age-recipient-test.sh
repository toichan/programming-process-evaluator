#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
[[ $# = 2 ]] || { echo "Usage: age-recipient-test.sh PUBLIC_RECIPIENT_FILE LOCAL_IDENTITY_FILE" >&2; exit 1; }
recipient_file=$1
identity=$2
scripts=$(cd "$(dirname "$0")/.." && pwd)
repository=$(cd "$scripts/../.." && pwd -P)
for file in "$recipient_file" "$identity"; do
    [[ "$file" = /* && -f "$file" && ! -L "$file" ]] || {
        echo "Tests require existing regular absolute files." >&2; exit 1;
    }
done
identity_directory=$(cd "$(dirname "$identity")" && pwd -P)
[[ "$identity_directory" != "$repository" && "$identity_directory" != "$repository/"* ]] || {
    echo "Identity must remain outside the repository." >&2; exit 1;
}
[[ -z "$(find "$identity" \( ! -user "$(id -u)" -o \( ! -perm 0600 -a ! -perm 0400 \) \) -print)" ]] || {
    echo "Identity must be owned by the current user with mode 0400 or 0600." >&2; exit 1;
}
command -v age >/dev/null
command -v age-keygen >/dev/null
root=$(mktemp -d)
cleanup() {
    rm -f "$root/dummy" "$root/ciphertext" "$root/restored" "$root/invalid" "$root/error"
    rmdir "$root"
}
trap cleanup EXIT
source "$scripts/backup-common.sh"
backup_aws() {
    case "$*" in
        *backup-s3-bucket*) printf 'ppe-synthetic-backup\n' ;;
        *backup-kms-key-arn*) printf 'arn:aws:kms:ap-northeast-1:123456789012:key/11111111-1111-1111-1111-111111111111\n' ;;
        *) echo "Unexpected mocked API." >&2; return 1 ;;
    esac
}
unset PPE_BACKUP_PARAMETER_PREFIX PPE_BACKUP_AGE_RECIPIENT
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_PROFILE
unset AWS_WEB_IDENTITY_TOKEN_FILE AWS_ROLE_ARN
unset AWS_CONTAINER_CREDENTIALS_RELATIVE_URI AWS_CONTAINER_CREDENTIALS_FULL_URI
export PPE_PROJECT=ppe-sim-recipient-test AWS_REGION=ap-northeast-1
export PPE_BACKUP_BUCKET_PARAMETER=/programming-process-evaluator/prod/backup-s3-bucket
export PPE_BACKUP_KMS_PARAMETER=/programming-process-evaluator/prod/backup-kms-key-arn
export PPE_BACKUP_S3_PREFIX=production/mysql PPE_BACKUP_RECIPIENT_FILE="$recipient_file"
export PPE_BACKUP_SNS_TOPIC_ARN=arn:aws:sns:ap-northeast-1:123456789012:synthetic
load_backup_config
[[ "$(age-keygen -y "$identity" 2>"$root/error")" = "$PPE_BACKUP_AGE_RECIPIENT" ]] || {
    echo "The local identity does not match the configured public recipient." >&2; exit 1;
}
printf 'Synthetic age roundtrip data only.\n' > "$root/dummy"
age -R "$recipient_file" -o "$root/ciphertext" "$root/dummy"
age -d -i "$identity" -o "$root/restored" "$root/ciphertext"
cmp "$root/dummy" "$root/restored"
if (unset PPE_BACKUP_AGE_RECIPIENT; PPE_BACKUP_RECIPIENT_FILE="$root/missing"; load_backup_config) \
    2>"$root/error"; then echo "Missing recipient accepted." >&2; exit 1; fi
: > "$root/invalid"
if (unset PPE_BACKUP_AGE_RECIPIENT; PPE_BACKUP_RECIPIENT_FILE="$root/invalid"; load_backup_config) \
    2>"$root/error"; then echo "Empty recipient accepted." >&2; exit 1; fi
printf 'not-an-age-recipient\n' > "$root/invalid"
if (unset PPE_BACKUP_AGE_RECIPIENT; PPE_BACKUP_RECIPIENT_FILE="$root/invalid"; load_backup_config) \
    2>"$root/error"; then echo "Invalid recipient accepted." >&2; exit 1; fi
checksum_suffix=q
[[ "$PPE_BACKUP_AGE_RECIPIENT" != *q ]] || checksum_suffix=p
printf '%s\n' "${PPE_BACKUP_AGE_RECIPIENT%?}$checksum_suffix" > "$root/invalid"
if age -R "$root/invalid" </dev/null >/dev/null 2>"$root/error"; then
    echo "Invalid recipient checksum accepted." >&2; exit 1;
fi
echo "Local file configuration, matching identity, real age roundtrip and missing/empty/invalid/checksum rejection: PASS"
