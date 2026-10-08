#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

: "${PPE_TLS_DIR:?Set PPE_TLS_DIR}"
: "${RENEWED_LINEAGE:?Run through the Certbot deploy hook}"
: "${RENEWED_DOMAINS:?Certbot did not supply the renewed domains}"
[[ "$RENEWED_LINEAGE" = "${PPE_CERTBOT_CONFIG_DIR:?Set PPE_CERTBOT_CONFIG_DIR}/live/ppeval" ]] || {
    echo "Ignoring a certificate lineage other than ppeval." >&2
    exit 1
}
for domain in student.ppeval.net teacher.ppeval.net; do
    [[ " $RENEWED_DOMAINS " = *" $domain "* ]] || {
        echo "Renewed certificate is missing a required portal domain." >&2
        exit 1
    }
done
[[ -s "$PPE_TLS_DIR/fullchain.pem" && -s "$PPE_TLS_DIR/privkey.pem" ]] || {
    echo "No existing certificate is available to protect during renewal." >&2
    exit 1
}

scripts=$(cd "$(dirname "$0")" && pwd)
project=${PPE_PROJECT:-ppe-production}
compose=${PPE_COMPOSE_FILE:-compose.production.yml}
parent=$(dirname "$PPE_TLS_DIR")
backup=$(mktemp -d "$parent/.ppe-tls-before-renew.XXXXXX")
chmod 0700 "$backup"
cp "$PPE_TLS_DIR/fullchain.pem" "$backup/fullchain.pem"
cp "$PPE_TLS_DIR/privkey.pem" "$backup/privkey.pem"
chmod 0400 "$backup/fullchain.pem" "$backup/privkey.pem"
changed=0

restore_old_certificate() {
    local name temporary
    for name in fullchain.pem privkey.pem; do
        temporary="$PPE_TLS_DIR/.$name.restore.$$"
        cp "$backup/$name" "$temporary"
        chmod 0444 "$temporary"
        mv -f "$temporary" "$PPE_TLS_DIR/$name"
    done
    if docker compose -p "$project" -f "$compose" exec -T nginx nginx -t \
        && docker compose -p "$project" -f "$compose" exec -T nginx nginx -s reload; then
        echo "Previous TLS certificate restored after a failed renewal deployment." >&2
    else
        echo "Previous certificate files were restored; Nginx reload needs manual review." >&2
    fi
}
finish() {
    local status=$?
    trap - EXIT
    if (( status != 0 && changed )); then restore_old_certificate; fi
    rm -f "$backup/fullchain.pem" "$backup/privkey.pem"
    rmdir "$backup"
    exit "$status"
}
trap finish EXIT
trap 'exit 1' HUP INT TERM

sh "$scripts/install-tls.sh" "$RENEWED_LINEAGE/fullchain.pem" "$RENEWED_LINEAGE/privkey.pem" "$PPE_TLS_DIR"
changed=1
docker compose -p "$project" -f "$compose" exec -T nginx nginx -t
docker compose -p "$project" -f "$compose" exec -T nginx nginx -s reload
changed=0
echo "Renewed TLS certificate installed; Nginx configuration and reload succeeded."
