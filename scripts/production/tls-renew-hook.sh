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
[[ -d "$parent" && ! -L "$parent" && ! -L "$PPE_TLS_DIR" \
    && -z "$(find "$parent" -prune ! -perm 0700 -print)" ]] || {
    echo "TLS renewal requires a private parent and a non-symlink serving directory." >&2
    exit 1
}
lock="$parent/.ppe-tls-renew-lock"
mkdir "$lock" 2>/dev/null || { echo "TLS renewal deployment is already in progress." >&2; exit 1; }
backup=
changed=0
finish() {
    local status=$? recovery_failed=0
    trap - EXIT HUP INT TERM
    if (( status != 0 && changed )); then
        if ! restore_old_certificate; then
            recovery_failed=1
            echo "TLS recovery is incomplete; preserve the private backup and inspect Nginx manually." >&2
        fi
    fi
    if [[ -n "$backup" ]]; then
        echo "Previous certificate pair retained in private backup: $backup" >&2
    fi
    if (( recovery_failed )); then
        echo "TLS renewal lock retained until manual recovery is verified." >&2
    else
        rmdir "$lock" || { echo "TLS renewal lock cleanup failed." >&2; status=1; }
    fi
    exit "$status"
}
trap finish EXIT
trap 'exit 1' HUP INT TERM
[[ ! -d "$PPE_TLS_DIR/.install-lock" ]] || {
    echo "TLS installation is already in progress; renewal was not deployed." >&2
    exit 1
}
backup=$(mktemp -d "$parent/.ppe-tls-before-renew.XXXXXX")
chmod 0700 "$backup"
cp "$PPE_TLS_DIR/fullchain.pem" "$backup/fullchain.pem"
cp "$PPE_TLS_DIR/privkey.pem" "$backup/privkey.pem"
chmod 0400 "$backup/fullchain.pem" "$backup/privkey.pem"
restore_old_certificate() {
    local name temporary
    for name in fullchain.pem privkey.pem; do
        temporary="$PPE_TLS_DIR/.$name.restore.$$"
        if ! cp "$backup/$name" "$temporary" \
            || ! chmod 0444 "$temporary" \
            || ! mv -f "$temporary" "$PPE_TLS_DIR/$name"; then
            echo "Previous certificate file restoration failed." >&2
            return 1
        fi
    done
    if docker compose -p "$project" -f "$compose" exec -T nginx nginx -t \
        && docker compose -p "$project" -f "$compose" exec -T nginx nginx -s reload; then
        echo "Previous TLS certificate restored after a failed renewal deployment." >&2
    else
        echo "Previous certificate files were restored; Nginx reload needs manual review." >&2
        return 1
    fi
}
changed=1
sh "$scripts/install-tls.sh" "$RENEWED_LINEAGE/fullchain.pem" "$RENEWED_LINEAGE/privkey.pem" "$PPE_TLS_DIR"
docker compose -p "$project" -f "$compose" exec -T nginx nginx -t
docker compose -p "$project" -f "$compose" exec -T nginx nginx -s reload
changed=0
echo "Renewed TLS certificate installed; Nginx configuration and reload succeeded."
