#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/scripts/generate-gateway-test-material.sh --output DIR

Generate short-lived local Gateway test material in an existing empty 0700
directory. Secrets and tokens are written to files and are never printed.
USAGE
}

output_dir=''
while (($# > 0)); do
  case "$1" in
    --output)
      (($# >= 2)) || fail '--output requires a directory'
      output_dir="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$output_dir" ]] || fail '--output is required'
[[ -d "$output_dir" ]] || fail 'Output directory must already exist'
output_dir="$(cd "$output_dir" && pwd -P)"
[[ "$(stat -c '%a' "$output_dir")" == '700' ]] || fail 'Output directory must have mode 0700'
[[ -z "$(find "$output_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]] || fail 'Output directory must be empty'
for command_name in openssl python3 stat find date; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found"
done

private_key="$output_dir/private.pem"
public_key="$output_dir/public.pem"
jwks_file="$output_dir/jwks.json"
token_file="$output_dir/token.jwt"
runtime_env="$output_dir/runtime.env"
cleanup_failure() {
  rm -f -- "$private_key" "$public_key" "$jwks_file" "$token_file" "$runtime_env"
}
trap cleanup_failure EXIT

umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_key"
openssl pkey -in "$private_key" -pubout -out "$public_key"
chmod 600 "$private_key"
chmod 644 "$public_key"

modulus_hex="$(openssl rsa -in "$private_key" -noout -modulus | sed 's/^Modulus=//')"
modulus="$(python3 -c '
import base64, sys
value = int(sys.argv[1], 16)
raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
print(base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii"))
' "$modulus_hex")"
kid='educloud-rocky-test'
printf '{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"AQAB"}]}\n' \
  "$kid" "$modulus" >"$jwks_file"
chmod 644 "$jwks_file"

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

issued_at="$(date -u +%s)"
not_before=$((issued_at - 5))
expires_at=$((issued_at + 900))
header="$(printf '{"alg":"RS256","typ":"JWT","kid":"%s"}' "$kid" | base64url)"
payload="$(printf '{"iss":"https://issuer.educloud.local","aud":["educloud-api"],"sub":"rocky-user","sid":"rocky-session","userType":"STUDENT","tokenVersion":1,"iat":%s,"nbf":%s,"exp":%s}' \
  "$issued_at" "$not_before" "$expires_at" | base64url)"
signing_input="$header.$payload"
signature="$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign "$private_key" | base64url)"
printf '%s.%s\n' "$signing_input" "$signature" >"$token_file"
chmod 600 "$token_file"

hmac_secret="$(openssl rand -base64 32 | tr -d '\n')"
{
  printf 'GATEWAY_JWKS_LOCATION=file:%s\n' "$jwks_file"
  printf 'GATEWAY_JWT_ISSUER=https://issuer.educloud.local\n'
  printf 'GATEWAY_JWT_AUDIENCE=educloud-api\n'
  printf 'GATEWAY_RATE_LIMIT_HMAC_SECRET=%s\n' "$hmac_secret"
  printf 'GATEWAY_TEST_JWT=%s\n' "$(cat "$token_file")"
} >"$runtime_env"
chmod 600 "$runtime_env"
unset hmac_secret signature signing_input payload header modulus_hex

trap - EXIT
printf 'Generated private key: %s\n' "$private_key"
printf 'Generated public key: %s\n' "$public_key"
printf 'Generated public JWKS: %s\n' "$jwks_file"
printf 'Generated token file: %s\n' "$token_file"
printf 'Generated runtime environment: %s\n' "$runtime_env"
