#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
mkdir "$root/bin"
cat > "$root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
case "$*" in
    *"context inspect"*) printf 'unix:///synthetic/docker.sock\n';;
    *"buildx build"*) echo "Synthetic image build failure." >&2; exit 42;;
    *"image inspect"*) exit 1;;
    *) exit 0;;
esac
MOCK
chmod 0700 "$root/bin/docker"
sha=$(git rev-parse HEAD)
if PATH="$root/bin:$PATH" bash "$scripts/build-production-images.sh" "$sha" HEAD "$root/build" > "$root/log" 2>&1; then
    echo "Failed build returned success." >&2; exit 1
else
    status=$?
    [[ "$status" = 42 ]] || { cat "$root/log" >&2; echo "Expected build exit42, received $status." >&2; exit 1; }
fi
grep -q '\*\*FAIL\*\*: exit=42' "$root/build/summary.md"
if grep -q '\*\*PASS\*\*' "$root/build/summary.md"; then
    echo "Failed build published PASS." >&2; exit 1
fi
if DOCKER_HOST=tcp://127.0.0.1:2375 PATH="$root/bin:$PATH" \
    bash "$scripts/build-production-images.sh" "$sha" HEAD "$root/remote" > "$root/log" 2>&1; then
    echo "Remote daemon accepted." >&2; exit 1
fi
[[ ! -e "$root/remote" ]]
if AWS_ACCESS_KEY_ID=synthetic-do-not-log PATH="$root/bin:$PATH" \
    bash "$scripts/build-production-images.sh" "$sha" HEAD "$root/credential" > "$root/log" 2>&1; then
    echo "Credential environment accepted." >&2; exit 1
fi
if grep -q synthetic-do-not-log "$root/log"; then
    echo "Credential value leaked." >&2; exit 1
fi
[[ ! -e "$root/credential" ]]
echo "Image runner: failed build/nonzero/no-PASS, remote daemon rejection and credential rejection/no value logging PASS (3 scenarios)."
