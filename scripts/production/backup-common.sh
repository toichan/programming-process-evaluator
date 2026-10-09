#!/usr/bin/env bash

backup_error() { echo "$1" >&2; return 1; }
backup_hash() {
    if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'
    else shasum -a 256 "$1" | awk '{print $1}'; fi
}
backup_size() { wc -c < "$1" | tr -d ' '; }
backup_aws() {
    AWS_MAX_ATTEMPTS=1 AWS_RETRY_MODE=standard \
        aws --region "${AWS_REGION:-ap-northeast-1}" --no-cli-pager "$@" 2>/dev/null
}
validate_backup_storage_config() {
    [[ "${PPE_BACKUP_S3_BUCKET:-}" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ \
        && "$PPE_BACKUP_S3_BUCKET" != *..* ]] || { backup_error "Invalid backup bucket."; return 1; }
    [[ "${PPE_BACKUP_S3_PREFIX:-}" =~ ^[A-Za-z0-9][A-Za-z0-9/_-]*$ \
        && "$PPE_BACKUP_S3_PREFIX" != */ && "$PPE_BACKUP_S3_PREFIX" != *//* ]] ||
        { backup_error "Use a nonempty backup prefix without a trailing slash."; return 1; }
    [[ "${PPE_BACKUP_KMS_KEY_ARN:-}" =~ ^arn:aws:kms:ap-northeast-1:[0-9]{12}:key/[a-f0-9-]{36}$ ]] ||
        { backup_error "Use a Tokyo KMS key ARN, not an alias."; return 1; }
}
validate_backup_cloud_config() {
    validate_backup_storage_config || return 1
    [[ "${PPE_BACKUP_AGE_RECIPIENT:-}" =~ ^age1[a-z0-9]{58}$ ]] ||
        { backup_error "Invalid age public recipient."; return 1; }
    [[ "${PPE_BACKUP_SNS_TOPIC_ARN:-}" =~ ^arn:aws:sns:ap-northeast-1:[0-9]{12}:[A-Za-z0-9_-]+$ ]] ||
        { backup_error "Invalid Tokyo SNS topic ARN."; return 1; }
}
load_backup_config() {
    [[ "${PPE_PROJECT:-ppe-production}" =~ ^ppe-[a-z0-9-]+$ ]] ||
        { backup_error "Invalid backup project name."; return 1; }
    [[ -z "${AWS_ACCESS_KEY_ID:-}" && -z "${AWS_SECRET_ACCESS_KEY:-}" \
        && -z "${AWS_SESSION_TOKEN:-}" && -z "${AWS_PROFILE:-}" \
        && -z "${AWS_WEB_IDENTITY_TOKEN_FILE:-}" && -z "${AWS_ROLE_ARN:-}" \
        && -z "${AWS_CONTAINER_CREDENTIALS_RELATIVE_URI:-}" \
        && -z "${AWS_CONTAINER_CREDENTIALS_FULL_URI:-}" ]] ||
        { backup_error "Backup must use the instance role, not environment credentials/profiles."; return 1; }
    export AWS_SHARED_CREDENTIALS_FILE=/dev/null AWS_CONFIG_FILE=/dev/null AWS_EC2_METADATA_DISABLED=false
    [[ "${AWS_REGION:-ap-northeast-1}" = ap-northeast-1 ]] ||
        { backup_error "This backup configuration requires the Tokyo region."; return 1; }
    if [[ -n "${PPE_BACKUP_BUCKET_PARAMETER:-}" || -n "${PPE_BACKUP_KMS_PARAMETER:-}" ]]; then
        [[ -z "${PPE_BACKUP_PARAMETER_PREFIX:-}" \
            && "${PPE_BACKUP_BUCKET_PARAMETER:-}" = /programming-process-evaluator/prod/backup-s3-bucket \
            && "${PPE_BACKUP_KMS_PARAMETER:-}" = /programming-process-evaluator/prod/backup-kms-key-arn ]] ||
            { backup_error "Use both approved direct parameter names without a parameter prefix."; return 1; }
        local parameter value
        for parameter in "$PPE_BACKUP_BUCKET_PARAMETER" "$PPE_BACKUP_KMS_PARAMETER"; do
            value=$(backup_aws ssm get-parameter --name "$parameter" \
                --query Parameter.Value --output text) ||
                { backup_error "Direct backup parameter retrieval failed."; return 1; }
            [[ -n "$value" && "$value" != None && "$value" != *$'\n'* && "$value" != *$'\r'* ]] ||
                { backup_error "Invalid direct backup parameter."; return 1; }
            if [[ "$parameter" = "$PPE_BACKUP_BUCKET_PARAMETER" ]]; then
                export PPE_BACKUP_S3_BUCKET=$value
            else
                export PPE_BACKUP_KMS_KEY_ARN=$value
            fi
        done
        if [[ -n "${PPE_BACKUP_RECIPIENT_FILE:-}" ]]; then
            [[ "$PPE_BACKUP_RECIPIENT_FILE" = /* \
                && -f "$PPE_BACKUP_RECIPIENT_FILE" && ! -L "$PPE_BACKUP_RECIPIENT_FILE" ]] ||
                { backup_error "Public age recipient requires a regular absolute file."; return 1; }
            value=$(cat "$PPE_BACKUP_RECIPIENT_FILE") || return 1
            [[ -z "${PPE_BACKUP_AGE_RECIPIENT:-}" || "$PPE_BACKUP_AGE_RECIPIENT" = "$value" ]] ||
                { backup_error "Public age recipient sources disagree."; return 1; }
            export PPE_BACKUP_AGE_RECIPIENT=$value
        fi
    elif [[ -n "${PPE_BACKUP_PARAMETER_PREFIX:-}" ]]; then
        [[ "$PPE_BACKUP_PARAMETER_PREFIX" = /programming-process-evaluator/prod/backup ]] ||
            { backup_error "Unexpected backup parameter namespace."; return 1; }
        local name value
        for name in sns-topic-arn s3-bucket s3-prefix age-recipient kms-key-arn; do
            value=$(backup_aws ssm get-parameter --name "$PPE_BACKUP_PARAMETER_PREFIX/$name" \
                --query Parameter.Value --output text) ||
                { backup_error "Backup parameter retrieval failed: $name."; return 1; }
            [[ -n "$value" && "$value" != None && "$value" != *$'\n'* && "$value" != *$'\r'* ]] ||
                { backup_error "Invalid backup parameter: $name."; return 1; }
            case "$name" in
                sns-topic-arn) export PPE_BACKUP_SNS_TOPIC_ARN=$value ;;
                s3-bucket) export PPE_BACKUP_S3_BUCKET=$value ;;
                s3-prefix) export PPE_BACKUP_S3_PREFIX=$value ;;
                age-recipient) export PPE_BACKUP_AGE_RECIPIENT=$value ;;
                kms-key-arn) export PPE_BACKUP_KMS_KEY_ARN=$value ;;
            esac
        done
    fi
    validate_backup_cloud_config
}
notify_backup_failure() {
    local stage=$1
    [[ "${PPE_BACKUP_SNS_TOPIC_ARN:-}" =~ ^arn:aws:sns:ap-northeast-1:[0-9]{12}:[A-Za-z0-9_-]+$ ]] || {
        echo "Notification unavailable; missing-success CloudWatch alarm must cover this failure." >&2; return 1;
    }
    backup_aws sns publish --topic-arn "$PPE_BACKUP_SNS_TOPIC_ARN" \
        --subject "PPE backup requires operator attention" \
        --message "PPE encrypted backup failed at stage=$stage. Inspect the private systemd journal. Do not automatically retry, delete or restore data." \
        --query MessageId --output text >/dev/null || {
            echo "SNS notification failed; check the independent CloudWatch alarm." >&2; return 1;
        }
}
backup_metric() {
    backup_aws cloudwatch put-metric-data --namespace PPE/Backup \
        --metric-data "MetricName=$1,Dimensions=[{Name=Project,Value=${PPE_PROJECT:-ppe-production}}],Value=$2,Unit=Count" ||
        backup_error "Backup monitoring metric publication failed."
}
upload_backup_object() {
    local file=$1 key=$2 hex=$3 size=$4 version actual b64
    [[ "$size" =~ ^[0-9]+$ && "$size" -gt 0 && "$size" -le 4294967296 ]] ||
        { backup_error "Backup object exceeds the supported single-PUT 4 GiB limit or is empty."; return 1; }
    b64=$(openssl dgst -sha256 -binary "$file" | openssl base64 -A)
    version=$(backup_aws s3api put-object --bucket "$PPE_BACKUP_S3_BUCKET" --key "$key" \
        --body "$file" --server-side-encryption aws:kms --ssekms-key-id "$PPE_BACKUP_KMS_KEY_ARN" \
        --checksum-algorithm SHA256 --checksum-sha256 "$b64" --metadata "sha256=$hex" \
        --if-none-match '*' --query VersionId --output text) ||
        { backup_error "S3 upload failed; local ciphertext retained."; return 1; }
    [[ "$version" =~ ^[A-Za-z0-9._+/=-]+$ && "$version" != None && "$version" != null ]] ||
        { backup_error "Versioned S3 upload required; bucket versioning may be disabled."; return 1; }
    verify_backup_object "$key" "$version" "$hex" "$size" "$b64" || return 1
    printf '%s\n' "$version"
}
verify_backup_object() {
    local key=$1 version=$2 hex=$3 size=$4 b64=$5 actual
    actual=$(backup_aws s3api head-object --bucket "$PPE_BACKUP_S3_BUCKET" \
        --key "$key" --version-id "$version" --checksum-mode ENABLED \
        --query '[VersionId,ContentLength,ChecksumSHA256,ServerSideEncryption,SSEKMSKeyId,Metadata.sha256]' \
        --output text) || { backup_error "S3 verification failed."; return 1; }
    [[ "$actual" = "$version"$'\t'"$size"$'\t'"$b64"$'\t'"aws:kms"$'\t'"$PPE_BACKUP_KMS_KEY_ARN"$'\t'"$hex" ]] ||
        backup_error "S3 object version, size, checksum or KMS verification mismatch."
}
receipt_value() {
    local file=$1 key=$2
    awk -v key="$key" 'index($0,key"=")==1 {count++; value=substr($0,length(key)+2)}
        END {if(count!=1) exit 1; print value}' "$file"
}
verify_backup_receipt() {
    local file=$1 key value created now name version hex size b64 localfile
    [[ "$file" = /* && -f "$file" && ! -L "$file" ]] ||
        { backup_error "Backup receipt missing; legacy journal cannot prove remote backup."; return 1; }
    [[ -z "$(find "$file" -prune \( ! -perm 0600 -o ! -user "$(id -u)" \) -print)" ]] ||
        { backup_error "Backup receipt must be private (0600) and owned by this user."; return 1; }
    [[ "$(receipt_value "$file" format)" = 1 \
        && "$(receipt_value "$file" project)" = "${PPE_PROJECT:-ppe-production}" \
        && "$(receipt_value "$file" bucket)" = "$PPE_BACKUP_S3_BUCKET" \
        && "$(receipt_value "$file" prefix)" = "$PPE_BACKUP_S3_PREFIX" \
        && "$(receipt_value "$file" kms)" = "$PPE_BACKUP_KMS_KEY_ARN" ]] ||
        { backup_error "Backup receipt configuration/provenance mismatch."; return 1; }
    [[ "$(receipt_value "$file" age_recipient)" =~ ^age1[a-z0-9]{58}$ ]] ||
        { backup_error "Backup receipt lacks a valid public recipient identifier."; return 1; }
    if [[ -n "${PPE_BACKUP_DEPLOYMENT_ID:-}" ]]; then
        [[ "$PPE_BACKUP_DEPLOYMENT_ID" =~ ^[a-f0-9]{32}$ \
            && "$(receipt_value "$file" age_recipient)" = "$PPE_BACKUP_AGE_RECIPIENT" \
            && "$PPE_BACKUP_AGE_RECIPIENT" = "${PPE_BACKUP_RECOVERY_AGE_RECIPIENT:?}" \
            && "$(receipt_value "$file" deployment_id)" = "$PPE_BACKUP_DEPLOYMENT_ID" \
            && "$(receipt_value "$file" source_container)" = "${PPE_BACKUP_SOURCE_CONTAINER:?}" \
            && "$(receipt_value "$file" source_release)" = "${PPE_BACKUP_SOURCE_RELEASE:?}" \
            && "$(receipt_value "$file" target_release)" = "${PPE_BACKUP_TARGET_RELEASE:?}" \
            && "$(receipt_value "$file" source_volume)" = "${PPE_BACKUP_SOURCE_VOLUME:?}" \
            && "$(receipt_value "$file" source_volume_created)" = "${PPE_BACKUP_SOURCE_VOLUME_CREATED:?}" \
            && "$(receipt_value "$file" history_hash)" = "${PPE_BACKUP_HISTORY_HASH:?}" \
            && "$(receipt_value "$file" inventory_hash)" = "${PPE_BACKUP_INVENTORY_HASH:?}" \
            && "$(receipt_value "$file" recovery_reference_hash)" = "${PPE_BACKUP_RECOVERY_REFERENCE_HASH:?}" ]] ||
            { backup_error "Deployment backup identity or recovery reference mismatch."; return 1; }
    fi
    if [[ -n "${PPE_BACKUP_RELEASE:-}" ]]; then
        [[ "$(receipt_value "$file" release)" = "$PPE_BACKUP_RELEASE" \
            && "$(receipt_value "$file" baseline)" = "${PPE_BACKUP_BASELINE:?}" ]] ||
            { backup_error "Backup receipt release/baseline mismatch."; return 1; }
    fi
    created=$(receipt_value "$file" created)
    now=$(date -u +%s)
    [[ "$created" =~ ^[0-9]{10}$ && "${PPE_BACKUP_MAX_AGE_SECONDS:-93600}" =~ ^[1-9][0-9]*$ ]] ||
        { backup_error "Invalid backup freshness metadata."; return 1; }
    (( now >= created && now - created <= ${PPE_BACKUP_MAX_AGE_SECONDS:-93600} )) ||
        { backup_error "Backup recovery point is stale or dated in the future."; return 1; }
    for name in data checksum manifest; do
        key=$(receipt_value "$file" "${name}_key")
        version=$(receipt_value "$file" "${name}_version")
        hex=$(receipt_value "$file" "${name}_hash")
        size=$(receipt_value "$file" "${name}_size")
        b64=$(receipt_value "$file" "${name}_base64")
        [[ "$key" = "$PPE_BACKUP_S3_PREFIX/"* && "$key" =~ ^[A-Za-z0-9/_.,-]+$ \
            && "$key" != *..* && "$version" =~ ^[A-Za-z0-9._+/=-]+$ \
            && "$version" != None && "$version" != null && "$hex" =~ ^[a-f0-9]{64}$ \
            && "$size" =~ ^[1-9][0-9]*$ && "$b64" =~ ^[A-Za-z0-9+/]{43}=$ ]] ||
            { backup_error "Malformed backup object receipt."; return 1; }
        verify_backup_object "$key" "$version" "$hex" "$size" "$b64" || return 1
    done
    if [[ -n "${PPE_BACKUP_DEPLOYMENT_ID:-}" ]]; then
        local remote expected
        remote=$(mktemp "$PPE_STATE_DIR/.remote-manifest.XXXXXX") || return 1
        expected=$(mktemp "$PPE_STATE_DIR/.receipt-manifest.XXXXXX") || { rm -f "$remote"; return 1; }
        if ! backup_aws s3api get-object --bucket "$PPE_BACKUP_S3_BUCKET" \
            --key "$(receipt_value "$file" manifest_key)" \
            --version-id "$(receipt_value "$file" manifest_version)" --checksum-mode ENABLED "$remote" >/dev/null; then
            rm -f "$remote" "$expected"
            backup_error "Cannot retrieve exact deployment completion manifest."; return 1
        fi
        awk '!/^manifest_(key|version|hash|size|base64)=/' "$file" > "$expected"
        if [[ "$(backup_hash "$remote")" != "$(receipt_value "$file" manifest_hash)" ]] \
            || [[ "$(backup_size "$remote")" != "$(receipt_value "$file" manifest_size)" ]] \
            || ! cmp -s "$remote" "$expected"; then
            rm -f "$remote" "$expected"
            backup_error "Remote completion manifest does not match deployment receipt."; return 1
        fi
        rm -f "$remote" "$expected"
    fi
}
