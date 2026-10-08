#!/usr/bin/env bash
set -euo pipefail
set +x

project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}

dc() { docker compose -p "$project" -f "$compose" "$@"; }
migration_has_super() {
    local grants
    grants=$(printf "SHOW GRANTS FOR 'ppe_migrate'@'%%';\n" |
        dc exec -T db sh /opt/ppe/db-admin.sh --skip-column-names)
    printf '%s\n' "$grants" | tr -d "\`'" |
        grep -Eiq 'GRANT (SUPER|ALL PRIVILEGES) ON \*\.\* TO ppe_migrate@%'
}

if migration_has_super; then
    printf "REVOKE SUPER ON *.* FROM 'ppe_migrate'@'%%';\n" |
        dc exec -T db sh /opt/ppe/db-admin.sh
    if migration_has_super; then
        echo "Temporary migration SUPER privilege remains; stop before migration." >&2
        exit 1
    fi
    echo "Residual migration SUPER privilege was revoked and verified."
fi
