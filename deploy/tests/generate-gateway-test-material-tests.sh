#!/usr/bin/env bash

set -euo pipefail

if [[ "$(uname -s)" != 'Linux' ]]; then
  printf 'SKIP: gateway test material permission contract requires Linux\n'
  exit 0
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/generate-gateway-test-material.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

output_dir="$fixture_root/material"
mkdir -m 700 "$output_dir"
bash "$script_under_test" --output "$output_dir" >"$fixture_root/generate.log" 2>&1

for file in private.pem public.pem jwks.json token.jwt runtime.env; do
  [[ -f "$output_dir/$file" ]] || fail "missing generated file: $file"
done
[[ "$(stat -c '%a' "$output_dir")" == '700' ]] || fail 'changed output directory mode'
[[ "$(stat -c '%a' "$output_dir/private.pem")" == '600' ]] || fail 'private key is not mode 0600'
[[ "$(stat -c '%a' "$output_dir/jwks.json")" == '644' ]] || fail 'JWKS is not mode 0644'
[[ "$(stat -c '%a' "$output_dir/runtime.env")" == '600' ]] || fail 'runtime env is not mode 0600'
openssl pkey -in "$output_dir/private.pem" -text -noout 2>/dev/null | \
  grep -Fq 'Private-Key: (2048 bit' || fail 'private key is not 2048-bit RSA'

python3 - "$output_dir/jwks.json" "$output_dir/token.jwt" <<'PY'
import base64
import json
import pathlib
import sys
import time

jwks = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert len(jwks["keys"]) == 1
key = jwks["keys"][0]
assert key["kty"] == "RSA" and key["alg"] == "RS256" and key["use"] == "sig"
assert not ({"d", "p", "q", "dp", "dq", "qi", "oth"} & key.keys())
token = pathlib.Path(sys.argv[2]).read_text(encoding="ascii").strip()
parts = token.split(".")
assert len(parts) == 3 and all(parts)
payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
assert payload["iss"] == "https://issuer.educloud.local"
assert payload["aud"] == ["educloud-api"]
assert payload["sub"] == "rocky-user" and payload["sid"] == "rocky-session"
assert payload["userType"] == "STUDENT" and payload["tokenVersion"] == 1
assert payload["iat"] - 10 <= payload["nbf"] <= payload["iat"]
assert 0 < payload["exp"] - payload["iat"] <= 900
assert payload["exp"] > int(time.time())
PY

token="$(cat "$output_dir/token.jwt")"
signing_input="${token%.*}"
signature="${token##*.}"
printf '%s' "$signature" | tr '_-' '/+' | awk '{ pad=(4-length($0)%4)%4; printf "%s", $0; while(pad--) printf "=" }' | \
  base64 -d >"$fixture_root/signature.bin"
printf '%s' "$signing_input" >"$fixture_root/signing-input.txt"
openssl dgst -sha256 -verify "$output_dir/public.pem" \
  -signature "$fixture_root/signature.bin" "$fixture_root/signing-input.txt" >/dev/null || \
  fail 'JWT signature does not verify with the generated public key'

hmac_secret="$(sed -n 's/^GATEWAY_RATE_LIMIT_HMAC_SECRET=//p' "$output_dir/runtime.env")"
[[ "$(printf '%s' "$hmac_secret" | base64 -d | wc -c | tr -d ' ')" -ge 32 ]] || \
  fail 'HMAC secret is shorter than 32 bytes'
if grep -Fq "$token" "$fixture_root/generate.log" || grep -Fq "$hmac_secret" "$fixture_root/generate.log"; then
  fail 'printed token or HMAC secret'
fi

nonempty="$fixture_root/nonempty"
mkdir -m 700 "$nonempty"
touch "$nonempty/existing"
if bash "$script_under_test" --output "$nonempty" >/dev/null 2>&1; then
  fail 'accepted a non-empty output directory'
fi

wrong_mode="$fixture_root/wrong-mode"
mkdir -m 755 "$wrong_mode"
if bash "$script_under_test" --output "$wrong_mode" >/dev/null 2>&1; then
  fail 'accepted an output directory without mode 0700'
fi

rm -rf "$output_dir"
[[ ! -e "$output_dir" ]] || fail 'test material cleanup left files behind'
printf 'All gateway test material checks passed\n'
