#!/usr/bin/env bash
set -euo pipefail
set +x
source "$(dirname "$0")/backup-common.sh"
: "${PPE_STATE_DIR:?Set the private operation state}"
load_backup_config
unset PPE_BACKUP_RELEASE PPE_BACKUP_BASELINE
if verify_backup_receipt "$PPE_STATE_DIR/last-successful-backup.receipt"; then
    backup_metric FreshBackup 1
    echo "A complete versioned backup exists within the configured freshness window."
else
    backup_metric FreshBackup 0
    notify_backup_failure freshness || true
    exit 1
fi
