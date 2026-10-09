#!/usr/bin/env bash
set -euo pipefail
set +x
[[ "$#" = 1 ]] || { echo "Usage: verify-backup-receipt.sh ABSOLUTE_RECEIPT" >&2; exit 1; }
source "$(dirname "$0")/backup-common.sh"
load_backup_config
verify_backup_receipt "$1"
echo "Versioned remote backup receipt and freshness verified."
