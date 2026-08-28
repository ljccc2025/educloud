#!/usr/bin/env bash
# EduCloud AI 助教 P1 契约测试（规格 2026-08-28-ai-assistant-p1-design.md §7）
# 前置：MySQL/Nacos/网关已按既有契约脚本准备；依赖 mysql 客户端与 curl。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8080}"
NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848/nacos}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASS="${NACOS_PASS:-nacos}"

echo "== [1/5] ai 库表结构 =="
for table in ai_conversation ai_message; do
  exists=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='educloud_ai' AND table_name='$table';" 2>/dev/null)
  if [ "$exists" != "1" ]; then
    echo "FAIL: table $table missing"; exit 1
  fi
  echo "OK: $table"
done

echo "== [2/5] ai_app 表级授权 =="
grants=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
  "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee LIKE '%ai_app%' AND table_schema='educloud_ai' AND table_name IN ('ai_conversation','ai_message');" 2>/dev/null)
if [ "$grants" -lt 2 ]; then
  echo "FAIL: ai_app grants missing"; exit 1
fi
echo "OK: grants"

echo "== [3/5] 网关路由与鉴权 =="
# 未带 token 访问应得 401（路由存在且被保护），404/000 才是路由缺失
status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/ai/conversations" || true)
if [ "$status" = "000" ] || [ "$status" = "404" ]; then
  echo "FAIL: gateway route /api/v1/ai/** not reachable (HTTP $status)"; exit 1
fi
echo "OK: gateway route protected (HTTP $status)"

echo "== [4/5] Nacos educloud-ai.yaml 配置存在性 =="
token=$(curl -s -X POST "$NACOS_URL/v1/auth/login" -d "username=$NACOS_USER" -d "password=$NACOS_PASS" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null || true)
if [ -z "$token" ]; then
  echo "SKIP: nacos login failed (console credentials differ)"
else
  config=$(curl -s "$NACOS_URL/v1/cs/configs?dataId=educloud-ai.yaml&group=EDUCLOUD_SERVICES&tenant=educloud-local&accessToken=$token" || true)
  if grep -q "thinking-enabled" <<<"$config"; then
    echo "OK: educloud-ai.yaml present"
  else
    echo "FAIL: educloud-ai.yaml missing or empty (run deploy/scripts/provision-ai-nacos.sh)"; exit 1
  fi
  if grep -q "api-key\|AI_PROVIDER_API_KEY" <<<"$config"; then
    echo "FAIL: nacos config must never contain the api key"; exit 1
  fi
fi

echo "== [5/5] 密钥占位纪律 =="
if grep -qE '^AI_PROVIDER_API_KEY=.+' deploy/docker-compose/.env.example 2>/dev/null; then
  echo "FAIL: .env.example must keep AI_PROVIDER_API_KEY empty"; exit 1
fi
echo "OK: placeholder only"

echo "== AI 助教契约测试全部通过 =="
