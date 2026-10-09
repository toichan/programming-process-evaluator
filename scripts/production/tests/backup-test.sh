#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
trap 'echo "Mock test failed at line $LINENO" >&2; [[ ! -f "$root/log" ]] || tail -n 12 "$root/log" >&2' ERR
cleanup() {
    find "$root" -type f -delete
    find "$root" -depth -type d -exec rmdir '{}' \;
}
trap cleanup EXIT
mkdir -m 700 "$root/bin" "$root/state" "$root/backups" "$root/remote" "$root/downloads"
export MOCK_ROOT="$root" PATH="$root/bin:$PATH"
export PPE_PROJECT=ppe-sim-backup-test PPE_OPERATION_APPROVAL=local-test
export PPE_RELEASE=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export PPE_STATE_DIR="$root/state" PPE_SECRETS_DIR="$root/secrets"
export PPE_BACKUP_DIR="$root/backups" PPE_BACKUP_MIN_FREE_MIB=1
export PPE_BACKUP_PARAMETER_PREFIX=/programming-process-evaluator/prod/backup
export PPE_BACKUP_REQUIRE_REMOTE=yes AWS_REGION=ap-northeast-1
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_PROFILE
printf '%s\n' "$PPE_RELEASE" > "$root/state/current-release"
cat > "$root/bin/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
while [[ "$1" = --* ]]; do
    case "$1" in --region) shift 2 ;; --no-cli-pager) shift ;; *) exit 99 ;; esac
done
service=$1; action=$2; shift 2
key="" version="" body="" hex="" b64="" name="" destination="" query="" metric_data=""
while (( $# )); do
    case "$1" in
        --key) key=$2; shift 2 ;;
        --version-id) version=$2; shift 2 ;;
        --body) body=$2; shift 2 ;;
        --metadata) hex=${2#sha256=}; shift 2 ;;
        --checksum-sha256) b64=$2; shift 2 ;;
        --name) name=$2; shift 2 ;;
        --query) query=$2; shift 2 ;;
        --metric-data) metric_data=$2; shift 2 ;;
        --*) shift 2 ;;
        *) destination=$1; shift ;;
    esac
done
case "$service/$action" in
ssm/get-parameter)
    [[ "${FAIL_STAGE:-}" != ssm ]] || exit 12
    case "$name" in
        */s3-bucket|*/backup-s3-bucket) echo ppe-mock-backup ;;
        */s3-prefix) echo production/mysql ;;
        */age-recipient) printf 'age1'; printf 'a%.0s' {1..58}; printf '\n' ;;
        */kms-key-arn|*/backup-kms-key-arn) echo arn:aws:kms:ap-northeast-1:123456789012:key/11111111-1111-1111-1111-111111111111 ;;
        */sns-topic-arn) echo arn:aws:sns:ap-northeast-1:123456789012:ppe-backup ;;
        *) exit 13 ;;
    esac ;;
s3api/put-object)
    kind=data
    [[ "$key" != *.sha256 ]] || kind=checksum
    [[ "$key" != *.manifest ]] || kind=manifest
    [[ "${FAIL_STAGE:-}" != "$kind" ]] || exit 14
    filename=$(basename "$key")
    [[ ! -e "$MOCK_ROOT/remote/$filename" ]] || exit 15
    cp "$body" "$MOCK_ROOT/remote/$filename"
    size=$(wc -c < "$body" | tr -d ' ')
    printf 'v1\t%s\t%s\taws:kms\tarn:aws:kms:ap-northeast-1:123456789012:key/11111111-1111-1111-1111-111111111111\t%s\n' \
        "$size" "$b64" "$hex" > "$MOCK_ROOT/remote/$filename.meta"
    [[ "${FAIL_STAGE:-}" != version ]] || { echo null; exit 0; }
    echo v1 ;;
s3api/head-object)
    [[ "${FAIL_STAGE:-}" != head ]] || exit 16
    [[ "$version" = v1 ]] || exit 17
    if [[ "${FAIL_STAGE:-}" = mismatch ]]; then echo invalid; exit 0; fi
    file="$MOCK_ROOT/remote/$(basename "$key").meta"
    if [[ "$query" = '[Metadata.sha256,ContentLength,ChecksumSHA256]' ]]; then
        awk -F '\t' '{printf "%s\t%s\t%s\n",$6,$2,$3}' "$file"
    else
        cat "$file"
    fi ;;
s3api/get-object)
    [[ "${FAIL_STAGE:-}" != download ]] || exit 18
    cp "$MOCK_ROOT/remote/$(basename "$key")" "$destination"
    [[ "${FAIL_STAGE:-}" != corrupt-download ]] || printf 'corrupt' >> "$destination"
    echo '{}' ;;
