#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/tests/user-gateway-e2e-tests.sh USER_JAR GATEWAY_JAR

Run the M03 User + Gateway real login end-to-end gate on a Rocky host with
the shared MySQL/Redis/RabbitMQ/Nacos stack already running (docker compose up).
The caller must export deploy/docker-compose/.env plus:
  MYSQL_HOST MYSQL_PORT MYSQL_ROOT_PASSWORD
  EDUCLOUD_USER_DB_PASSWORD EDUCLOUD_USER_MIGRATION_PASSWORD
  REDIS_HOST REDIS_PASSWORD
  RABBITMQ_HOST RABBITMQ_PORT RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS
  NACOS_SERVER_ADDR NACOS_GATEWAY_USERNAME NACOS_GATEWAY_PASSWORD
  EDUCLOUD_USER_NACOS_USERNAME EDUCLOUD_USER_NACOS_PASSWORD
  EDUCLOUD_USER_JWT_ISSUER EDUCLOUD_USER_JWT_AUDIENCE
  GATEWAY_JWT_ISSUER GATEWAY_JWT_AUDIENCE (must match the user values)
  GATEWAY_RATE_LIMIT_HMAC_SECRET (base64, >= 32 bytes)
  GATEWAY_ALLOWED_ORIGINS
USAGE
}

