#!/usr/bin/env bash

# 服务客户端注册/轮换脚本（SERVICE_BOOTSTRAP_JOB）
# 依据：安全设计第 8 节——Secret 只从 stdin 读取，不进参数/stdout/日志；相同 clientId+secret 幂等。
#
# 用法：
#   CLIENT_ID=... AUDIENCES='["educloud-order"]' SCOPES='["order:read"]' \
#     printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh
#   # M04: 为 File 注册 user-service 内部客户端
#   CLIENT_ID=user-service AUDIENCES='["educloud-file"]' SCOPES='["file:internal"]' \
#     printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh
#   # M05: 为 File 注册 educloud-course 内部客户端
#   CLIENT_ID=educloud-course AUDIENCES='["educloud-file"]' SCOPES='["file:internal"]' \
#     printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh
#
# 环境变量：CLIENT_ID、AUDIENCES（JSON 数组）、SCOPES（JSON 数组）、
#   BOOTSTRAP_KEY、BOOTSTRAP_URL（默认 http://127.0.0.1:8082/internal/bootstrap/service-clients）

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ -n "${CLIENT_ID:-}" ]] || fail 'CLIENT_ID is required'
[[ -n "${BOOTSTRAP_KEY:-}" ]] || fail 'BOOTSTRAP_KEY is required'
audiences="${AUDIENCES:-[]}"
scopes="${SCOPES:-[]}"
url="${BOOTSTRAP_URL:-http://127.0.0.1:8082/internal/bootstrap/service-clients}"

secret="$(cat)"
[[ -n "$secret" ]] || fail 'secret must be provided on stdin'

payload="$(python3 -c '
import json, sys
secret = sys.stdin.read()
print(json.dumps({
    "clientId": "'"$CLIENT_ID"'",
    "secret": secret,
    "allowedAudiences": json.loads('"$audiences"'),
    "allowedScopes": json.loads('"$scopes"'),
}, separators=(",", ":")))
' <<<"$secret")"

curl --fail --silent --show-error --max-time 10 \
  -X POST "$url" \
  -H "X-Bootstrap-Key: $BOOTSTRAP_KEY" \
  -H 'Content-Type: application/json' \
  --data "$payload" >/dev/null
printf 'Service client %s bootstrapped\n' "$CLIENT_ID"
