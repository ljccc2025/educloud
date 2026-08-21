#!/usr/bin/env bash

# 生成 User 服务 RSA 签名密钥对：私钥（User 服务持有）+ 公共 JWKS（Gateway 配置引用）
#
# 依据：M03 设计规格第 2/11 节（签名密钥管理）与 M02 JwksLoader 契约：
#   - Gateway 只接受 jwks-json / jwks-location 静态来源，不提供远程 JWKS URL；
#   - JWKS 只能包含公钥参数（拒绝 d/p/q/dp/dq/qi/oth）；
#   - kid 由公钥模数哈希派生，密钥对不变则 kid 稳定，支持平滑轮换。
#
# 用法：
#   bash deploy/scripts/generate-user-jwt-keys.sh [--private-key PATH] [--jwks PATH] [--force]
# 默认输出：deploy/secrets/user-jwt-private.pem（0600）与 deploy/secrets/user-jwks.json（0644）。

set -euo pipefail

fail() {
  printf 'ERROR: %s
' "$1" >&2
  exit 1
}

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
secrets_dir="$repo_root/deploy/secrets"
private_key="$secrets_dir/user-jwt-private.pem"
jwks_file="$secrets_dir/user-jwks.json"
force=0

while (($# > 0)); do
  case "$1" in
    --private-key)
      (($# >= 2)) || fail '--private-key requires a path'
      private_key="$2"
      shift 2
      ;;
    --jwks)
      (($# >= 2)) || fail '--jwks requires a path'
      jwks_file="$2"
      shift 2
      ;;
    --force)
      force=1
      shift
      ;;
    --help|-h)
      cat <<'USAGE'
Usage: bash deploy/scripts/generate-user-jwt-keys.sh [--private-key PATH] [--jwks PATH] [--force]
USAGE
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

for command_name in openssl python3; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found"
done

if [[ -e "$private_key" && "$force" != '1' ]]; then
  fail "Private key already exists: $private_key (use --force to regenerate)"
fi

mkdir -p "$(dirname "$private_key")" "$(dirname "$jwks_file")"
umask 077

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_key"
chmod 600 "$private_key"

modulus_hex="$(openssl rsa -in "$private_key" -noout -modulus | sed 's/^Modulus=//')"
modulus="$(python3 -c '
import base64, sys
value = int(sys.argv[1], 16)
raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
print(base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii"))
' "$modulus_hex")"
# kid 必须与 User 服务 JwtKeyProvider 一致：对 modulus 无符号字节做 SHA-256（不是 hex 文本）。
kid="educloud-user-$(python3 -c '
import hashlib, sys
value = int(sys.argv[1], 16)
raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
print(hashlib.sha256(raw).hexdigest()[:16])
' "$modulus_hex")"

printf '{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"AQAB"}]}
'   "$kid" "$modulus" >"$jwks_file"
chmod 644 "$jwks_file"

printf 'Generated User JWT private key: %s (0600)
' "$private_key"
printf 'Generated public JWKS: %s (0644)
' "$jwks_file"
printf 'Active kid: %s
' "$kid"
