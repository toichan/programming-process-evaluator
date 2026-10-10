#!/usr/bin/env bash
# Source this helper after operation-common.sh has established PPE_STATE_DIR.
# API: preflight_operation_configs RELEASE_DIRECTORY DB_SOURCE_DIRECTORY DB_RELEASE
#      sync_operation_configs RELEASE_DIRECTORY DB_SOURCE_DIRECTORY DB_RELEASE
#      verify_operation_configs RELEASE_DIRECTORY DB_SOURCE_DIRECTORY DB_RELEASE
# Unfinished journals are preserved and refused for operator review; no service
# or TLS/ACME lifecycle commands are run here.
set +x

load_operation_network_config() {
    local name value bind http_port https_port
    _operation_config_validate_env_file "${PPE_TLS_OPERATION_ENV:-}" TLS || return 1
    _operation_config_parse "$PPE_TLS_OPERATION_ENV" tls || return 1
    bind=${_opcfg_PPE_BIND_ADDRESS:-}
    http_port=${_opcfg_PPE_HTTP_PORT:-}
    https_port=${_opcfg_PPE_HTTPS_PORT:-}
    [[ -n "$bind" && "$bind" =~ ^[a-zA-Z0-9.:_-]+$ ]] || {
        _operation_config_fail "TLS operation environment requires an explicit valid bind address."
        return 1
    }
    for value in "$http_port" "$https_port"; do
        [[ "$value" =~ ^[1-9][0-9]{0,4}$ ]] && (( value <= 65535 )) || {
            _operation_config_fail "TLS operation environment requires explicit valid HTTP/HTTPS ports."
            return 1
        }
    done
    _operation_config_validate_env_file "${PPE_BACKUP_OPERATION_ENV:-}" Backup || return 1
    _operation_config_parse "$PPE_BACKUP_OPERATION_ENV" backup || return 1
    for name in PPE_BIND_ADDRESS PPE_HTTP_PORT PPE_HTTPS_PORT; do
        case "$name" in
            PPE_BIND_ADDRESS)
                value=$bind
                [[ -z "${PPE_BIND_ADDRESS:-}" || "$PPE_BIND_ADDRESS" = "$value" ]] \
                    && [[ -z "${_opcfg_PPE_BIND_ADDRESS:-}" || "$_opcfg_PPE_BIND_ADDRESS" = "$value" ]] ;;
            PPE_HTTP_PORT)
                value=$http_port
                [[ -z "${PPE_HTTP_PORT:-}" || "$PPE_HTTP_PORT" = "$value" ]] \
                    && [[ -z "${_opcfg_PPE_HTTP_PORT:-}" || "$_opcfg_PPE_HTTP_PORT" = "$value" ]] ;;
            PPE_HTTPS_PORT)
                value=$https_port
                [[ -z "${PPE_HTTPS_PORT:-}" || "$PPE_HTTPS_PORT" = "$value" ]] \
                    && [[ -z "${_opcfg_PPE_HTTPS_PORT:-}" || "$_opcfg_PPE_HTTPS_PORT" = "$value" ]] ;;
        esac || {
            _operation_config_fail "Caller/backup network settings disagree with the reviewed TLS operation environment."
            return 1
        }
    done
    export PPE_BIND_ADDRESS="$bind" PPE_HTTP_PORT="$http_port" PPE_HTTPS_PORT="$https_port"
}

_operation_config_fail() {
    printf '%s\n' "$1" >&2
    return 1
}

_operation_config_owned_mode() {
    local path=$1 mode=$2 owner
    [[ ! -L "$path" ]] || return 1
    [[ -e "$path" ]] || return 1
    owner=$(id -un) || return 1
    [[ -n "$(find "$path" -prune -user root -perm "$mode" -print -quit 2>/dev/null)" \
        || -n "$(find "$path" -prune -user "$owner" -perm "$mode" -print -quit 2>/dev/null)" ]]
}

_operation_config_validate_env_file() {
    local file=$1 label=$2
    [[ "$file" = /* && -f "$file" && ! -L "$file" ]] || {
        _operation_config_fail "$label operation environment must be an absolute regular non-symlink file."
        return 1
    }
    _operation_config_owned_mode "$file" 0600 || {
        _operation_config_fail "$label operation environment must be root/current-user owned with mode 0600."
        return 1
    }
}

_operation_config_valid_path() {
    local path=$1
    [[ "$path" =~ ^/[A-Za-z0-9_./:-]+$ \
        && "$path" != *'/../'* && "$path" != *'/./'* \
        && "$path" != */.. && "$path" != */. && "$path" != / ]]
}

