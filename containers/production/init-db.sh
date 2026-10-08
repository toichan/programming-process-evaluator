#!/bin/sh
set -e
set +x

sql_password() {
    test -s "$1" || { echo "Database credential file is empty." >&2; exit 1; }
    # Use SQL-standard quotes with NO_BACKSLASH_ESCAPES, never shell evaluation.
    sed "s/'/''/g" "$1"
}
app_password=$(sql_password /run/secrets/db_password)
migration_password=$(sql_password /run/secrets/db_migration_password)
docker_process_sql <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER 'ppe_app'@'%' IDENTIFIED BY '$app_password';
CREATE USER 'ppe_migrate'@'%' IDENTIFIED BY '$migration_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON ppe.* TO 'ppe_app'@'%';
GRANT ALL PRIVILEGES ON ppe.* TO 'ppe_migrate'@'%';
SQL
unset app_password migration_password
touch /var/lib/mysql/.ppe-initialized
