#!/usr/bin/env bash
# The contract that matters: legal keys become variables of the same name, illegal keys do not.
set -uo pipefail
cd "$(dirname "$0")/.."
./build.sh >/dev/null || { echo "build failed" >&2; exit 1; }
. tools/smoke-lib.sh

# 1. a .env with comments, quotes, an illegal key and a duplicate
dir="$(mktemp -d)"
cat >"$dir/.env" <<'ENV'
# staging settings
APP_ENV=staging
DB_HOST="db.internal"
API_TOKEN=s3cr3t
not-a-key=ignored
EMPTY=
ENV
printf '{"files": ".env", "failOnMissing": "true"}' >"$dir/input.json"
run "$dir" >"$dir/log.txt" 2>&1 || fail "run failed: $(cat "$dir/log.txt")"
[ "$(status "$dir")" = "success" ] || fail "not a success: $(cat "$dir/output.json")"
[ "$(output "$dir" APP_ENV)" = "staging" ] || fail "APP_ENV: $(cat "$dir/output.json")"
[ "$(output "$dir" DB_HOST)" = "db.internal" ] || fail "quotes must be stripped: $(cat "$dir/output.json")"
[ "$(output "$dir" API_TOKEN)" = "s3cr3t" ] || fail "API_TOKEN"
[ "$(output "$dir" count)" = "4" ] || fail "count: $(cat "$dir/output.json")"
python3 -c "
import json,sys
d=json.load(open('$dir/output.json'))['data']
sys.exit(0 if 'not-a-key' not in d else 1)" || fail "an illegal key must not be exported"
grep -q "s3cr3t" "$dir/log.txt" && fail "a TOKEN value must not be echoed to the log"

# 2. a prefix keeps the names in one namespace
dir="$(mktemp -d)"
printf 'REGION=eu\n' >"$dir/vars.env"
printf '{"files": "vars.env", "prefix": "APP_"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "prefix run failed"
[ "$(output "$dir" APP_REGION)" = "eu" ] || fail "prefix: $(cat "$dir/output.json")"

# 3. several files, later values win
dir="$(mktemp -d)"
printf 'A=1\nB=1\n' >"$dir/one.env"
printf 'B=2\n' >"$dir/two.env"
printf '{"files": "one.env, two.env"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "two-file run failed"
[ "$(output "$dir" B)" = "2" ] || fail "later file must win: $(cat "$dir/output.json")"

# 4. a missing file is an error unless told otherwise
dir="$(mktemp -d)"
printf '{"files": "nope.env", "failOnMissing": "true"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 && fail "a missing file must fail"
grep -q "no such file" "$dir/output.json" || fail "message not readable"

echo "smoke ok: legal/illegal keys, quotes, prefix, later file wins, missing file"