_operation_config_parse() {
    local file=$1 mode=$2 line name value seen=$'\n'
    unset _opcfg_PPE_PROJECT _opcfg_PPE_RELEASE _opcfg_PPE_DB_RELEASE \
        _opcfg_PPE_DB_SOURCE_DIR _opcfg_PPE_COMPOSE_FILE _opcfg_PPE_BACKUP_IMAGE \
        _opcfg_PPE_SECRETS_DIR _opcfg_PPE_TLS_DIR _opcfg_PPE_ACME_DIR \
        _opcfg_PPE_STATE_DIR _opcfg_PPE_BACKUP_DIR _opcfg_PPE_BACKUP_RECIPIENT_FILE \
        _opcfg_PPE_CERTBOT_CONFIG_DIR _opcfg_PPE_CERTBOT_WORK_DIR \
        _opcfg_PPE_CERTBOT_LOG_DIR _opcfg_PPE_BIND_ADDRESS _opcfg_PPE_HTTP_PORT _opcfg_PPE_HTTPS_PORT

    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" =~ ^([A-Z_][A-Z0-9_]*)=(.*)$ ]]; then
            name=${BASH_REMATCH[1]}
            value=${BASH_REMATCH[2]}
            case "$name" in
                PPE_PROJECT|PPE_RELEASE|PPE_DB_RELEASE|PPE_DB_SOURCE_DIR|PPE_COMPOSE_FILE|\
                PPE_BACKUP_IMAGE|PPE_SECRETS_DIR|PPE_TLS_DIR|PPE_ACME_DIR|PPE_STATE_DIR|\
                PPE_BACKUP_DIR|PPE_BACKUP_RECIPIENT_FILE|PPE_CERTBOT_CONFIG_DIR|\
                PPE_CERTBOT_WORK_DIR|PPE_CERTBOT_LOG_DIR|PPE_BIND_ADDRESS|PPE_HTTP_PORT|PPE_HTTPS_PORT)
                    [[ "$seen" != *$'\n'"$name"$'\n'* ]] || {
                        _operation_config_fail "Duplicate required assignment in operation environment."
                        return 1
                    }
                    seen+="$name"$'\n'
                    printf -v "_opcfg_$name" '%s' "$value"
                    ;;
            esac
        fi
    done < "$file"

    local required
    local -a common_required=(PPE_PROJECT PPE_RELEASE PPE_DB_RELEASE PPE_DB_SOURCE_DIR
        PPE_COMPOSE_FILE PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR)
    local -a backup_required=(PPE_BACKUP_IMAGE PPE_STATE_DIR PPE_BACKUP_DIR PPE_BACKUP_RECIPIENT_FILE)
    local -a tls_required=(PPE_CERTBOT_CONFIG_DIR PPE_CERTBOT_WORK_DIR PPE_CERTBOT_LOG_DIR)
    # Resolve required names through a fixed allowlist; never evaluate config text.
    for required in "${common_required[@]}"; do
        case "$required" in
            PPE_PROJECT) value=${_opcfg_PPE_PROJECT:-} ;;
            PPE_RELEASE) value=${_opcfg_PPE_RELEASE:-} ;;
            PPE_DB_RELEASE) value=${_opcfg_PPE_DB_RELEASE:-} ;;
            PPE_DB_SOURCE_DIR) value=${_opcfg_PPE_DB_SOURCE_DIR:-} ;;
            PPE_COMPOSE_FILE) value=${_opcfg_PPE_COMPOSE_FILE:-} ;;
            PPE_SECRETS_DIR) value=${_opcfg_PPE_SECRETS_DIR:-} ;;
            PPE_TLS_DIR) value=${_opcfg_PPE_TLS_DIR:-} ;;
            PPE_ACME_DIR) value=${_opcfg_PPE_ACME_DIR:-} ;;
        esac
        [[ "$seen" = *$'\n'"$required"$'\n'* && -n "$value" ]] || {
            _operation_config_fail "Required operation environment assignment is missing."
            return 1
        }
    done
    if [[ "$mode" = backup ]]; then
        for required in "${backup_required[@]}"; do
            case "$required" in
                PPE_BACKUP_IMAGE) value=${_opcfg_PPE_BACKUP_IMAGE:-} ;;
                PPE_STATE_DIR) value=${_opcfg_PPE_STATE_DIR:-} ;;
                PPE_BACKUP_DIR) value=${_opcfg_PPE_BACKUP_DIR:-} ;;
                PPE_BACKUP_RECIPIENT_FILE) value=${_opcfg_PPE_BACKUP_RECIPIENT_FILE:-} ;;
            esac
            [[ "$seen" = *$'\n'"$required"$'\n'* && -n "$value" ]] || {
                _operation_config_fail "Required backup operation environment assignment is missing."
                return 1
            }
        done
    else
        for required in "${tls_required[@]}"; do
            case "$required" in
                PPE_CERTBOT_CONFIG_DIR) value=${_opcfg_PPE_CERTBOT_CONFIG_DIR:-} ;;
                PPE_CERTBOT_WORK_DIR) value=${_opcfg_PPE_CERTBOT_WORK_DIR:-} ;;
                PPE_CERTBOT_LOG_DIR) value=${_opcfg_PPE_CERTBOT_LOG_DIR:-} ;;
            esac
            [[ "$seen" = *$'\n'"$required"$'\n'* && -n "$value" ]] || {
                _operation_config_fail "Required TLS operation environment assignment is missing."
                return 1
            }
        done
    fi

    [[ "$_opcfg_PPE_PROJECT" =~ ^[a-z0-9][a-z0-9_-]*$ \
        && "$_opcfg_PPE_RELEASE" =~ ^[0-9a-f]{40}$ \
        && "$_opcfg_PPE_DB_RELEASE" =~ ^[0-9a-f]{40}$ ]] || {
        _operation_config_fail "Operation environment has an invalid project or release pin."
        return 1
    }
    if [[ "$mode" = backup && "$_opcfg_PPE_BACKUP_IMAGE" != "ppe-backup:$_opcfg_PPE_RELEASE" ]]; then
        _operation_config_fail "Backup image and release pins do not match."
        return 1
    fi
    local path_name path_value
    for path_name in PPE_DB_SOURCE_DIR PPE_COMPOSE_FILE PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR; do
        case "$path_name" in
            PPE_DB_SOURCE_DIR) path_value=$_opcfg_PPE_DB_SOURCE_DIR ;;
            PPE_COMPOSE_FILE) path_value=$_opcfg_PPE_COMPOSE_FILE ;;
            PPE_SECRETS_DIR) path_value=$_opcfg_PPE_SECRETS_DIR ;;
            PPE_TLS_DIR) path_value=$_opcfg_PPE_TLS_DIR ;;
            PPE_ACME_DIR) path_value=$_opcfg_PPE_ACME_DIR ;;
        esac
        _operation_config_valid_path "$path_value" || {
            _operation_config_fail "Operation environment contains an unsafe required path."
            return 1
        }
    done
    if [[ "$mode" = backup ]]; then
        for path_name in PPE_STATE_DIR PPE_BACKUP_DIR PPE_BACKUP_RECIPIENT_FILE; do
            case "$path_name" in
                PPE_STATE_DIR) path_value=$_opcfg_PPE_STATE_DIR ;;
                PPE_BACKUP_DIR) path_value=$_opcfg_PPE_BACKUP_DIR ;;
                PPE_BACKUP_RECIPIENT_FILE) path_value=$_opcfg_PPE_BACKUP_RECIPIENT_FILE ;;
            esac
            _operation_config_valid_path "$path_value" || {
                _operation_config_fail "Backup operation environment contains an unsafe required path."
                return 1
            }
        done
        [[ "$_opcfg_PPE_BACKUP_IMAGE" =~ ^ppe-backup:[0-9a-f]{40}$ ]] || {
            _operation_config_fail "Backup operation environment has an invalid image pin."
            return 1
        }
    else
        local tls_parent
        tls_parent=$(dirname "$_opcfg_PPE_TLS_DIR")
        [[ -d "$tls_parent" && ! -L "$tls_parent" \
            && ! -e "$tls_parent/.ppe-tls-renew-lock" \
            && ! -L "$tls_parent/.ppe-tls-renew-lock" ]] || {
            _operation_config_fail "A TLS renewal deploy hook is active; retry after it releases its private lock."
            return 1
        }
        for path_name in PPE_CERTBOT_CONFIG_DIR PPE_CERTBOT_WORK_DIR PPE_CERTBOT_LOG_DIR; do
            case "$path_name" in
                PPE_CERTBOT_CONFIG_DIR) path_value=$_opcfg_PPE_CERTBOT_CONFIG_DIR ;;
                PPE_CERTBOT_WORK_DIR) path_value=$_opcfg_PPE_CERTBOT_WORK_DIR ;;
                PPE_CERTBOT_LOG_DIR) path_value=$_opcfg_PPE_CERTBOT_LOG_DIR ;;
            esac
            _operation_config_valid_path "$path_value" || {
                _operation_config_fail "TLS operation environment contains an unsafe required path."
                return 1
            }
        done
    fi
}