sns/publish)
    printf 'notification\n' >> "$MOCK_ROOT/notified"
    [[ "${FAIL_STAGE:-}" != sns && "${FAIL_SNS:-no}" != yes ]] || exit 19
    echo message-id ;;
cloudwatch/put-metric-data)
    [[ "${FAIL_STAGE:-}" != metric ]] || exit 20
    printf '%s\n' "$metric_data" >> "$MOCK_ROOT/metrics" ;;
*) exit 98 ;;
esac
MOCK
cat > "$root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" = context ]]; then
    echo unix:///mock.sock
elif [[ "$1" = inspect ]]; then
    printf 'a%.0s' {1..64}
    if [[ "${FAIL_STAGE:-}" = mount ]]; then
        printf '|true|healthy|ppe-production|db|volume:ppe-production_database:true;|2026-01-01T00:00:00Z\n'
    else
        printf '|true|healthy|ppe-sim-backup-test|db|volume:ppe-sim-backup-test_database:true;|2026-01-01T00:00:00Z\n'
    fi
elif [[ "$1" = compose ]]; then
    printf 'database-access\n' >> "$MOCK_ROOT/database-access"
    if [[ "$*" = *"ps --all -q db"* ]]; then
        printf 'a%.0s' {1..64}; printf '\n'; exit 0
    fi
    if [[ "$*" = *"--skip-column-names"* ]]; then
        cat >/dev/null
        if [[ "${FAIL_STAGE:-}" = engine ]]; then echo 1; else echo 0; fi
        exit 0
    fi
    [[ "${FAIL_STAGE:-}" != dump ]] || exit 21
    printf 'synthetic-SQL-not-to-be-logged\n'
else
    [[ "${FAIL_STAGE:-}" != age ]] || exit 22
    [[ "${FAIL_STAGE:-}" != recipient-checksum ]] || exit 23
    cat >/dev/null
    printf 'synthetic-encrypted-not-to-be-logged\n'
fi
MOCK
chmod 0700 "$root/bin/aws" "$root/bin/docker"
bash "$scripts/backup.sh" > "$root/log" 2>&1
test -s "$root/state/last-successful-backup.receipt"
bash "$scripts/verify-backup-receipt.sh" "$root/state/last-successful-backup.receipt" >> "$root/log" 2>&1
bash "$scripts/check-backup-freshness.sh" >> "$root/log" 2>&1
grep -Fxq "MetricName=BackupSuccess,Dimensions=[{Name=Project,Value=$PPE_PROJECT}],Value=1,Unit=Count" "$root/metrics"
grep -Fxq "MetricName=FreshBackup,Dimensions=[{Name=Project,Value=$PPE_PROJECT}],Value=1,Unit=Count" "$root/metrics"
mv "$root/state/last-successful-backup.receipt" "$root/saved.receipt"
if bash "$scripts/check-backup-freshness.sh" > "$root/log" 2>&1; then exit 1; fi
grep -Fxq "MetricName=FreshBackup,Dimensions=[{Name=Project,Value=$PPE_PROJECT}],Value=0,Unit=Count" "$root/metrics"
test -s "$root/notified"
mv "$root/saved.receipt" "$root/state/last-successful-backup.receipt"
metrics_before=$(sha256sum "$root/metrics")
notifications_before=$(sha256sum "$root/notified")
if FAIL_STAGE=metric bash "$scripts/check-backup-freshness.sh" > "$root/log" 2>&1; then exit 1; fi
test "$metrics_before" = "$(sha256sum "$root/metrics")"
test "$notifications_before" = "$(sha256sum "$root/notified")"
if FAIL_STAGE=ssm bash "$scripts/check-backup-freshness.sh" > "$root/log" 2>&1; then exit 1; fi
test "$metrics_before" = "$(sha256sum "$root/metrics")"
test "$notifications_before" = "$(sha256sum "$root/notified")"
source "$scripts/backup-common.sh"
load_backup_config
manifest_key=$(receipt_value "$root/state/last-successful-backup.receipt" manifest_key)
bash "$scripts/download-backup.sh" "$manifest_key" v1 "$root/downloads/success" >> "$root/log" 2>&1
test -s "$root/downloads/success/backup.sql.age"
for failure in download corrupt-download; do
    if FAIL_STAGE=$failure bash "$scripts/download-backup.sh" "$manifest_key" v1 \
        "$root/downloads/$failure" > "$root/log" 2>&1; then exit 1; fi
    test ! -e "$root/downloads/$failure"
