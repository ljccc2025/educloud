#!/usr/bin/env bash

# 向 Nacos 发布 educloud-ai 非敏感配置（规格 2026-08-28-ai-assistant-p1-design.md §5.6）：
# ai.* 微调项进 Nacos 配置中心（dataId educloud-ai.yaml，group EDUCLOUD_SERVICES，tenant educloud-local）；
# AI_PROVIDER_API_KEY 永不出现在 Nacos——密钥只走 VM 的 deploy/docker-compose/.env 注入。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

nacos_port='8848'
namespace='educloud-local'
config_group='EDUCLOUD_SERVICES'
admin_username='nacos'
admin_password='nacos'

base_url="http://127.0.0.1:${nacos_port}/nacos"

login_response="$(curl -s -X POST "${base_url}/v1/auth/login" \
  -d "username=${admin_username}" -d "password=${admin_password}")"
token="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' <<<"$login_response")"
[[ -n "$token" ]] || { echo "ERROR: Nacos login failed" >&2; exit 1; }

content=$(cat <<'YAML'
ai:
  provider:
    base-url: https://api.deepseek.com
    model: deepseek-v4-flash-vision-exp
    thinking-enabled: false
    max-tokens: 1024
  timeout:
    connect-ms: 5000
    read-ms: 25000
  quota:
    daily-requests: 50
    daily-tokens: 2000000
  context:
    max-history-messages: 10
    max-prompt-tokens: 3000
YAML
)

http_code="$(curl -s -o /dev/null -w "%{http_code}" -X POST "${base_url}/v1/cs/configs" \
  --data-urlencode "accessToken=${token}" \
  --data-urlencode "dataId=educloud-ai.yaml" \
  --data-urlencode "group=${config_group}" \
  --data-urlencode "tenant=${namespace}" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=${content}")"

[[ "$http_code" == "200" ]] || { echo "ERROR: publish config failed: HTTP $http_code" >&2; exit 1; }

verify="$(curl -s "${base_url}/v1/cs/configs?dataId=educloud-ai.yaml&group=${config_group}&tenant=${namespace}&accessToken=${token}")"
grep -q "thinking-enabled" <<<"$verify" || { echo "ERROR: published config verify failed" >&2; exit 1; }
if grep -q "api-key\|AI_PROVIDER_API_KEY" <<<"$verify"; then
  echo "ERROR: nacos config must never contain the api key" >&2
  exit 1
fi

echo "OK: educloud-ai.yaml published to group=${config_group} tenant=${namespace}"