_operation_config_check_resources() {
    local mode=$1 expected_project=$2
    [[ "$_opcfg_PPE_PROJECT" = "$expected_project" ]] || {
        _operation_config_fail "Operation environment belongs to a different Compose project."
        return 1
    }
    [[ -d "$_opcfg_PPE_DB_SOURCE_DIR" && -f "$_opcfg_PPE_COMPOSE_FILE" ]] || {
        _operation_config_fail "Pinned source or Compose path is missing."
        return 1
    }
    local path_name path_value
    for path_name in PPE_SECRETS_DIR PPE_TLS_DIR PPE_ACME_DIR; do
        case "$path_name" in
            PPE_SECRETS_DIR) path_value=$_opcfg_PPE_SECRETS_DIR ;;
            PPE_TLS_DIR) path_value=$_opcfg_PPE_TLS_DIR ;;
            PPE_ACME_DIR) path_value=$_opcfg_PPE_ACME_DIR ;;
        esac
        [[ -d "$path_value" ]] || {
            _operation_config_fail "A required operation directory is missing."
            return 1
        }
    done
    if [[ "$mode" = backup ]]; then
        [[ "$_opcfg_PPE_STATE_DIR" = "$PPE_STATE_DIR" && -d "$_opcfg_PPE_BACKUP_DIR" \
            && -f "$_opcfg_PPE_BACKUP_RECIPIENT_FILE" && ! -L "$_opcfg_PPE_BACKUP_RECIPIENT_FILE" ]] || {
            _operation_config_fail "A required backup operation path is missing or inconsistent."
            return 1
        }
    else
        for path_name in PPE_CERTBOT_CONFIG_DIR PPE_CERTBOT_WORK_DIR PPE_CERTBOT_LOG_DIR; do
            case "$path_name" in
                PPE_CERTBOT_CONFIG_DIR) path_value=$_opcfg_PPE_CERTBOT_CONFIG_DIR ;;
                PPE_CERTBOT_WORK_DIR) path_value=$_opcfg_PPE_CERTBOT_WORK_DIR ;;
                PPE_CERTBOT_LOG_DIR) path_value=$_opcfg_PPE_CERTBOT_LOG_DIR ;;
            esac
            [[ -d "$path_value" ]] || {
                _operation_config_fail "A required TLS operation directory is missing."
                return 1
            }
        done
    fi
}