done
test -z "$(find "$root/downloads" -name '.backup-download.*' -print)"
! grep -q 'synthetic-SQL\|synthetic-encrypted\|age1' "$root/log"
for failure in ssm mount engine dump age data checksum manifest head mismatch version metric; do
    before=$(sha256sum "$root/state/last-successful-backup.receipt")
    if FAIL_STAGE=$failure bash "$scripts/backup.sh" > "$root/log" 2>&1; then
        echo "Expected failure at $failure." >&2; exit 1
    fi
    [[ "$before" = "$(sha256sum "$root/state/last-successful-backup.receipt")" ]]
    ! grep -q 'synthetic-SQL\|synthetic-encrypted\|age1' "$root/log"
done
test -s "$root/notified"
if FAIL_STAGE=dump FAIL_SNS=yes bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
grep -q 'SNS notification failed' "$root/log"
test -n "$(find "$root/backups" -name '*.sql.age' -print)"
test -z "$(find "$root/backups" -name '.ppe-*' -print)"
if FAIL_STAGE=head bash "$scripts/check-backup-freshness.sh" > "$root/log" 2>&1; then exit 1; fi
cp "$root/state/last-successful-backup.receipt" "$root/stale"
sed 's/^created=.*/created=1000000000/' "$root/stale" > "$root/state/stale.receipt"
if bash "$scripts/verify-backup-receipt.sh" "$root/state/stale.receipt" > "$root/log" 2>&1; then exit 1; fi
grep -q 'stale or dated in the future' "$root/log"
if AWS_ACCESS_KEY_ID=synthetic bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
if PPE_BACKUP_RELEASE=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb PPE_BACKUP_BASELINE=23 \
    bash "$scripts/verify-backup-receipt.sh" "$root/state/last-successful-backup.receipt" > "$root/log" 2>&1; then exit 1; fi
(
    source "$scripts/database-lock.sh"
    acquire_database_lock
    bash "$scripts/backup.sh" > "$root/log" 2>&1
    if bash -c 'exec 9>&-; source "$1"; acquire_database_lock' bash "$scripts/database-lock.sh" >> "$root/log" 2>&1; then
        echo "Expected independent lock contention." >&2; exit 1
    fi
)
(
    unset PPE_BACKUP_PARAMETER_PREFIX PPE_BACKUP_AGE_RECIPIENT
    export PPE_BACKUP_BUCKET_PARAMETER=/programming-process-evaluator/prod/backup-s3-bucket
    export PPE_BACKUP_KMS_PARAMETER=/programming-process-evaluator/prod/backup-kms-key-arn
    export PPE_BACKUP_RECIPIENT_FILE="$root/public-recipient"
    printf 'age1' > "$PPE_BACKUP_RECIPIENT_FILE"
    printf 'a%.0s' {1..58} >> "$PPE_BACKUP_RECIPIENT_FILE"
    printf '\n' >> "$PPE_BACKUP_RECIPIENT_FILE"
    export PPE_BACKUP_S3_BUCKET=incorrect-local-value PPE_BACKUP_KMS_KEY_ARN=incorrect-local-value
    bash "$scripts/backup.sh" > "$root/log" 2>&1
    bash "$scripts/verify-backup-receipt.sh" "$root/state/last-successful-backup.receipt" >> "$root/log" 2>&1
    grep -q '^bucket=ppe-mock-backup$' "$root/state/last-successful-backup.receipt"
    if PPE_BACKUP_PARAMETER_PREFIX=/programming-process-evaluator/prod/backup \
        bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
    if PPE_BACKUP_KMS_PARAMETER= bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
    if PPE_BACKUP_SNS_TOPIC_ARN= bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
    if PPE_BACKUP_RECIPIENT_FILE="$root/missing-recipient" \
        bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
    for failure in empty invalid checksum; do
        rm -f "$root/database-access"
        case "$failure" in
            empty) : > "$root/public-recipient-invalid" ;;
            invalid) printf 'not-an-age-recipient\n' > "$root/public-recipient-invalid" ;;
            checksum) cp "$PPE_BACKUP_RECIPIENT_FILE" "$root/public-recipient-invalid" ;;
        esac
        if PPE_BACKUP_RECIPIENT_FILE="$root/public-recipient-invalid" \
            FAIL_STAGE="recipient-$failure" bash "$scripts/backup.sh" > "$root/log" 2>&1; then
            echo "Expected public recipient rejection: $failure." >&2; exit 1
        fi
        test ! -e "$root/database-access"
        test -z "$(find "$root/state" -name '.age-recipient.*' -print)"
    done
    if FAIL_STAGE=ssm bash "$scripts/backup.sh" > "$root/log" 2>&1; then exit 1; fi
)
echo "Backup/config/S3/download completion/failure/freshness/provenance/credential rejection and real flock inheritance: PASS"
