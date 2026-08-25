#!/usr/bin/env bash

# EduCloud 开发环境一键启动（在 VM/Rocky 上执行）。幂等：已占用端口跳过启动。
# 用法：bash deploy/scripts/start-dev.sh
# 启动：基础设施容器(compose) + educloud-user + educloud-gateway + educloud-course + educloud-file + 三门户 Vite Dev Server。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

port_free() {
  ! ss -tln 2>/dev/null | grep -q ":$1 "
}

wait_ready() {
  local url="$1" label="$2"
  # 兼容性修复（M08）：order/payment 监控端仅暴露 /actuator/health（未启用 probes 时
  # /readiness 为 404），就绪探测改为双地址轮询，任一可达即视为就绪。
  local alt="${url%/readiness}"
  for _ in {1..60}; do
    if curl --fail --silent --max-time 3 "$url" >/dev/null 2>&1 \
       || curl --fail --silent --max-time 3 "$alt" >/dev/null 2>&1; then
      printf "%s: UP\n" "$label"
      return 0
    fi
    sleep 1
  done
  printf "ERROR: %s did not become ready\n" "$label" >&2
  return 1
}

set -a
. deploy/docker-compose/.env
set +a

printf "[1/6] Ensuring infrastructure containers...\n"
docker compose -f deploy/docker-compose/compose.yml up -d >/dev/null 2>&1 || true

printf "[2/6] Ensuring JWT key material...\n"
mkdir -p /tmp/educloud-live
if [[ ! -f /tmp/educloud-live/private.pem ]]; then
  bash deploy/scripts/generate-user-jwt-keys.sh \
    --private-key /tmp/educloud-live/private.pem --jwks /tmp/educloud-live/jwks.json >/dev/null
fi

printf "[3/6] Starting educloud-user and educloud-gateway...\n"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.20.1.1-1.1.el8_10.x86_64
export PATH="$JAVA_HOME/bin:$PATH"