_operation_config_inputs() {
    [[ "$#" = 3 ]] || {
        _operation_config_fail "Expected RELEASE_DIRECTORY DB_SOURCE_DIRECTORY DB_RELEASE."
        return 1
    }
    local release_directory=$1 db_source_directory=$2 db_release=$3 release_sha
    [[ "$release_directory" = /* && -d "$release_directory" && ! -L "$release_directory" \
        && -f "$release_directory/commit" && -f "$release_directory/READY" ]] || {
        _operation_config_fail "Release directory or manifest is missing."
        return 1
    }
    release_sha=$(cat "$release_directory/commit") || return 1
    [[ "$release_sha" =~ ^[0-9a-f]{40}$ && "$(cat "$release_directory/READY")" = "$release_sha" ]] || {
        _operation_config_fail "Release manifest is invalid."
        return 1
    }
    [[ "$db_source_directory" = /* && -d "$db_source_directory" && ! -L "$db_source_directory" \
        && "$db_release" =~ ^[0-9a-f]{40}$ ]] || {
        _operation_config_fail "Database source directory or release pin is invalid."
        return 1
    }
    [[ -d "$release_directory/source" && ! -L "$release_directory/source" \
        && -f "$release_directory/source/compose.production.yml" \
        && ! -L "$release_directory/source/compose.production.yml" ]] || {
        _operation_config_fail "Release Compose file is missing."
        return 1
    }
    _opcfg_release_directory=$release_directory
    _opcfg_release_sha=$release_sha
    _opcfg_db_source_directory=$db_source_directory
    _opcfg_db_release=$db_release
    _opcfg_compose_file=$release_directory/source/compose.production.yml
}

_operation_config_id() {
    if [[ -n "${deployment_id:-}" ]]; then
        [[ "$deployment_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || {
            _operation_config_fail "Deployment identifier is not safe for a private journal name."
            return 1
        }
        printf '%s\n' "$deployment_id"
        return 0
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        printf '%s\0' "$_opcfg_release_directory" "$_opcfg_release_sha" \
            "$_opcfg_db_source_directory" "$_opcfg_db_release" \
            "$PPE_BACKUP_OPERATION_ENV" "$PPE_TLS_OPERATION_ENV" | sha256sum | awk '{print $1}'
    else
        printf '%s\0' "$_opcfg_release_directory" "$_opcfg_release_sha" \
            "$_opcfg_db_source_directory" "$_opcfg_db_release" \
            "$PPE_BACKUP_OPERATION_ENV" "$PPE_TLS_OPERATION_ENV" | shasum -a 256 | awk '{print $1}'
    fi
}

_operation_config_sync_root() {
    local root=$PPE_STATE_DIR/operation-config-sync
    if [[ -e "$root" || -L "$root" ]]; then
        [[ -d "$root" && ! -L "$root" ]] && _operation_config_owned_mode "$root" 0700 || {
            _operation_config_fail "Operation-config journal directory is unsafe."
            return 1
        }
        local journal status journal_name
        for journal in "$root"/*; do
            [[ -e "$journal" || -L "$journal" ]] || continue
            journal_name=$(basename "$journal")
            [[ -d "$journal" && ! -L "$journal" \
                && "$journal_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || {
                _operation_config_fail "Unexpected operation-config journal entry; refusing to continue."
                return 1
            }
            _operation_config_owned_mode "$journal" 0700 || {
                _operation_config_fail "Operation-config journal is not privately owned."
                return 1
            }
            status=$journal/status
            [[ -f "$status" && ! -L "$status" ]] || {
                _operation_config_fail "An unfinished operation-config sync needs operator review."
                return 1
            }
            case "$(cat "$status")" in
                completed|rolled_back) ;;
                *) _operation_config_fail "An unfinished operation-config sync needs operator review."; return 1 ;;
            esac
        done
    fi
    _opcfg_sync_root=$root
}

_operation_config_validate_pair() {
    local expected_project=$1
    _operation_config_validate_env_file "$PPE_BACKUP_OPERATION_ENV" Backup || return 1
    _operation_config_validate_env_file "$PPE_TLS_OPERATION_ENV" TLS || return 1
    _operation_config_parse "$PPE_BACKUP_OPERATION_ENV" backup || return 1
    _operation_config_check_resources backup "$expected_project" || return 1
    _operation_config_parse "$PPE_TLS_OPERATION_ENV" tls || return 1
    _operation_config_check_resources tls "$expected_project" || return 1
}

_operation_config_validate_target() {
    local current_project=$1
    [[ "${PPE_PROJECT:-}" = "$current_project" && "$current_project" =~ ^ppe-[a-z0-9-]+$ ]] || {
        _operation_config_fail "The current Compose project is missing or invalid."
        return 1
    }
    _operation_config_valid_path "$_opcfg_release_directory" \
        && _operation_config_valid_path "$_opcfg_db_source_directory" || {
        _operation_config_fail "Release paths must be absolute and traversal-free."
        return 1
    }
}

_operation_config_write_status() {
    local journal=$1 status=$2 temporary=$journal/.status.$$
    printf '%s\n' "$status" > "$temporary" || return 1
    chmod 0600 "$temporary" || return 1
    mv -f "$temporary" "$journal/status" && sync
}

_operation_config_move() {
    mv -f "$1" "$2" && sync
}

_operation_config_render() {
    local file=$1 destination=$2 mode=$3 line newline name value
    local count_release=0 count_compose=0 count_db_release=0 count_db_source=0 count_image=0
    umask 077
    : > "$destination" || return 1
    while :; do
        line=
        if IFS= read -r line; then newline=yes
        elif [[ -n "$line" ]]; then newline=no
        else break
        fi
        if [[ "$line" =~ ^(PPE_RELEASE|PPE_COMPOSE_FILE|PPE_DB_RELEASE|PPE_DB_SOURCE_DIR|PPE_BACKUP_IMAGE)=(.*)$ ]]; then
            name=${BASH_REMATCH[1]}
            case "$name" in
                PPE_RELEASE) value=$_opcfg_release_sha; ((count_release+=1)) ;;
                PPE_COMPOSE_FILE) value=$_opcfg_compose_file; ((count_compose+=1)) ;;
                PPE_DB_RELEASE) value=$_opcfg_db_release; ((count_db_release+=1)) ;;
                PPE_DB_SOURCE_DIR) value=$_opcfg_db_source_directory; ((count_db_source+=1)) ;;
                PPE_BACKUP_IMAGE)
                    if [[ "$mode" = backup ]]; then value=ppe-backup:$_opcfg_release_sha; ((count_image+=1))
                    else value=${BASH_REMATCH[2]}
                    fi
                    ;;
            esac
            line=$name=$value
        fi
        printf '%s' "$line" >> "$destination" || return 1
        [[ "$newline" = no ]] || printf '\n' >> "$destination" || return 1
    done < "$file"
    [[ "$count_release" = 1 && "$count_compose" = 1 && "$count_db_release" = 1 \
        && "$count_db_source" = 1 ]] || {
        _operation_config_fail "Operation environment cannot be safely updated."
        return 1
    }
    [[ "$mode" != backup || "$count_image" = 1 ]] || {
        _operation_config_fail "Backup image pin cannot be safely updated."
        return 1
    }
    chmod 0600 "$destination"
}

_operation_config_restore() {
    local before=$1 target=$2 temporary
    temporary=$(mktemp "$(dirname "$target")/.ppe-operation-restore.XXXXXX") || return 1
    chmod 0600 "$temporary" && cp "$before" "$temporary" || {
        rm -f "$temporary"
        return 1
    }
    if ! _operation_config_move "$temporary" "$target"; then
        rm -f "$temporary"
        return 1
    fi
}

_operation_config_release_lock() {
    rmdir "$1" 2>/dev/null || {
        _operation_config_fail "Operation-config writer lock could not be released; inspect it before retrying."
        return 1
    }
}

preflight_operation_configs() {
    local expected_project=${PPE_PROJECT:-} journal_root lock id
    _operation_config_inputs "$@" || return 1
    _operation_config_validate_target "$expected_project" || return 1
    [[ -n "${PPE_BACKUP_OPERATION_ENV:-}" && -n "${PPE_TLS_OPERATION_ENV:-}" \
        && "$PPE_BACKUP_OPERATION_ENV" != "$PPE_TLS_OPERATION_ENV" ]] || {
        _operation_config_fail "Pin distinct backup and TLS operation environment paths."
        return 1
    }
    [[ "$PPE_STATE_DIR" = /* && -d "$PPE_STATE_DIR" && ! -L "$PPE_STATE_DIR" ]] || {
        _operation_config_fail "A private existing PPE_STATE_DIR is required."
        return 1
    }
    _operation_config_owned_mode "$PPE_STATE_DIR" 0700 || {
        _operation_config_fail "PPE_STATE_DIR must be root/current-user owned with mode 0700."
        return 1
    }
    _operation_config_validate_pair "$expected_project" || return 1
    _operation_config_sync_root || return 1
    journal_root=$_opcfg_sync_root
    lock=$PPE_STATE_DIR/operation-config-sync.lock
    [[ ! -e "$lock" && ! -L "$lock" ]] || {
        _operation_config_fail "Another operation-config sync is active or needs operator review."
        return 1
    }
    id=$(_operation_config_id) || return 1
    _opcfg_journal_id=$id
    for journal in "$journal_root"/*; do
        [[ -e "$journal" || -L "$journal" ]] || continue
        [[ "$(basename "$journal")" != "$id" ]] || {
            [[ -f "$journal/status" && ! -L "$journal/status" \
                && "$(cat "$journal/status")" = completed ]] || {
                _operation_config_fail "This deployment already has an unfinished operation-config journal."
                return 1
            }
        }
    done
    printf '%s\n' "Operation-config preflight passed for the current project; no service was changed."
}

sync_operation_configs() {
    local expected_project=${PPE_PROJECT:-} lock journal id backup_stage tls_stage status
    preflight_operation_configs "$@" || return 1
    lock=$PPE_STATE_DIR/operation-config-sync.lock
    if ! mkdir -m 0700 "$lock" 2>/dev/null; then
        _operation_config_fail "Another operation-config sync is active or needs operator review."
        return 1
    fi
    if ! _operation_config_sync_root; then
        _operation_config_release_lock "$lock" || :
        return 1
    fi
    _opcfg_sync_root=$PPE_STATE_DIR/operation-config-sync
    if [[ ! -d "$_opcfg_sync_root" ]]; then
        if ! mkdir -m 0700 "$_opcfg_sync_root"; then
            _operation_config_release_lock "$lock" || :
            _operation_config_fail "Could not create the private operation-config journal directory."
            return 1
        fi
    fi
    if ! _operation_config_owned_mode "$_opcfg_sync_root" 0700; then
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Operation-config journal directory must be private."
        return 1
    fi
    id=${_opcfg_journal_id:-$(_operation_config_id)}
    journal=$_opcfg_sync_root/$id
    if [[ -d "$journal" ]]; then
        if [[ -f "$journal/status" && ! -L "$journal/status" \
            && "$(cat "$journal/status")" = completed ]] \
            && verify_operation_configs "$@"; then
            _operation_config_release_lock "$lock" || return 1
            printf '%s\n' "Operation-config sync was already completed for this deployment."
            return 0
        fi
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "An existing operation-config journal needs operator review."
        return 1
    fi
    if ! mkdir -m 0700 "$journal"; then
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Could not create the private operation-config journal."
        return 1
    fi
    if ! _operation_config_write_status "$journal" preparing \
        || ! cp "$PPE_BACKUP_OPERATION_ENV" "$journal/backup.before" \
        || ! cp "$PPE_TLS_OPERATION_ENV" "$journal/tls.before" \
        || ! chmod 0600 "$journal/backup.before" "$journal/tls.before"; then
        _operation_config_write_status "$journal" failed >/dev/null 2>&1 || :
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Could not preserve private before-copies for operation-config sync."
        return 1
    fi
    backup_stage=$(mktemp "$(dirname "$PPE_BACKUP_OPERATION_ENV")/.ppe-operation-backup.XXXXXX") || backup_stage=
    tls_stage=$(mktemp "$(dirname "$PPE_TLS_OPERATION_ENV")/.ppe-operation-tls.XXXXXX") || tls_stage=
    if [[ -z "$backup_stage" || -z "$tls_stage" ]] \
        || ! _operation_config_render "$PPE_BACKUP_OPERATION_ENV" "$backup_stage" backup \
            "$_opcfg_release_sha" "$_opcfg_compose_file" "$_opcfg_db_source_directory" "$_opcfg_db_release" \
        || ! _operation_config_render "$PPE_TLS_OPERATION_ENV" "$tls_stage" tls \
            "$_opcfg_release_sha" "$_opcfg_compose_file" "$_opcfg_db_source_directory" "$_opcfg_db_release" \
        || ! _operation_config_write_status "$journal" staged; then
        [[ -z "$backup_stage" ]] || rm -f "$backup_stage"
        [[ -z "$tls_stage" ]] || rm -f "$tls_stage"
        _operation_config_write_status "$journal" failed >/dev/null 2>&1 || :
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Could not stage both operation environments."
        return 1
    fi
    if ! _operation_config_move "$backup_stage" "$PPE_BACKUP_OPERATION_ENV"; then
        rm -f "$backup_stage" "$tls_stage"
        if _operation_config_restore "$journal/backup.before" "$PPE_BACKUP_OPERATION_ENV"; then
            _operation_config_write_status "$journal" rolled_back || {
                _operation_config_fail "Backup was restored but rollback could not be journaled."
            }
        else
            _operation_config_write_status "$journal" recovery_failed || :
            _operation_config_fail "Backup environment install and restoration both failed; preserve evidence."
        fi
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Could not atomically install the backup operation environment."
        return 1
    fi
    _operation_config_write_status "$journal" backup_installed || {
        status=1
        if _operation_config_restore "$journal/backup.before" "$PPE_BACKUP_OPERATION_ENV"; then
            if ! _operation_config_write_status "$journal" rolled_back; then status=3; fi
        else
            _operation_config_write_status "$journal" recovery_failed || :
            status=2
        fi
        rm -f "$tls_stage"
        _operation_config_release_lock "$lock" || :
        if [[ "$status" = 2 ]]; then
            _operation_config_fail "Journal update failed and backup environment restoration also failed; recovery evidence is preserved."
        elif [[ "$status" = 3 ]]; then
            _operation_config_fail "Backup environment was restored but its rollback status could not be journaled; restart will refuse pending operator review."
        else
            _operation_config_fail "Journal update failed; backup environment was restored."
        fi
        return 1
    }
    if ! _operation_config_move "$tls_stage" "$PPE_TLS_OPERATION_ENV"; then
        rm -f "$tls_stage"
        status=0
        _operation_config_restore "$journal/tls.before" "$PPE_TLS_OPERATION_ENV" || status=1
        _operation_config_restore "$journal/backup.before" "$PPE_BACKUP_OPERATION_ENV" || status=1
        if [[ "$status" = 0 ]]; then
            if ! _operation_config_write_status "$journal" rolled_back; then
                _operation_config_fail "Backup environment was restored but its rollback status could not be journaled; restart will refuse pending operator review."
            else
                _operation_config_fail "TLS environment install failed; backup environment was restored."
            fi
            _operation_config_release_lock "$lock" || :
        else
            _operation_config_write_status "$journal" recovery_failed || :
            _operation_config_release_lock "$lock" || :
            _operation_config_fail "TLS environment install and backup restoration both failed; recovery evidence is preserved."
        fi
        return 1
    fi
    if ! _operation_config_write_status "$journal" completed; then
        _operation_config_release_lock "$lock" || :
        _operation_config_fail "Both environments were installed but completion could not be journaled; restart will refuse pending operator review."
        return 1
    fi
    rmdir "$lock" 2>/dev/null || {
        _operation_config_fail "Operation-config sync completed but its writer lock could not be removed."
        return 1
    }
    printf '%s\n' "Operation-config sync completed; before-copies and journal are preserved privately."
}

verify_operation_configs() {
    local expected_project=${PPE_PROJECT:-}
    _operation_config_inputs "$@" || return 1
    _operation_config_validate_target "$expected_project" || return 1
    [[ -n "${PPE_BACKUP_OPERATION_ENV:-}" && -n "${PPE_TLS_OPERATION_ENV:-}" \
        && "$PPE_BACKUP_OPERATION_ENV" != "$PPE_TLS_OPERATION_ENV" ]] || {
        _operation_config_fail "Pin distinct backup and TLS operation environment paths."
        return 1
    }
    [[ "$PPE_STATE_DIR" = /* && -d "$PPE_STATE_DIR" && ! -L "$PPE_STATE_DIR" ]] || {
        _operation_config_fail "A private existing PPE_STATE_DIR is required."
        return 1
    }
    _operation_config_owned_mode "$PPE_STATE_DIR" 0700 || {
        _operation_config_fail "PPE_STATE_DIR must be root/current-user owned with mode 0700."
        return 1
    }
    _operation_config_validate_env_file "$PPE_BACKUP_OPERATION_ENV" Backup || return 1
    _operation_config_parse "$PPE_BACKUP_OPERATION_ENV" backup || return 1
    _operation_config_check_resources backup "$expected_project" || return 1
    [[ "$_opcfg_PPE_RELEASE" = "$_opcfg_release_sha" \
        && "$_opcfg_PPE_DB_RELEASE" = "$_opcfg_db_release" \
        && "$_opcfg_PPE_DB_SOURCE_DIR" = "$_opcfg_db_source_directory" \
        && "$_opcfg_PPE_COMPOSE_FILE" = "$_opcfg_compose_file" \
        && "$_opcfg_PPE_BACKUP_IMAGE" = "ppe-backup:$_opcfg_release_sha" ]] || {
        _operation_config_fail "Operation environments do not match the requested release and database pins."
        return 1
    }
    _operation_config_validate_env_file "$PPE_TLS_OPERATION_ENV" TLS || return 1
    _operation_config_parse "$PPE_TLS_OPERATION_ENV" tls || return 1
    _operation_config_check_resources tls "$expected_project" || return 1
    [[ "$_opcfg_PPE_RELEASE" = "$_opcfg_release_sha" \
        && "$_opcfg_PPE_DB_RELEASE" = "$_opcfg_db_release" \
        && "$_opcfg_PPE_DB_SOURCE_DIR" = "$_opcfg_db_source_directory" \
        && "$_opcfg_PPE_COMPOSE_FILE" = "$_opcfg_compose_file" ]] || {
        _operation_config_fail "Operation environments do not match the requested release and database pins."
        return 1
    }
    printf '%s\n' "Operation environments match the requested release and database pins."
}
