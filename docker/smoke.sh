#!/usr/bin/env bash
# Creates an account, reads it back, and checks one refusal, against the service on 127.0.0.1:8080.
# Exits 1 at the first check that fails, and names it.
set -euo pipefail

base=http://127.0.0.1:8080
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# Prints the status. The body goes to $work/body and the headers to $work/headers.
request() {
  curl --silent --show-error --max-time 5 --output "$work/body" --dump-header "$work/headers" \
    --write-out '%{http_code}' "$@"
}

create() {
  request --header 'Content-Type: application/json' \
    --data "{\"customerId\":\"$customer\",\"branch\":\"$1\",\"customerName\":\"Smoke Test\"}" \
    "$base/api/v1/savings-accounts"
}

# A new customer for each run, so repeated runs never reach the limit of five accounts.
customer=$(uuidgen | tr '[:upper:]' '[:lower:]')

status=$(create 0123) || fail "create: curl could not complete the request"
[ "$status" = 201 ] || fail "create: expected 201, got $status"
location=$(grep -i '^location:' "$work/headers" | tr -d '\r' | cut -d ' ' -f 2)
[ -n "$location" ] || fail "create: no Location header"
created=$(cat "$work/body")
echo "ok: create gave 201 and $location"

status=$(request "$base$location") || fail "read: curl could not complete the request"
[ "$status" = 200 ] || fail "read: expected 200, got $status"
[ "$(cat "$work/body")" = "$created" ] || fail "read: the body differs from the body of the create"
echo "ok: read gave 200 and the same body"

# Branch 2000 is outside every bank 03 range.
status=$(create 2000) || fail "refusal: curl could not complete the request"
[ "$status" = 422 ] || fail "refusal: expected 422, got $status"
grep -q '"code":"branch-not-allowed"' "$work/body" || fail "refusal: expected the code branch-not-allowed"
echo "ok: branch 2000 gave 422 branch-not-allowed"
