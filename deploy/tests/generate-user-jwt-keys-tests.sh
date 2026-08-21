#!/usr/bin/env bash

# generate-user-jwt-keys.sh 契约测试
# 依据：M03 计划任务 5 与设计规格第 11 节（密钥文件 0600、JWKS 0644 且只含公钥、幂等）。

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/generate-user-jwt-keys.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

failed=0
pass() { printf 'PASS: %s
' "$1"; }
fail() { printf 'FAIL: %s
' "$1" >&2; failed=$((failed + 1)); }

private_key="$fixture_root/user-jwt-private.pem"
jwks_file="$fixture_root/user-jwks.json"

bash "$script_under_test" --private-key "$private_key" --jwks "$jwks_file" >"$fixture_root/first.log" 2>&1 ||   fail 'first key generation must succeed'
[[ -f "$private_key" ]] || fail 'private key file missing'
[[ -f "$jwks_file" ]] || fail 'jwks file missing'
if [[ "$(uname -s)" == 'Linux' ]]; then
  [[ "$(stat -c '%a' "$private_key")" == '600' ]] || fail 'private key mode must be 0600'
  [[ "$(stat -c '%a' "$jwks_file")" == '644' ]] || fail 'jwks mode must be 0644'
  pass 'restricts key file permissions on Linux'
else
  printf 'SKIP: exact POSIX file-mode checks require Linux
'
fi

python3 - "$jwks_file" <<'PY' || fail 'jwks JSON contract violated'
import json, sys
root = json.load(open(sys.argv[1], encoding="utf-8"))
keys = root.get("keys", [])
assert len(keys) == 1, "exactly one key expected"
key = keys[0]
assert key.get("kty") == "RSA", "kty must be RSA"
assert key.get("use") == "sig", "use must be sig"
assert key.get("alg") == "RS256", "alg must be RS256"
assert key.get("kid", "").startswith("educloud-user-"), "kid prefix"
assert key.get("n"), "modulus n required"
assert key.get("e") == "AQAB", "exponent"
for private_field in ("d", "p", "q", "dp", "dq", "qi", "oth"):
    assert private_field not in key, "private parameter leaked: " + private_field
PY
pass 'generates 0600 private key and public-only JWKS with stable kid prefix'

if bash "$script_under_test" --private-key "$private_key" --jwks "$jwks_file" >/dev/null 2>&1; then
  fail 'second run without --force must fail'
fi
pass 'refuses to overwrite an existing key without --force'

bash "$script_under_test" --private-key "$private_key" --jwks "$jwks_file" --force >/dev/null 2>&1 ||   fail '--force must regenerate the key pair'
pass 'regenerates with --force'

if grep -Fq 'BEGIN PRIVATE KEY' "$fixture_root/first.log"; then
  fail 'key material must not be printed to stdout'
fi
pass 'never prints private key material'

if ((failed > 0)); then
  printf '%s jwt key generation checks failed
' "$failed" >&2
  exit 1
fi
printf 'All JWT key generation checks passed
'
