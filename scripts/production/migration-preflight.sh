#!/usr/bin/env bash

inspect_migration_inventory() {
    local inventory history path version script checksum success extra match applied=0 pending=0
    inventory=$(mktemp "$PPE_STATE_DIR/.migration-inventory.XXXXXX") || return 1
    history=$(mktemp "$PPE_STATE_DIR/.migration-history.XXXXXX") || { rm -f "$inventory"; return 1; }
    for path in "$release/source/src/main/resources/db/migration/"*.sql; do
        [[ -f "$path" ]] || continue
        script=${path##*/}
        [[ "$script" =~ ^V([0-9]+([._][0-9]+)*)__[A-Za-z0-9_-]+\.sql$ ]] || {
            rm -f "$inventory" "$history"
            echo "Only explicitly versioned migrations are supported by deployment inventory." >&2; return 1;
        }
        version=${BASH_REMATCH[1]//_/.}
        printf '%s|%s\n' "$version" "$script" >> "$inventory"
    done
    if [[ ! -s "$inventory" ]] || [[ -n "$(cut -d '|' -f 1 "$inventory" | sort | uniq -d)" ]]; then
        rm -f "$inventory" "$history"
        echo "Migration inventory is empty or has duplicate versions." >&2; return 1
    fi
    if [[ "$(database_table_count)" != 0 ]]; then
        if ! dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names > "$history" <<'SQL'
SELECT CONCAT(COALESCE(version,''),'|',script,'|',COALESCE(checksum,''),'|',success)
FROM ppe.flyway_schema_history ORDER BY installed_rank;
SQL
        then
            rm -f "$inventory" "$history"
            echo "Cannot inspect Flyway history." >&2; return 1
        fi
    fi
    while IFS='|' read -r version script checksum success extra; do
        [[ -n "$version" && "$checksum" =~ ^-?[0-9]+$ && "$success" = 1 && -z "$extra" ]] || {
            rm -f "$inventory" "$history"
            echo "Failed, repeatable or malformed migration history requires manual review." >&2; return 1;
        }
        match=$(awk -F '|' -v v="$version" -v s="$script" '$1==v && $2==s {n++} END {print n+0}' "$inventory")
        [[ "$match" = 1 ]] || {
            rm -f "$inventory" "$history"
            echo "Applied migration version/script is absent from the release." >&2; return 1;
        }
        (( applied += 1 ))
    done < "$history"
    [[ -z "$(cut -d '|' -f 1 "$history" | sort | uniq -d)" ]] || {
        rm -f "$inventory" "$history"
        echo "Duplicate applied migration versions." >&2; return 1;
    }
    target_migrations=$(wc -l < "$inventory" | tr -d ' ')
    pending=$(( target_migrations - applied ))
    migration_pending_count=$pending
    migration_history_hash=$(source_hash "$history")
    migration_inventory_hash=$(source_hash "$inventory")
    rm -f "$inventory" "$history"
    echo "Flyway inventory checked: applied=$applied pending=$pending; checksums require Flyway validate."
}
