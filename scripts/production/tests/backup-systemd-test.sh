#!/usr/bin/env bash
set -euo pipefail
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
cleanup() {
    rm -f "$root/docker.service" "$root/ppe-backup.service" "$root/ppe-backup.timer" \
        "$root/ppe-backup-freshness.service" "$root/ppe-backup-freshness.timer"
    rmdir "$root"
}
trap cleanup EXIT
cat > "$root/docker.service" <<'UNIT'
[Unit]
Description=Mock dependency for syntax validation only
[Service]
Type=oneshot
ExecStart=/usr/bin/true
UNIT
for unit in ppe-backup.service ppe-backup.timer ppe-backup-freshness.service ppe-backup-freshness.timer; do
    cp "$scripts/../../containers/production/$unit" "$root/$unit"
done
systemd-analyze verify --man=no "$root/docker.service" "$root/ppe-backup.service" \
    "$root/ppe-backup.timer" "$root/ppe-backup-freshness.service" "$root/ppe-backup-freshness.timer"
echo "Backup unit syntax/dependencies with inert Docker fixture: PASS"
