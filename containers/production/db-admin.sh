#!/bin/sh
set -eu
set +x
umask 077
options=$(mktemp /tmp/ppe-mysql-admin.XXXXXX)
trap 'rm -f "$options"' EXIT HUP INT TERM
test -s /run/secrets/db_root_password
password=$(sed 's/\\/\\\\/g; s/"/\\"/g' /run/secrets/db_root_password)
printf '[client]\nuser=root\npassword="%s"\nprotocol=socket\n' "$password" > "$options"
unset password
if [ "${1:-}" = --dump ]; then
    shift
    mysqldump --defaults-extra-file="$options" --single-transaction --routines --triggers \
        --events --hex-blob --no-tablespaces --set-gtid-purged=OFF --databases ppe "$@"
else
    mysql --defaults-extra-file="$options" --batch --binary-mode "$@"
fi
