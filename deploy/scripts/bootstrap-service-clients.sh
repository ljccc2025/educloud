#!/usr/bin/env bash

# 服务客户端注册/轮换脚本（SERVICE_BOOTSTRAP_JOB）
# 依据：安全设计第 8 节——Secret 只从 stdin 读取，不进参数/stdout/日志；相同 clientId+secret 幂等。
#
# 用法一（推荐，export 环境变量后从 stdin 传入 Secret）：
#   export CLIENT_ID=user-service
#   export AUDIENCES='["educloud-file"]'
#   export SCOPES='["file:internal"]'
#   export BOOTSTRAP_KEY=...
#   printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh
#
# 用法二（直接传参，Secret 仍从 stdin 读取；参数依次为
#   CLIENT_ID、AUDIENCES、SCOPES、BOOTSTRAP_KEY）：
#   printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh \
#     user-service '["educloud-file"]' '["file:internal"]' "$BOOTSTRAP_KEY"
#
# 注意：
#   - 不要写成 `CLIENT_ID=... AUDIENCES=... printf ... | bash script`：管道左侧的
#     变量赋值只作用于 printf 命令，不会传给脚本进程（子 shell 隔离）。
#   - AUDIENCES/SCOPES 传 JSON 数组字面量即可（如 ["educloud-file"]）；脚本兼容
#     历史上带外围字面引号的写法（'["educloud-file"]'），会去掉外围引号后解析。
#
# 环境变量（用法一时使用）：CLIENT_ID、AUDIENCES（JSON 数组）、SCOPES（JSON 数组）、
#   BOOTSTRAP_KEY、BOOTSTRAP_URL（默认 http://127.0.0.1:8082/internal/bootstrap/service-clients）

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

# 位置参数优先，未提供时回退环境变量。
CLIENT_ID="${1:-${CLIENT_ID:-}}"
audiences="${2:-${AUDIENCES:-[]}}"
scopes="${3:-${SCOPES:-[]}}"
BOOTSTRAP_KEY="${4:-${BOOTSTRAP_KEY:-}}"
url="${BOOTSTRAP_URL:-http://127.0.0.1:8082/internal/bootstrap/service-clients}"

[[ -n "$CLIENT_ID" ]] || fail 'CLIENT_ID is required'
[[ -n "$BOOTSTRAP_KEY" ]] || fail 'BOOTSTRAP_KEY is required'

secret="$(cat)"
[[ -n "$secret" ]] || fail 'secret must be provided on stdin'

payload_py="$(cat <<'PYEOF'
import json
import os
import sys


def parse_json_list(raw):
    raw = raw.strip()
    # 兼容历史上带外围字面引号的写法：'["educloud-file"]' / '"educloud-file"'
    if len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in ('"', "'"):
        raw = raw[1:-1]
    try:
        value = json.loads(raw)
    except (TypeError, ValueError):
        # 兼容历史上反斜杠转义内层引号的写法（字面含 \" ）
        value = json.loads(raw.replace('\\"', '"'))
    if not isinstance(value, list):
        raise ValueError('AUDIENCES/SCOPES must be a JSON array, got: %s' % raw)
    return value


secret = sys.stdin.read()
print(json.dumps({
    'clientId': os.environ['PY_CLIENT_ID'],
    'secret': secret,
    'allowedAudiences': parse_json_list(os.environ['PY_AUDIENCES']),
    'allowedScopes': parse_json_list(os.environ['PY_SCOPES']),
}, separators=(',', ':')))
PYEOF
)"

payload="$(
  PY_CLIENT_ID="$CLIENT_ID" \
  PY_AUDIENCES="$audiences" \
  PY_SCOPES="$scopes" \
  python3 -c "$payload_py" <<<"$secret"
)"

curl --fail --silent --show-error --max-time 10 \
  -X POST "$url" \
  -H "X-Bootstrap-Key: $BOOTSTRAP_KEY" \
  -H 'Content-Type: application/json' \
  --data "$payload" >/dev/null
printf 'Service client %s bootstrapped\n' "$CLIENT_ID"
