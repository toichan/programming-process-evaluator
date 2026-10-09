#!/usr/bin/env bash
set -Eeuo pipefail
set +x
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
cleanup() {
    find "$root" -type f -delete
    find "$root" -depth -type d -exec rmdir '{}' \;
}
trap cleanup EXIT
mkdir -m 700 "$root/bin"
export MOCK_ROOT="$root" PATH="$root/bin:$PATH" PPE_PROJECT=ppe-restore-mock
export PPE_RESTORE_APPROVED=isolated-empty-db PPE_SECRETS_DIR="$root/secrets"
printf 'synthetic ciphertext' > "$root/backup.age"
printf 'synthetic identity' > "$root/identity"
chmod 0600 "$root/identity"
sha256sum "$root/backup.age" > "$root/backup.age.sha256"
cat > "$root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
    inspect)
        [[ "${FAIL_STAGE:-}" != mount ]] || { echo 'true|ppe-production|db|volume:ppe-production_database:true;'; exit 0; }
        echo 'true|ppe-restore-mock|db|volume:ppe-restore-mock_database:true;' ;;
    compose)
        if [[ "$*" = *"ps --all -q db"* ]]; then
            printf 'a%.0s' {1..64}; printf '\n'
        elif [[ "$*" = *"--skip-column-names"* ]]; then
            cat >/dev/null
            if [[ "${FAIL_STAGE:-}" = nonempty ]]; then echo 1; else echo 0; fi
        else
            cat >/dev/null
            echo restored > "$MOCK_ROOT/written"
        fi ;;
    run)
        cat >/dev/null
        [[ "${FAIL_STAGE:-}" != authentication ]] || exit 12
        echo 'synthetic SQL' ;;
    *) exit 99 ;;
esac
MOCK
chmod 0700 "$root/bin/docker"
bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" > "$root/log" 2>&1
test -f "$root/written"
for failure in mount nonempty authentication; do
    rm -f "$root/written"
    if FAIL_STAGE=$failure bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" \
        > "$root/log" 2>&1; then exit 1; fi
    test ! -e "$root/written"
done
if PPE_PROJECT=ppe-production bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" \
    > "$root/log" 2>&1; then exit 1; fi
printf tamper >> "$root/backup.age"
if bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" > "$root/log" 2>&1; then exit 1; fi
test ! -e "$root/written"
sha256sum "$root/backup.age" > "$root/backup.age.sha256"
chmod 0644 "$root/identity"
if bash "$scripts/restore-isolated.sh" "$root/backup.age" "$root/identity" > "$root/log" 2>&1; then exit 1; fi
test ! -e "$root/written"
echo "Isolated restore, production/mount/nonempty/tamper/authentication/private-key refusal: PASS"