[[ $# == 2 ]] || { usage >&2; exit 2; }

for command_name in base64 curl java mktemp python3 realpath redis-cli sleep stat; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found"
done

user_jar="$1"
gateway_jar="$2"
[[ -f "$user_jar" && -r "$user_jar" ]] || fail 'User executable JAR is not readable'
[[ -f "$gateway_jar" && -r "$gateway_jar" ]] || fail 'Gateway executable JAR is not readable'
user_jar="$(realpath -e -- "$user_jar")"
gateway_jar="$(realpath -e -- "$gateway_jar")"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_dir="$repo_root/deploy/docker-compose"

required_variables=(
  MYSQL_HOST MYSQL_PORT MYSQL_ROOT_PASSWORD
  EDUCLOUD_USER_DB_PASSWORD EDUCLOUD_USER_MIGRATION_PASSWORD
  REDIS_HOST REDIS_PASSWORD
  RABBITMQ_HOST RABBITMQ_PORT RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS
  NACOS_SERVER_ADDR NACOS_GATEWAY_USERNAME NACOS_GATEWAY_PASSWORD
  EDUCLOUD_USER_NACOS_USERNAME EDUCLOUD_USER_NACOS_PASSWORD
  EDUCLOUD_USER_JWT_ISSUER EDUCLOUD_USER_JWT_AUDIENCE
  GATEWAY_JWT_ISSUER GATEWAY_JWT_AUDIENCE
  GATEWAY_RATE_LIMIT_HMAC_SECRET GATEWAY_ALLOWED_ORIGINS
)
for variable_name in "${required_variables[@]}"; do
  [[ -n "${!variable_name:-}" ]] || fail "Required environment variable is missing: $variable_name"
done

[[ "$EDUCLOUD_USER_JWT_ISSUER" == "$GATEWAY_JWT_ISSUER" ]] || fail 'EDUCLOUD_USER_JWT_ISSUER must equal GATEWAY_JWT_ISSUER'
[[ "$EDUCLOUD_USER_JWT_AUDIENCE" == "$GATEWAY_JWT_AUDIENCE" ]] || fail 'EDUCLOUD_USER_JWT_AUDIENCE must equal GATEWAY_JWT_AUDIENCE'
[[ "$REDIS_PORT" =~ ^[0-9]{1,5}$ ]] || fail 'REDIS_PORT is invalid'
[[ "$NACOS_SERVER_ADDR" == 127.0.0.1:* ]] || fail 'NACOS_SERVER_ADDR must point at localhost for e2e'
hmac_bytes="$(printf '%s' "$GATEWAY_RATE_LIMIT_HMAC_SECRET" | base64 -d 2>/dev/null | wc -c | tr -d ' ')" || fail 'Gateway rate-limit HMAC secret is not valid Base64'
(( hmac_bytes >= 32 )) || fail 'Gateway rate-limit HMAC secret is shorter than 32 bytes'

redis_host="${REDIS_HOST:-127.0.0.1}"
nacos_http_port="${NACOS_SERVER_ADDR##*:}"
[[ "$nacos_http_port" =~ ^[0-9]{1,5}$ ]] || fail 'NACOS_SERVER_ADDR port is invalid'

e2e_id="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:16])')"
environment="m03-e2e-${e2e_id}"
[[ "$environment" =~ ^[a-z0-9-]{1,32}$ ]] || fail 'EDUCLOUD_ENVIRONMENT is invalid'
username="e2e-${e2e_id}"
email="e2e-${e2e_id}@example.com"
# hex 含字母，phone 校验只允许数字，故用纯数字尾号
phone_tail="$(python3 -c 'import uuid; print(str(uuid.uuid4().int)[-8:])')"
phone="138${phone_tail}"
password="E2ePassword_${e2e_id}"
username2="e2e2-${e2e_id}"
username3="e2e3-${e2e_id}"
email2="e2e2-${e2e_id}@example.com"
phone2="137${phone_tail}"
email3="e2e3-${e2e_id}@example.com"
phone3="136${phone_tail}"

work_dir="$(mktemp -d /tmp/educloud-user-e2e.XXXXXX)"
chmod 700 "$work_dir"
user_pid_file="$work_dir/user.pid"
gateway_pid_file="$work_dir/gateway.pid"
user_log="$work_dir/user.log"
gateway_log="$work_dir/gateway.log"
material_dir="$work_dir/material"
mkdir -p "$material_dir"
chmod 700 "$material_dir"
user_pid=''
gateway_pid=''
user_started=0
gateway_started=0

redis_call() {
  REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h "$redis_host" -p "$REDIS_PORT" --no-auth-warning "$@"
}

mysql_call() {
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u root --batch --skip-column-names "$@"
}

nacos_call() {
  local method="$1"
  local path="$2"
  shift 2
  {
    printf 'silent\nshow-error\nfail\n'
    printf 'request = "%s"\n' "$method"
    printf 'url = "http://127.0.0.1:%s/nacos%s"\n' "$nacos_http_port" "$path"
    [[ "$method" == 'GET' ]] && printf 'get\n'
    local field
    for field in "$@"; do
      printf 'data-urlencode = "%s"\n' "$field"
    done
  } | curl --config -
}

request() {
  # request METHOD URL OUT_FILE [cookie_jar] [body] [header...]
  local method="$1"
  local url="$2"
  local out_file="$3"
  local jar="${4:-}"
  local body="${5:-}"
  shift 5 || true
  local args=(--silent --show-error --connect-timeout 2 --max-time 8 --request "$method" \
    --output "$out_file" --write-out '%{http_code}\n' \
    --header 'Content-Type: application/json' --header 'Accept: application/json')
  [[ -z "$jar" ]] || args+=(--cookie "$jar" --cookie-jar "$jar")
  [[ -z "$body" ]] || args+=(--data "$body")
  local header
  for header in "$@"; do
    args+=(--header "$header")
  done
  curl "${args[@]}" "$url"
}

cleanup() {
  local exit_code=$?
  set +e
  if [[ -n "$gateway_pid" ]] && kill -0 "$gateway_pid" 2>/dev/null; then
    kill -TERM "$gateway_pid" 2>/dev/null
    for _ in {1..40}; do
      kill -0 "$gateway_pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -0 "$gateway_pid" 2>/dev/null && kill -KILL "$gateway_pid" 2>/dev/null
    wait "$gateway_pid" 2>/dev/null
  fi
  if [[ -n "$user_pid" ]] && kill -0 "$user_pid" 2>/dev/null; then
    kill -TERM "$user_pid" 2>/dev/null
    for _ in {1..40}; do
      kill -0 "$user_pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -0 "$user_pid" 2>/dev/null && kill -KILL "$user_pid" 2>/dev/null
    wait "$user_pid" 2>/dev/null
  fi
  if (( user_started == 1 )); then
    while IFS= read -r key; do
      [[ -n "$key" ]] && redis_call DEL "$key" >/dev/null 2>&1
    done < <(redis_call --scan --pattern "educloud:{${environment}:auth}:session:*" 2>/dev/null)
  fi
  if command -v mysql >/dev/null 2>&1; then
    mysql_call educloud_user -e "DELETE FROM login_audit WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM refresh_session WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM user_profile WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM outbox_event WHERE aggregate_id IN (SELECT id FROM sys_user WHERE username IN ('$username','$username2','$username3')); DELETE FROM sys_user WHERE username IN ('$username','$username2','$username3');" >/dev/null 2>&1
  fi
  unset GATEWAY_RATE_LIMIT_HMAC_SECRET NACOS_GATEWAY_PASSWORD EDUCLOUD_USER_NACOS_PASSWORD REDIS_PASSWORD MYSQL_ROOT_PASSWORD
  case "$work_dir" in
    /tmp/educloud-user-e2e.*) rm -rf -- "$work_dir" ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

[[ "$(redis_call PING 2>/dev/null)" == 'PONG' ]] || fail 'Redis is not reachable with the configured credential'
mysql_call -N -e 'SELECT 1' >/dev/null 2>&1 || fail 'MySQL is not reachable with the root credential'
curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:${nacos_http_port}/nacos/v1/console/health/readiness" >/dev/null || fail 'Nacos readiness endpoint is unavailable'

python3 - <<'PY' || fail 'Ports 8080, 8081, 8082 or 8083 are already occupied'
import socket
for port in (8080, 8081, 8082, 8083):
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", port))
PY
# 1) 生成 User 签名密钥材料（私钥给 User，公共 JWKS 给 Gateway）
bash "$repo_root/deploy/scripts/generate-user-jwt-keys.sh" --private-key "$material_dir/private.pem" --jwks "$material_dir/jwks.json" >/dev/null
chmod 600 "$material_dir/private.pem"

# 2) 数据库迁移到最新（幂等；迁移账号只用于 DDL）
MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" EDUCLOUD_USER_MIGRATION_PASSWORD="$EDUCLOUD_USER_MIGRATION_PASSWORD" bash "$repo_root/deploy/scripts/run-migrations.sh" --service user >/dev/null

# 3) 启动 User 服务（8082 业务 / 8083 管理）
export SERVER_PORT=8082
export USER_MANAGEMENT_ADDRESS=127.0.0.1
export USER_MANAGEMENT_PORT=8083
export MYSQL_HOST="$MYSQL_HOST"
export MYSQL_PORT="$MYSQL_PORT"
export EDUCLOUD_USER_DB_PASSWORD="$EDUCLOUD_USER_DB_PASSWORD"
export REDIS_HOST="$redis_host"
export REDIS_PORT="$REDIS_PORT"
export REDIS_PASSWORD="$REDIS_PASSWORD"
export RABBITMQ_HOST="$RABBITMQ_HOST"
export RABBITMQ_PORT="$RABBITMQ_PORT"
export RABBITMQ_DEFAULT_USER="$RABBITMQ_DEFAULT_USER"
export RABBITMQ_DEFAULT_PASS="$RABBITMQ_DEFAULT_PASS"
export NACOS_SERVER_ADDR="$NACOS_SERVER_ADDR"
export EDUCLOUD_USER_NACOS_USERNAME="$EDUCLOUD_USER_NACOS_USERNAME"
export EDUCLOUD_USER_NACOS_PASSWORD="$EDUCLOUD_USER_NACOS_PASSWORD"
export USER_JWT_PRIVATE_KEY_LOCATION="$material_dir/private.pem"
export EDUCLOUD_USER_JWT_ISSUER="$EDUCLOUD_USER_JWT_ISSUER"
export EDUCLOUD_USER_JWT_AUDIENCE="$EDUCLOUD_USER_JWT_AUDIENCE"
export EDUCLOUD_ENVIRONMENT="$environment"
export SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1
: >"$user_log"
chmod 600 "$user_log"
java -jar "$user_jar" >"$user_log" 2>&1 &
user_pid=$!
printf '%s\n' "$user_pid" >"$user_pid_file"
chmod 600 "$user_pid_file"
user_started=1

user_ready=0
for _ in {1..90}; do
  kill -0 "$user_pid" 2>/dev/null || fail 'User process exited during startup'
  if curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:8083/actuator/health/readiness" >/dev/null 2>&1; then
    user_ready=1
    break
  fi
  sleep 1
done
(( user_ready == 1 )) || fail 'User readiness probe did not become healthy'

# 4) 启动 Gateway（8080 业务 / 8081 管理）
export SERVER_PORT=8080
export GATEWAY_MANAGEMENT_ADDRESS=127.0.0.1
export GATEWAY_MANAGEMENT_PORT=8081
export GATEWAY_JWKS_LOCATION="file:$material_dir/jwks.json"
export GATEWAY_JWT_ISSUER="$GATEWAY_JWT_ISSUER"
export GATEWAY_JWT_AUDIENCE="$GATEWAY_JWT_AUDIENCE"
export GATEWAY_RATE_LIMIT_HMAC_SECRET="$GATEWAY_RATE_LIMIT_HMAC_SECRET"
export GATEWAY_ALLOWED_ORIGINS="$GATEWAY_ALLOWED_ORIGINS"
export NACOS_GATEWAY_NAMESPACE="${NACOS_GATEWAY_NAMESPACE:-educloud-local}"
export NACOS_GATEWAY_CONFIG_GROUP="${NACOS_GATEWAY_CONFIG_GROUP:-EDUCLOUD_GATEWAY}"
export NACOS_GATEWAY_DISCOVERY_GROUP="${NACOS_GATEWAY_DISCOVERY_GROUP:-EDUCLOUD_SERVICES}"
export NACOS_GATEWAY_USERNAME="$NACOS_GATEWAY_USERNAME"
export NACOS_GATEWAY_PASSWORD="$NACOS_GATEWAY_PASSWORD"
export EDUCLOUD_ENVIRONMENT="$environment"
: >"$gateway_log"
chmod 600 "$gateway_log"
java -jar "$gateway_jar" >"$gateway_log" 2>&1 &
gateway_pid=$!
printf '%s\n' "$gateway_pid" >"$gateway_pid_file"
chmod 600 "$gateway_pid_file"
gateway_started=1

gateway_ready=0
for _ in {1..90}; do
  kill -0 "$gateway_pid" 2>/dev/null || fail 'Gateway process exited during startup'
  if curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:8081/actuator/health/readiness" >/dev/null 2>&1; then
    gateway_ready=1
    break
  fi
  sleep 1
done
(( gateway_ready == 1 )) || fail 'Gateway readiness probe did not become healthy'

printf 'User and Gateway are ready on environment %s\n' "$environment"
# 5) 注册学生成功；重复用户名 409
reg_body="{\"username\":\"$username\",\"password\":\"$password\",\"email\":\"$email\",\"phone\":\"$phone\",\"displayName\":\"E2E User\"}"
reg_status="$(request POST http://127.0.0.1:8080/api/v1/auth/register "$work_dir/reg1.json" "" "$reg_body")"
[[ "$reg_status" == '201' ]] || { cat "$work_dir/reg1.json"; fail 'student registration did not return 201'; }
dup_status="$(request POST http://127.0.0.1:8080/api/v1/auth/register "$work_dir/reg2.json" "" "$reg_body")"
[[ "$dup_status" == '409' ]] || fail 'duplicate registration did not return 409'
grep -q 'USERNAME_TAKEN' "$work_dir/reg2.json" || fail 'duplicate registration did not report USERNAME_TAKEN'

# 6) 登录成功：Cookie 存在、Access 可经 Gateway 访问 /api/v1/me
jar1="$work_dir/u1.jar"
login_body="{\"loginName\":\"$username\",\"password\":\"$password\",\"portal\":\"STUDENT\"}"
login_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login1.json" "$jar1" "$login_body")"
[[ "$login_status" == '200' ]] || fail 'login did not return 200'
access1="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["accessToken"])' "$work_dir/login1.json")"
[[ -n "$access1" && "$access1" =~ ^[A-Za-z0-9_.-]+\.[A-Za-z0-9_.-]+\.[A-Za-z0-9_.-]+$ ]] || fail 'login response has no valid access token'
grep -q 'refresh_token' "$jar1" || fail 'login did not set the refresh_token cookie'
# Nacos 发现可能有秒级延迟：重试直到 200（或真实失败）。
me_status=''
for _ in {1..20}; do
  me_status="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me1.json" "" "" "Authorization: Bearer $access1")"
  [[ "$me_status" == '200' ]] && break
  sleep 2
done
[[ "$me_status" == '200' ]] || { cat "$work_dir/me1.json"; fail "/api/v1/me via Gateway did not return 200 (got $me_status)"; }
grep -q "$username" "$work_dir/me1.json" || fail '/me did not return the logged-in user'

# 7) 并发刷新（同一 Refresh Token 两个并发请求）：一个成功一个 409
# 注意：裸 wait 会等待 java 服务后台 job；curl cookie-jar 并发写同一文件会竞争，
# 因此两个请求各用独立 cookie 副本，成功后把成功者的 jar 提升为当前会话。
# 轮询结果文件（裸 wait 会等 java 服务导致永久阻塞）。
cp "$jar1" "$work_dir/u1_a.jar"
cp "$jar1" "$work_dir/u1_b.jar"
: >"$work_dir/ref_a.code"
: >"$work_dir/ref_b.code"
(request POST http://127.0.0.1:8080/api/v1/auth/refresh "$work_dir/ref_a.json" "$work_dir/u1_a.jar" "" >"$work_dir/ref_a.code" 2>/dev/null &)
(request POST http://127.0.0.1:8080/api/v1/auth/refresh "$work_dir/ref_b.json" "$work_dir/u1_b.jar" "" >"$work_dir/ref_b.code" 2>/dev/null &)
for _ in {1..30}; do
  [[ -s "$work_dir/ref_a.code" && -s "$work_dir/ref_b.code" ]] && break
  sleep 0.5
done
concurrent_codes="$(cat "$work_dir/ref_a.code" "$work_dir/ref_b.code" | sort -u | tr '\n' ' ' | sed 's/ $//')"
[[ "$concurrent_codes" == '200 409' ]] || fail "concurrent refresh did not yield one success and one 409 (got: $concurrent_codes)"
grep -q 'REFRESH_ALREADY_ROTATED' "$work_dir/ref_a.json" "$work_dir/ref_b.json" || fail 'losing refresh did not report REFRESH_ALREADY_ROTATED'
if [[ "$(cat "$work_dir/ref_a.code")" == '200' ]]; then
  cp "$work_dir/u1_a.jar" "$jar1"
else
  cp "$work_dir/u1_b.jar" "$jar1"
fi

# 8) 宽限窗口外重用：家族撤销，旧 Access 立即 401
cp "$jar1" "$work_dir/u1_r2.jar"
sleep 6
refresh_ok="$(request POST http://127.0.0.1:8080/api/v1/auth/refresh "$work_dir/ref_ok.json" "$jar1" "")"
[[ "$refresh_ok" == '200' ]] || fail 'refresh after grace window did not return 200'
# 等待宽限窗口（5s）过去，确保 R2 重用落在窗外（窗外重用触发家族撤销）。
sleep 6
reuse_status="$(request POST http://127.0.0.1:8080/api/v1/auth/refresh "$work_dir/ref_reuse.json" "$work_dir/u1_r2.jar" "")"
# 窗外重用：401 SESSION_REUSE_DETECTED（与 409 REFRESH_ALREADY_ROTATED 的宽限内冲突区分）。
[[ "$reuse_status" == '401' ]] || { cat "$work_dir/ref_reuse.json"; fail "reuse outside the grace window did not return 401 (got $reuse_status)"; }
grep -Fq 'SESSION_REUSE_DETECTED' "$work_dir/ref_reuse.json" || { cat "$work_dir/ref_reuse.json"; fail 'reuse did not report SESSION_REUSE_DETECTED'; }
me_after_reuse="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_reuse.json" "" "" "Authorization: Bearer $access1")"
[[ "$me_after_reuse" == '401' ]] || fail 'old Access did not become 401 after family revocation'

# 9) 注销：旧 Access 立即 401
reg_body2="{\"username\":\"$username2\",\"password\":\"$password\",\"email\":\"$email2\",\"phone\":\"$phone2\",\"displayName\":\"E2E Two\"}"
reg2_status="$(request POST http://127.0.0.1:8080/api/v1/auth/register "$work_dir/reg21.json" "" "$reg_body2")"
[[ "$reg2_status" == '201' ]] || { cat "$work_dir/reg21.json"; fail 'second registration did not return 201'; }
login_body2="{\"loginName\":\"$username2\",\"password\":\"$password\",\"portal\":\"STUDENT\"}"
jar2="$work_dir/u2.jar"
login2_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login2.json" "$jar2" "$login_body2")"
[[ "$login2_status" == '200' ]] || fail 'second login did not return 200'
access2="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["accessToken"])' "$work_dir/login2.json")"
logout_status="$(request POST http://127.0.0.1:8080/api/v1/auth/logout "$work_dir/logout.json" "$jar2" "")"
[[ "$logout_status" == '204' ]] || { cat "$work_dir/logout.json"; cp "$gateway_log" /tmp/m03-e2e-gateway.log 2>/dev/null; cp "$user_log" /tmp/m03-e2e-user.log 2>/dev/null; fail "logout did not return 204 (got $logout_status)"; }
me_after_logout="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_logout.json" "" "" "Authorization: Bearer $access2")"
[[ "$me_after_logout" == '401' ]] || fail 'old Access did not become 401 after logout'

# 10) 改密：旧 Access 401，当前会话可刷新，新密码可登录
login2b_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login2b.json" "$jar2" "$login_body2")"
[[ "$login2b_status" == '200' ]] || fail 're-login after logout did not return 200'
access2b="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["accessToken"])' "$work_dir/login2b.json")"
new_password="New${password}"
change_body="{\"oldPassword\":\"$password\",\"newPassword\":\"$new_password\"}"
change_status="$(request POST http://127.0.0.1:8080/api/v1/auth/password/change "$work_dir/change.json" "$jar2" "$change_body" "Authorization: Bearer $access2b")"
[[ "$change_status" == '200' ]] || fail 'password change did not return 200'
me_after_change="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_change.json" "" "" "Authorization: Bearer $access2b")"
[[ "$me_after_change" == '401' ]] || fail 'old Access did not become 401 after password change'
refresh_after_change="$(request POST http://127.0.0.1:8080/api/v1/auth/refresh "$work_dir/ref_change.json" "$jar2" "")"
[[ "$refresh_after_change" == '200' ]] || fail 'current refresh token did not survive the password change'
access2c="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["accessToken"])' "$work_dir/ref_change.json")"
login_new_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login_new.json" "$work_dir/u2_new.jar" "{\"loginName\":\"$username2\",\"password\":\"$new_password\",\"portal\":\"STUDENT\"}")"
[[ "$login_new_status" == '200' ]] || fail 'login with the new password did not return 200'
login_old_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login_old.json" "$work_dir/u2_old.jar" "$login_body2")"
[[ "$login_old_status" == '401' ]] || fail 'login with the old password did not return 401'
grep -q 'INVALID_CREDENTIALS' "$work_dir/login_old.json" || fail 'old password login did not report INVALID_CREDENTIALS'
access_final="$access2c"
# 11) 禁用：模拟 UserStatusService 禁用效果（DB DISABLED + tokenVersion+1 + Redis REVOKED），旧 Access 立即 401
reg_body3="{\"username\":\"$username3\",\"password\":\"$password\",\"email\":\"$email3\",\"phone\":\"$phone3\",\"displayName\":\"E2E Three\"}"
reg3_status="$(request POST http://127.0.0.1:8080/api/v1/auth/register "$work_dir/reg31.json" "" "$reg_body3")"
[[ "$reg3_status" == '201' ]] || { cat "$work_dir/reg31.json"; fail 'third registration did not return 201'; }
login_body3="{\"loginName\":\"$username3\",\"password\":\"$password\",\"portal\":\"STUDENT\"}"
jar3="$work_dir/u3.jar"
login3_status="$(request POST http://127.0.0.1:8080/api/v1/auth/login "$work_dir/login3.json" "$jar3" "$login_body3")"
[[ "$login3_status" == '200' ]] || fail 'third login did not return 200'
access3="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["data"]["accessToken"])' "$work_dir/login3.json")"
family3="$(mysql_call educloud_user -N -e "SELECT family_id FROM refresh_session WHERE user_id = (SELECT id FROM sys_user WHERE username = '$username3') ORDER BY id DESC LIMIT 1")"
[[ -n "$family3" ]] || fail 'no refresh session family found for the third user'
mysql_call educloud_user -e "UPDATE sys_user SET status = 'DISABLED', token_version = token_version + 1, updated_at = NOW(3) WHERE username = '$username3'" >/dev/null
new_version3="$(mysql_call educloud_user -N -e "SELECT token_version FROM sys_user WHERE username = '$username3'")"
redis_call HSET "educloud:{${environment}:auth}:session:${family3}" subject "$username3" status REVOKED tokenVersion "$new_version3" >/dev/null
redis_call PEXPIRE "educloud:{${environment}:auth}:session:${family3}" 900000 >/dev/null
me_after_disable="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_disable.json" "" "" "Authorization: Bearer $access3")"
[[ "$me_after_disable" == '401' ]] || fail 'old Access did not become 401 after account disable'

