#!/bin/sh
set -eu
set +x
umask 077
test "$#" = 3 || { echo "Usage: sh install-tls.sh FULLCHAIN PRIVATE_KEY ABSOLUTE_TLS_DIRECTORY" >&2; exit 1; }
chain=$1
key=$2
output=$3
case "$output" in /*) ;; *) echo "An absolute TLS directory is required." >&2; exit 1 ;; esac
case "$output" in /|*'/../'*|*'/./'*|*/..|*/.) echo "Invalid TLS directory." >&2; exit 1 ;; esac
parent=$(dirname "$output")
test -d "$parent" && test ! -L "$parent" && test -z "$(find "$parent" -prune ! -perm 0700 -print)" || {
    echo "The TLS parent directory must already be private (0700)." >&2; exit 1;
}
test ! -L "$output" || { echo "TLS output cannot be a symlink." >&2; exit 1; }
mkdir -p "$output"
mkdir "$output/.install-lock" 2>/dev/null || { echo "TLS installation is already in progress." >&2; exit 1; }
cleanup() {
    rm -f "$output/.chain.new" "$output/.key.new" "$output/.cert-public" "$output/.key-public"
    rmdir "$output/.install-lock"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
openssl x509 -in "$chain" -checkend 86400 -noout >/dev/null
openssl x509 -in "$chain" -checkhost student.ppeval.net -noout | grep -q 'does match certificate'
openssl x509 -in "$chain" -checkhost teacher.ppeval.net -noout | grep -q 'does match certificate'
openssl x509 -in "$chain" -pubkey -noout > "$output/.cert-public"
openssl pkey -in "$key" -pubout > "$output/.key-public" 2>/dev/null
cmp -s "$output/.cert-public" "$output/.key-public" || { echo "TLS key and certificate do not match." >&2; exit 1; }
cp "$chain" "$output/.chain.new"
cp "$key" "$output/.key.new"
chmod 0444 "$output/.chain.new" "$output/.key.new"
mv "$output/.chain.new" "$output/fullchain.pem"
mv "$output/.key.new" "$output/privkey.pem"
chmod 0755 "$output"
echo "TLS certificate installed; validate and reload Nginx after successful installation."
