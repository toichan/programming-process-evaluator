#!/usr/bin/env bash

acquire_database_lock() {
    : "${PPE_STATE_DIR:?Set the shared private state directory}"
    [[ "$PPE_STATE_DIR" = /* && "$PPE_STATE_DIR" != / && "$PPE_STATE_DIR" != "$HOME" \
        && ! -L "$PPE_STATE_DIR" ]] || return 1
    mkdir -p -m 700 "$PPE_STATE_DIR" || return 1
    [[ -z "$(find "$PPE_STATE_DIR" -prune \( ! -perm 0700 -o ! -user "$(id -u)" \) -print)" ]] || {
        echo "Database operation state must be owned by this user and private (0700)." >&2; return 1;
    }
    command -v flock >/dev/null || {
        echo "Linux flock is required for database operation exclusion." >&2; return 1;
    }
    local path="$PPE_STATE_DIR/database-operation.lock" descriptor="/proc/$$/fd/9"
    [[ ! -L "$path" && ( ! -e "$path" || -f "$path" ) ]] || {
        echo "Invalid database operation lock file." >&2; return 1;
    }
    if [[ -e "$descriptor" ]]; then
        [[ -f "$path" && "$(stat -Lc '%d:%i' "$descriptor")" = "$(stat -Lc '%d:%i' "$path")" ]] || {
            echo "Inherited descriptor does not reference this database lock." >&2; return 1;
        }
    else
        exec 9>> "$path"
    fi
    chmod 0600 "$path" || return 1
    flock -n 9 || {
        echo "Another backup/deployment owns the database operation lock." >&2; return 1;
    }
    # Children reuse the open description; never unlock it in a child.
}
