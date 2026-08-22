#!/usr/bin/env bash

# EduCloud 开发环境一键启动（在 VM/Rocky 上执行）。幂等：已占用端口跳过启动。
# 用法：bash deploy/scripts/start-dev.sh
# 启动：基础设施容器(compose) + educloud-user + educloud-file + educloud-gateway + 三门户 Vite Dev Server。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

port_free() {
  ! ss -tln 2>/dev/null | grep -q ":$1 "
}

wait_ready() {
  local url="$1" label="$2"
  for _ in {1..60}; do
    if curl --fail --silent --max-time 3 "$url" >/dev/null 2>&1; then
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

printf "[1/5] Ensuring infrastructure containers...\n"
docker compose -f deploy/docker-compose/compose.yml up -d >/dev/null 2>&1 || true

printf "[2/5] Ensuring JWT key material...\n"
mkdir -p /tmp/educloud-live
if [[ ! -f /tmp/educloud-live/private.pem ]]; then
  bash deploy/scripts/generate-user-jwt-keys.sh \
    --private-key /tmp/educloud-live/private.pem --jwks /tmp/educloud-live/jwks.json >/dev/null
fi

printf "[3/5] Starting educloud-user and educloud-gateway...\n"
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

printf "[4/5] Starting educloud-file...\n"
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
  EDUCLOUD_FILE_INTERNAL_ALLOWED_CLIENT_IDS="${EDUCLOUD_FILE_INTERNAL_ALLOWED_CLIENT_IDS:-user-service}" \
  EDUCLOUD_FILE_INTERNAL_AUDIENCE="${EDUCLOUD_FILE_INTERNAL_AUDIENCE:-educloud-file}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-file/target/educloud-file-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/file.log 2>&1 < /dev/null &
  printf "  educloud-file started (8087/8088)\n"
else
  printf "  educloud-file already running\n"
fi

wait_ready "http://127.0.0.1:8088/actuator/health/readiness" "educloud-file"

printf "[5/5] Starting frontend dev servers...\n"
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
printf "  Student: http://192.168.100.136:5173\n"
printf "  Teacher: http://192.168.100.136:5174  (demo_teacher / EduCloud@2026)\n"
printf "  Admin:   http://192.168.100.136:5175  (demo_admin / EduCloud@2026)\n"
printf "  File:    http://192.168.100.136:8087  (management 8088)\n"
printf "\nLogs: /tmp/educloud-live/{user,gateway,file}.log, /tmp/vm-vite-*.log\n"