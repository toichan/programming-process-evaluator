#!/bin/sh
set -eu
port=${PPE_HTTPS_PORT:-18443}
http_port=${PPE_HTTP_PORT:-18088}
for role in student teacher; do
    host="$role.ppeval.net"
    headers=$(curl -ksS --max-time 10 --resolve "$host:$port:127.0.0.1" \
        -D - -o /dev/null "https://$host:$port/$role/account/login")
    printf '%s\n' "$headers" | grep -q 'HTTP/1.1 200'
    printf '%s\n' "$headers" | grep -i '^Set-Cookie:' | grep -q 'Secure; HttpOnly; SameSite=Lax'
    printf '%s\n' "$headers" | grep -iq '^Strict-Transport-Security:'
    # Headers contain session identifiers: assert them in memory, never print them.
    redirect=$(curl -sS --max-time 10 -H "Host: $host" -o /dev/null -w '%{http_code}' \
        "http://127.0.0.1:$http_port/$role/account/login")
    test "$redirect" = 308
done
test "$(curl -ksS --max-time 10 --resolve "student.ppeval.net:$port:127.0.0.1" \
    "https://student.ppeval.net:$port/health")" = '{"status":"ok"}'
wrong_host=$(curl -ksS --max-time 10 --resolve "student.ppeval.net:$port:127.0.0.1" \
    -o /dev/null -w '%{redirect_url}' "https://student.ppeval.net:$port/teacher/progress")
test "$wrong_host" = 'https://teacher.ppeval.net/teacher/account/login'
echo "TLS proxy, two hosts, redirect, cookie flags and app health: PASS (local dummy certificate)"
