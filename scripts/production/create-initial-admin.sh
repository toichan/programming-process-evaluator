#!/usr/bin/env bash
set -euo pipefail
set +x

[[ "$#" = 0 ]] || { echo "Usage: bash scripts/production/create-initial-admin.sh" >&2; exit 2; }
[[ -t 0 && -t 1 ]] || {
    echo "Run this command from an interactive terminal; passwords are read without echo." >&2
    exit 2
}

project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || { echo "Invalid Compose project." >&2; exit 2; }
[[ -f "$compose" ]] || { echo "Production Compose file was not found." >&2; exit 2; }

exec docker compose -p "$project" -f "$compose" exec --interactive --tty --user 10001:10001 app \
    sh /usr/local/bin/ppe-app java \
    -cp '/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*:/usr/local/tomcat/lib/*' \
    control.auth.InitialAdminBootstrap