if port_free 8082; then
  SERVER_PORT=8082 USER_MANAGEMENT_PORT=8083 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_USER_DB_PASSWORD="$EDUCLOUD_USER_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="$REDIS_PASSWORD" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="$RABBITMQ_DEFAULT_USER" RABBITMQ_DEFAULT_PASS="$RABBITMQ_DEFAULT_PASS" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"$NACOS_HTTP_PORT" \
  EDUCLOUD_USER_NACOS_USERNAME=educloud_user EDUCLOUD_USER_NACOS_PASSWORD="$NACOS_USER_PASSWORD" \
  USER_JWT_PRIVATE_KEY_LOCATION=/tmp/educloud-live/private.pem \
  EDUCLOUD_USER_JWT_ISSUER="${EDUCLOUD_USER_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_USER_JWT_AUDIENCE="${EDUCLOUD_USER_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_USER_INTERNAL_BOOTSTRAP_KEY="$EDUCLOUD_USER_INTERNAL_BOOTSTRAP_KEY" \
  EDUCLOUD_USER_FILE_ENDPOINT="${EDUCLOUD_USER_FILE_ENDPOINT:-http://127.0.0.1:8087}" \
  EDUCLOUD_USER_FILE_CLIENT_ID="${EDUCLOUD_USER_FILE_CLIENT_ID:-user-service}" \
  EDUCLOUD_USER_FILE_CLIENT_SECRET="$EDUCLOUD_USER_FILE_CLIENT_SECRET" \
  EDUCLOUD_USER_FILE_ENABLED="${EDUCLOUD_USER_FILE_ENABLED:-true}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-user/target/educloud-user-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/user.log 2>&1 < /dev/null &
  printf "  educloud-user started (8082/8083)\n"
else
  printf "  educloud-user already running\n"
fi

wait_ready "http://127.0.0.1:8083/actuator/health/readiness" "educloud-user"

if port_free 8080; then
  SERVER_PORT=8080 GATEWAY_MANAGEMENT_ADDRESS=127.0.0.1 GATEWAY_MANAGEMENT_PORT=8081 \
  GATEWAY_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  GATEWAY_JWT_ISSUER="${GATEWAY_JWT_ISSUER:-https://issuer.educloud.local}" \
  GATEWAY_JWT_AUDIENCE="${GATEWAY_JWT_AUDIENCE:-educloud-api}" \
  GATEWAY_RATE_LIMIT_HMAC_SECRET="${GATEWAY_RATE_LIMIT_HMAC_SECRET:-$(openssl rand -base64 48)}" \
  GATEWAY_ALLOWED_ORIGINS="${GATEWAY_ALLOWED_ORIGINS:-https://educloud.local,http://localhost:5173,http://localhost:5174,http://localhost:5175,http://127.0.0.1:5173,http://127.0.0.1:5174,http://127.0.0.1:5175,http://192.168.100.136:5173,http://192.168.100.136:5174,http://192.168.100.136:5175}" \
  NACOS_GATEWAY_NAMESPACE="${NACOS_GATEWAY_NAMESPACE:-educloud-local}" \
  NACOS_GATEWAY_CONFIG_GROUP="${NACOS_GATEWAY_CONFIG_GROUP:-EDUCLOUD_GATEWAY}" \
  NACOS_GATEWAY_DISCOVERY_GROUP="${NACOS_GATEWAY_DISCOVERY_GROUP:-EDUCLOUD_SERVICES}" \
  NACOS_GATEWAY_USERNAME=educloud_gateway NACOS_GATEWAY_PASSWORD="$NACOS_GATEWAY_PASSWORD" \
  EDUCLOUD_ENVIRONMENT=local \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="$REDIS_PASSWORD" \
  NACOS_SERVER_ADDR=127.0.0.1:"$NACOS_HTTP_PORT" \
  setsid nohup java -jar educloud-backend/educloud-gateway/target/educloud-gateway-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/gateway.log 2>&1 < /dev/null &
  printf "  educloud-gateway started (8080/8081)\n"
else
  printf "  educloud-gateway already running\n"
fi

wait_ready "http://127.0.0.1:8081/actuator/health/readiness" "educloud-gateway"

# 启动顺序修复（M08）：payment 必须先于 course/order 启动——
# course.payment.refund.queue 与 order.payment.refund.queue 由 payment 声明，
# 消费端被动声明队列缺失会直接启动失败。
printf "[3.5/9] Starting educloud-payment...\n"
if port_free 8093; then
  SERVER_PORT=8093 PAYMENT_MANAGEMENT_PORT=8094 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_PAYMENT_DB_PASSWORD="${EDUCLOUD_PAYMENT_DB_PASSWORD:-0776b911c75c80efcb36c841c888e285a73e46c7ad721be0}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-educloud_local}" RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:-14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42}" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"${NACOS_HTTP_PORT:-8848}" \
  EDUCLOUD_PAYMENT_NACOS_USERNAME="${EDUCLOUD_PAYMENT_NACOS_USERNAME:-${NACOS_ADMIN_USERNAME:-nacos}}" EDUCLOUD_PAYMENT_NACOS_PASSWORD="${NACOS_PAYMENT_PASSWORD:-${NACOS_ADMIN_PASSWORD:-nacos}}" \
  PAYMENT_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_PAYMENT_JWT_ISSUER="${EDUCLOUD_PAYMENT_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_PAYMENT_JWT_AUDIENCE="${EDUCLOUD_PAYMENT_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-payment/target/educloud-payment-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/payment.log 2>&1 < /dev/null &
  printf "  educloud-payment started (8093/8094)\n"
else
  printf "  educloud-payment already running\n"
fi

wait_ready "http://127.0.0.1:8094/actuator/health/readiness" "educloud-payment"

printf "[4/6] Starting educloud-course...\n"
if port_free 8089; then
  SERVER_PORT=8089 COURSE_MANAGEMENT_PORT=8090 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_COURSE_DB_PASSWORD="$EDUCLOUD_COURSE_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="$REDIS_PASSWORD" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="$RABBITMQ_DEFAULT_USER" RABBITMQ_DEFAULT_PASS="$RABBITMQ_DEFAULT_PASS" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"$NACOS_HTTP_PORT" \
  NACOS_GATEWAY_NAMESPACE="${NACOS_GATEWAY_NAMESPACE:-educloud-local}" \
  NACOS_GATEWAY_DISCOVERY_GROUP="${NACOS_GATEWAY_DISCOVERY_GROUP:-EDUCLOUD_SERVICES}" \
  EDUCLOUD_COURSE_NACOS_USERNAME="$EDUCLOUD_COURSE_NACOS_USERNAME" EDUCLOUD_COURSE_NACOS_PASSWORD="$NACOS_COURSE_PASSWORD" \
  COURSE_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_COURSE_JWT_ISSUER="${EDUCLOUD_COURSE_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_COURSE_JWT_AUDIENCE="${EDUCLOUD_COURSE_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_COURSE_FILE_ENDPOINT="${EDUCLOUD_COURSE_FILE_ENDPOINT:-http://127.0.0.1:8087}" \
  EDUCLOUD_COURSE_FILE_CLIENT_ID="${EDUCLOUD_COURSE_FILE_CLIENT_ID:-educloud-course}" \
  EDUCLOUD_COURSE_FILE_CLIENT_SECRET="$EDUCLOUD_COURSE_FILE_CLIENT_SECRET" \
  EDUCLOUD_COURSE_FILE_ENABLED="${EDUCLOUD_COURSE_FILE_ENABLED:-true}" \
  EDUCLOUD_COURSE_USER_TOKEN_ENDPOINT="${EDUCLOUD_COURSE_USER_TOKEN_ENDPOINT:-http://127.0.0.1:8082}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-course/target/educloud-course-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/course.log 2>&1 < /dev/null &
  printf "  educloud-course started (8089/8090)\n"
else
  printf "  educloud-course already running\n"
fi

wait_ready "http://127.0.0.1:8090/actuator/health/readiness" "educloud-course"

printf "[5/6] Starting educloud-file...\n"
if port_free 8087; then
  SERVER_PORT=8087 FILE_MANAGEMENT_PORT=8088 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_FILE_DB_PASSWORD="$EDUCLOUD_FILE_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="$REDIS_PASSWORD" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="$RABBITMQ_DEFAULT_USER" RABBITMQ_DEFAULT_PASS="$RABBITMQ_DEFAULT_PASS" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"$NACOS_HTTP_PORT" \
  EDUCLOUD_FILE_NACOS_USERNAME=educloud_file EDUCLOUD_FILE_NACOS_PASSWORD="$NACOS_FILE_PASSWORD" \
  MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://127.0.0.1:9000}" \
  MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-}" MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-}" \
  EDUCLOUD_FILE_BUCKET="${EDUCLOUD_FILE_BUCKET:-educloud-files}" \
  FILE_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_FILE_JWT_ISSUER="${EDUCLOUD_FILE_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_FILE_JWT_AUDIENCE="${EDUCLOUD_FILE_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_FILE_INTERNAL_BOOTSTRAP_KEY="${EDUCLOUD_FILE_INTERNAL_BOOTSTRAP_KEY:-}" \
  EDUCLOUD_FILE_INTERNAL_ALLOWED_CLIENT_IDS="${EDUCLOUD_FILE_INTERNAL_ALLOWED_CLIENT_IDS:-user-service,educloud-course,educloud-content}" \
  EDUCLOUD_FILE_INTERNAL_AUDIENCE="${EDUCLOUD_FILE_INTERNAL_AUDIENCE:-educloud-file}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-file/target/educloud-file-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/file.log 2>&1 < /dev/null &
  printf "  educloud-file started (8087/8088)\n"
else
  printf "  educloud-file already running\n"
fi

wait_ready "http://127.0.0.1:8088/actuator/health/readiness" "educloud-file"

printf "[6/8] Starting educloud-content...\n"
if port_free 8085; then
  SERVER_PORT=8085 CONTENT_MANAGEMENT_PORT=8086 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_CONTENT_DB_PASSWORD="$EDUCLOUD_CONTENT_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="$REDIS_PASSWORD" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="$RABBITMQ_DEFAULT_USER" RABBITMQ_DEFAULT_PASS="$RABBITMQ_DEFAULT_PASS" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"$NACOS_HTTP_PORT" \
  EDUCLOUD_CONTENT_NACOS_USERNAME=educloud_content EDUCLOUD_CONTENT_NACOS_PASSWORD="b2b6c6a9387119adf22914f13320eed100c19ec5edcdc760" \
  CONTENT_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_CONTENT_JWT_ISSUER="${EDUCLOUD_CONTENT_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_CONTENT_JWT_AUDIENCE="${EDUCLOUD_CONTENT_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_CONTENT_FILE_ENDPOINT="${EDUCLOUD_CONTENT_FILE_ENDPOINT:-http://127.0.0.1:8087}" \
  EDUCLOUD_CONTENT_FILE_CLIENT_ID="${EDUCLOUD_CONTENT_FILE_CLIENT_ID:-educloud-content}" \
  EDUCLOUD_CONTENT_FILE_CLIENT_SECRET="${EDUCLOUD_CONTENT_FILE_CLIENT_SECRET:-${EDUCLOUD_COURSE_FILE_CLIENT_SECRET}}" \
  EDUCLOUD_CONTENT_USER_TOKEN_ENDPOINT="${EDUCLOUD_CONTENT_USER_TOKEN_ENDPOINT:-http://127.0.0.1:8082}" \
  EDUCLOUD_CONTENT_COURSE_CLIENT_SECRET="${EDUCLOUD_CONTENT_COURSE_CLIENT_SECRET:-${EDUCLOUD_CONTENT_FILE_CLIENT_SECRET}}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-content/target/educloud-content-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/content.log 2>&1 < /dev/null &
  printf "  educloud-content started (8085/8086)\n"
else
  printf "  educloud-content already running\n"
fi

wait_ready "http://127.0.0.1:8086/actuator/health/readiness" "educloud-content"

printf "[7/8] Starting educloud-order...\n"
if port_free 8091; then
  SERVER_PORT=8091 ORDER_MANAGEMENT_PORT=8092 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_ORDER_DB_PASSWORD="${EDUCLOUD_ORDER_DB_PASSWORD:-b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-educloud_local}" RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:-14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42}" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"${NACOS_HTTP_PORT:-8848}" \
  EDUCLOUD_ORDER_NACOS_USERNAME="${EDUCLOUD_ORDER_NACOS_USERNAME:-${NACOS_ADMIN_USERNAME:-nacos}}" EDUCLOUD_ORDER_NACOS_PASSWORD="${NACOS_ORDER_PASSWORD:-${NACOS_ADMIN_PASSWORD:-nacos}}" \
  ORDER_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_ORDER_JWT_ISSUER="${EDUCLOUD_ORDER_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_ORDER_JWT_AUDIENCE="${EDUCLOUD_ORDER_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/order.log 2>&1 < /dev/null &
  printf "  educloud-order started (8091/8092)\n"
else
  printf "  educloud-order already running\n"
fi

wait_ready "http://127.0.0.1:8092/actuator/health/readiness" "educloud-order"

printf "[8/9] Starting educloud-live...\n"
if port_free 8095; then
  SERVER_PORT=8095 LIVE_MANAGEMENT_PORT=8096 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_LIVE_DB_PASSWORD="${EDUCLOUD_LIVE_DB_PASSWORD:-${EDUCLOUD_PAYMENT_DB_PASSWORD:-0776b911c75c80efcb36c841c888e285a73e46c7ad721be0}}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  NACOS_SERVER_ADDR=127.0.0.1:"${NACOS_HTTP_PORT:-8848}" \
  EDUCLOUD_LIVE_NACOS_USERNAME="${EDUCLOUD_LIVE_NACOS_USERNAME:-${NACOS_ADMIN_USERNAME:-nacos}}" EDUCLOUD_LIVE_NACOS_PASSWORD="${NACOS_LIVE_PASSWORD:-${NACOS_ADMIN_PASSWORD:-nacos}}" \
  LIVE_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_LIVE_JWT_ISSUER="${EDUCLOUD_LIVE_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_LIVE_JWT_AUDIENCE="${EDUCLOUD_LIVE_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_LIVE_COURSE_ENDPOINT="${EDUCLOUD_LIVE_COURSE_ENDPOINT:-http://127.0.0.1:8089}" \
  EDUCLOUD_LIVE_FILE_ENDPOINT="${EDUCLOUD_LIVE_FILE_ENDPOINT:-http://127.0.0.1:8087}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-live/target/educloud-live-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/live.log 2>&1 < /dev/null &
  printf "  educloud-live started (8095/8096)\n"
else
  printf "  educloud-live already running\n"
fi

wait_ready "http://127.0.0.1:8096/actuator/health/readiness" "educloud-live"

printf "[9/10] Starting educloud-notification...\n"
if port_free 8097; then
  SERVER_PORT=8097 NOTIFICATION_MANAGEMENT_PORT=8098 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_NOTIFICATION_DB_PASSWORD="${EDUCLOUD_NOTIFICATION_DB_PASSWORD:-${EDUCLOUD_ORDER_DB_PASSWORD:-b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95}}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT="${RABBITMQ_AMQP_PORT:-5672}" \
  RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-educloud_local}" RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:-14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42}" \
  RABBITMQ_DEFAULT_VHOST="${RABBITMQ_DEFAULT_VHOST:-educloud}" \
  NACOS_SERVER_ADDR=127.0.0.1:"${NACOS_HTTP_PORT:-8848}" \
  EDUCLOUD_NOTIFICATION_NACOS_USERNAME="${EDUCLOUD_NOTIFICATION_NACOS_USERNAME:-${NACOS_ADMIN_USERNAME:-nacos}}" EDUCLOUD_NOTIFICATION_NACOS_PASSWORD="${NACOS_NOTIFICATION_PASSWORD:-${NACOS_ADMIN_PASSWORD:-nacos}}" \
  NOTIFICATION_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_NOTIFICATION_JWT_ISSUER="${EDUCLOUD_NOTIFICATION_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_NOTIFICATION_JWT_AUDIENCE="${EDUCLOUD_NOTIFICATION_JWT_AUDIENCE:-educloud-api}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-notification/target/educloud-notification-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/notification.log 2>&1 < /dev/null &
  printf "  educloud-notification started (8097/8098)\n"
else
  printf "  educloud-notification already running\n"
fi

wait_ready "http://127.0.0.1:8098/actuator/health/readiness" "educloud-notification"

printf "[10/10] Starting frontend dev servers...\n"
start_portal() {
  local dir="$1" port="$2"
  if port_free "$port"; then
    (cd "$repo_root/educloud-frontend/$dir" \
      && VITE_GATEWAY_TARGET=http://127.0.0.1:8080 \
      setsid nohup npx vite --port "$port" > "/tmp/vm-vite-$dir.log" 2>&1 < /dev/null &)
    printf "  %s started (: %s)\n" "$dir" "$port"
  else
    printf "  %s already running (: %s)\n" "$dir" "$port"
  fi
}
start_portal student-portal 5173
start_portal teacher-portal 5174
start_portal admin-portal 5175

sleep 5
printf "\nAll set. Open in your browser:\n"
printf "  Student:      http://192.168.100.136:5173\n"
printf "  Teacher:      http://192.168.100.136:5174  (demo_teacher / EduCloud@2026)\n"
printf "  Admin:        http://192.168.100.136:5175  (demo_admin / EduCloud@2026)\n"
printf "  File:         http://192.168.100.136:8087  (management 8088)\n"
printf "  Course:       http://192.168.100.136:8089  (management 8090)\n"
printf "  Content:      http://192.168.100.136:8085  (management 8086)\n"
printf "  Order:        http://192.168.100.136:8091  (management 8092)\n"
printf "  Payment:      http://192.168.100.136:8093  (management 8094)\n"
printf "  Live:         http://192.168.100.136:8095  (management 8096)\n"
printf "  Notification: http://192.168.100.136:8097  (management 8098)\n"
printf "\nLogs: /tmp/educloud-live/{user,gateway,course,file,content,order,payment,live,notification}.log, /tmp/vm-vite-*.log\n"