# 12) Gateway Redis 失败关闭语义：停 Redis 后受保护请求 503，恢复后正常
if [[ -f "$compose_dir/compose.yml" && -d "$compose_dir" ]]; then
  (cd "$compose_dir" && docker compose stop redis >/dev/null 2>&1) || fail 'unable to stop the Redis container'
  redis_down=0
  for _ in {1..30}; do
    if ! redis_call PING >/dev/null 2>&1; then
      redis_down=1
      break
    fi
    sleep 1
  done
  (( redis_down == 1 )) || fail 'Redis did not stop within the wait window'
  me_redis_down="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_redis_down.json" "" "" "Authorization: Bearer $access_final")"
  [[ "$me_redis_down" == '503' ]] || fail "Gateway did not fail closed with 503 while Redis was down (got: $me_redis_down)"
  (cd "$compose_dir" && docker compose start redis >/dev/null 2>&1) || fail 'unable to start the Redis container'
  redis_back=0
  for _ in {1..30}; do
    if [[ "$(redis_call PING 2>/dev/null)" == 'PONG' ]]; then
      redis_back=1
      break
    fi
    sleep 1
  done
  (( redis_back == 1 )) || fail 'Redis did not recover after being restarted'
  me_redis_back=''
  for _ in {1..30}; do
    me_redis_back="$(request GET http://127.0.0.1:8080/api/v1/me "$work_dir/me_redis_back.json" "" "" "Authorization: Bearer $access_final")"
    [[ "$me_redis_back" == '200' ]] && break
    sleep 1
  done
  [[ "$me_redis_back" == '200' ]] || fail 'Gateway did not recover after Redis came back'
else
  printf 'SKIP: compose directory not found; Redis fail-closed check skipped\n' >&2
fi

# 13) 共享依赖结束仍健康
[[ "$(redis_call PING 2>/dev/null)" == 'PONG' ]] || fail 'Shared Redis is unhealthy after e2e'
mysql_call -N -e 'SELECT 1' >/dev/null 2>&1 || fail 'Shared MySQL is unhealthy after e2e'
curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:${nacos_http_port}/nacos/v1/console/health/readiness" >/dev/null || fail 'Shared Nacos is unhealthy after e2e'
if [[ -d "$compose_dir" ]]; then
  (cd "$compose_dir" && docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1) || fail 'Shared RabbitMQ is unhealthy after e2e'
fi

# 14) 优雅停止（cleanup trap 负责进程回收与数据/Redis 清理）
printf 'All User-Gateway e2e checks passed on environment %s\n' "$environment"