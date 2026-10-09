#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
source "$scripts/source-manifest.sh"
source "$scripts/migration-preflight.sh"
root=$(mktemp -d)
cleanup() { find "$root" -type f -delete; find "$root" -depth -type d -exec rmdir '{}' \;; }
trap cleanup EXIT
export PPE_STATE_DIR="$root"
release="$root/release"
mkdir -p "$release/source/src/main/resources/db/migration"
printf '%s\n' '-- synthetic migration 1' > "$release/source/src/main/resources/db/migration/V1__test.sql"
printf '%s\n' '-- synthetic migration 2' > "$release/source/src/main/resources/db/migration/V2__test.sql"
printf '1|V1__test.sql|123|1\n' > "$root/history"
database_table_count() { echo 1; }
dc() { cat >/dev/null; cat "$root/history"; }
inspect_migration_inventory
[[ "$target_migrations" = 2 && "$migration_pending_count" = 1 ]]
baseline=$migration_history_hash
echo "PASS: exact versions/scripts/checksums and pending set inspected"
printf '2|V2__test.sql|456|1\n' >> "$root/history"
inspect_migration_inventory
[[ "$migration_pending_count" = 0 && "$migration_history_hash" != "$baseline" ]]
echo "PASS: complete and changed history distinguished from baseline"
for row in '1|V1__test.sql|123|0' '3|V3__test.sql|123|1' '1|different.sql|123|1' '1|V1__test.sql||1'; do
    printf '%s\n' "$row" > "$root/history"
    if inspect_migration_inventory >/dev/null 2>&1; then echo "FAIL: accepted invalid history" >&2; exit 1; fi
done
echo "PASS: failed, unknown, renamed and malformed applied migrations refused"
printf '1|V1__test.sql|123|1\n1|V1__test.sql|123|1\n' > "$root/history"
if inspect_migration_inventory >/dev/null 2>&1; then exit 1; fi
printf '1|V1__test.sql|123|1\n' > "$root/history"
cp "$release/source/src/main/resources/db/migration/V1__test.sql" "$release/source/src/main/resources/db/migration/V1__duplicate.sql"
if inspect_migration_inventory >/dev/null 2>&1; then exit 1; fi
echo "PASS: duplicate migration versions refused"
