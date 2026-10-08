#!/bin/sh
set -eu
set +x

read_secret() {
    path=$1
    test -r "$path" && test -s "$path" || { echo "Required secret file is missing or empty." >&2; exit 1; }
    LC_ALL=C awk 'NR != 1 || length($0) < 1 || length($0) > 4096 || /[^ -~]/ {exit 1}
        END {if (NR != 1) exit 1}' "$path" || {
        echo "Required secret file has an invalid format." >&2; exit 1;
    }
    value=$(cat "$path")
    test -n "$value" || { echo "Required secret file is empty." >&2; exit 1; }
    printf '%s' "$value"
}

DB_PASSWORD=$(read_secret "${DB_PASSWORD_FILE:?DB_PASSWORD_FILE is required}")
export DB_PASSWORD
if [ "${PPE_ENV:-}" = production ] || [ "${PPE_ENV:-}" = simulation ]; then
    GEMINI_API_KEY=$(read_secret "${GEMINI_API_KEY_FILE:?}")
    STUDENT_CREDENTIAL_KEY=$(read_secret "${STUDENT_CREDENTIAL_KEY_FILE:?}")
    PYTHON_RUNNER_TOKEN=$(read_secret "${PYTHON_RUNNER_TOKEN_FILE:?}")
    printf '%s\n' "$STUDENT_CREDENTIAL_KEY" | grep -Eq '^[A-Za-z0-9+/]{43}=$' || {
        echo "The credential encryption key must be 256-bit base64." >&2; exit 1;
    }
    test "${#PYTHON_RUNNER_TOKEN}" -ge 32 || { echo "The runner token is too short." >&2; exit 1; }
    export GEMINI_API_KEY STUDENT_CREDENTIAL_KEY PYTHON_RUNNER_TOKEN
fi
exec "$@"
