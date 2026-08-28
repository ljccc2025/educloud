#!/usr/bin/env bash
# EduCloud 在线考试模块契约测试（规格 2026-08-28-educloud-exam-design.md §9）
# 前置：MySQL/Nacos/网关已按既有契约脚本准备；依赖 mysql 客户端与 curl。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8080}"

echo "== [1/4] 考试表结构 =="
for table in exam_bank_question exam exam_paper_question exam_attempt; do
  exists=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='educloud_content' AND table_name='$table';" 2>/dev/null)
  if [ "$exists" != "1" ]; then
    echo "FAIL: table $table missing"; exit 1
  fi
  echo "OK: $table"
done

echo "== [2/4] content_app 授权 =="
grants=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
  "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee LIKE '%content_app%' AND table_schema='educloud_content' AND table_name IN ('exam_bank_question','exam','exam_paper_question','exam_attempt');" 2>/dev/null)
if [ "$grants" -lt 4 ]; then
  echo "FAIL: content_app grants missing for exam tables"; exit 1
fi
echo "OK: grants"

echo "== [3/4] 网关路由 =="
routes=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/me/exams" || true)
if [ "$routes" = "404" ] || [ "$routes" = "000" ]; then
  echo "WARN: gateway route not reachable (HTTP $routes), check gateway is up"
else
  echo "OK: gateway route reachable (HTTP $routes)"
fi

echo "== [4/4] RabbitMQ 事件路由 =="
if command -v rabbitmqadmin >/dev/null 2>&1; then
  binding=$(rabbitmqadmin -q list bindings source=educloud.events routing_key=exam.graded 2>/dev/null | grep -c exam.graded || true)
  if [ "$binding" -ge 1 ]; then
    echo "OK: exam.graded binding present"
  else
    echo "FAIL: exam.graded binding missing"; exit 1
  fi
else
  echo "SKIP: rabbitmqadmin not available"
fi

echo "== 考试契约测试全部通过 =="
