#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/tests/gateway-rocky-smoke-tests.sh JAR MATERIAL_DIR

Run the M02 Gateway smoke gate against already-running local Redis and Nacos.
The caller must first export deploy/docker-compose/.env and MATERIAL_DIR/runtime.env.
USAGE
}

[[ $# == 2 ]] || {
  usage >&2
  exit 2
}

for command_name in base64 curl java mktemp python3 realpath redis-cli stat; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found"
done

jar_path="$1"
material_dir="$2"
[[ -f "$jar_path" && -r "$jar_path" ]] || fail 'Gateway executable JAR is not readable'
jar_path="$(realpath -e -- "$jar_path")"
[[ -d "$material_dir" && ! -L "$material_dir" ]] || fail 'Material directory is not a regular directory'
material_dir="$(realpath -e -- "$material_dir")"
case "$material_dir" in
  /tmp/educloud-gateway.*) ;;
  *) fail 'Material directory must be an isolated /tmp/educloud-gateway.* directory' ;;
esac
[[ "$(stat -c '%a' "$material_dir")" == '700' ]] || fail 'Material directory must have mode 0700'

required_variables=(
  REDIS_PORT
  REDIS_PASSWORD
  NACOS_HTTP_PORT
  NACOS_GATEWAY_NAMESPACE
  NACOS_GATEWAY_CONFIG_GROUP
  NACOS_GATEWAY_DISCOVERY_GROUP
  NACOS_GATEWAY_USERNAME
  NACOS_GATEWAY_PASSWORD
  GATEWAY_JWKS_LOCATION
  GATEWAY_JWT_ISSUER
  GATEWAY_JWT_AUDIENCE
  GATEWAY_RATE_LIMIT_HMAC_SECRET
  GATEWAY_TEST_JWT
)
for variable_name in "${required_variables[@]}"; do
  [[ -n "${!variable_name:-}" ]] || fail "Required environment variable is missing: $variable_name"
done

[[ "$REDIS_PORT" =~ ^[0-9]{1,5}$ ]] || fail 'REDIS_PORT is invalid'
[[ "$NACOS_HTTP_PORT" =~ ^[0-9]{1,5}$ ]] || fail 'NACOS_HTTP_PORT is invalid'
(( 10#$REDIS_PORT >= 1 && 10#$REDIS_PORT <= 65535 )) || fail 'REDIS_PORT is outside the valid range'
(( 10#$NACOS_HTTP_PORT >= 1 && 10#$NACOS_HTTP_PORT <= 65535 )) || \
  fail 'NACOS_HTTP_PORT is outside the valid range'
[[ "$NACOS_GATEWAY_NAMESPACE" == 'educloud-local' ]] || fail 'Gateway Nacos namespace is invalid'
[[ "$NACOS_GATEWAY_CONFIG_GROUP" == 'EDUCLOUD_GATEWAY' ]] || fail 'Gateway Nacos config group is invalid'
[[ "$NACOS_GATEWAY_DISCOVERY_GROUP" == 'EDUCLOUD_SERVICES' ]] || fail 'Gateway Nacos discovery group is invalid'
[[ "$NACOS_GATEWAY_USERNAME" == 'educloud_gateway' ]] || fail 'Gateway Nacos username is invalid'
[[ "$NACOS_GATEWAY_PASSWORD" =~ ^[A-Za-z0-9_.:@%+=-]{16,128}$ ]] || \
  fail 'NACOS_GATEWAY_PASSWORD is invalid'
[[ "$GATEWAY_JWKS_LOCATION" == file:* ]] || fail 'GATEWAY_JWKS_LOCATION must be a file resource'
jwks_path="$(realpath -e -- "${GATEWAY_JWKS_LOCATION#file:}")"
[[ "$jwks_path" == "$material_dir/jwks.json" && -r "$jwks_path" ]] || \
  fail 'GATEWAY_JWKS_LOCATION is outside the material directory'
for required_file in private.pem public.pem token.jwt runtime.env; do
  [[ -f "$material_dir/$required_file" ]] || fail "Generated material is missing: $required_file"
done
[[ "$(stat -c '%a' "$material_dir/private.pem")" == '600' ]] || fail 'Private key mode is not 0600'
[[ "$(stat -c '%a' "$material_dir/runtime.env")" == '600' ]] || fail 'Runtime environment mode is not 0600'
[[ "$GATEWAY_TEST_JWT" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]] || \
  fail 'GATEWAY_TEST_JWT is not a compact three-part token'
hmac_bytes="$(printf '%s' "$GATEWAY_RATE_LIMIT_HMAC_SECRET" | base64 -d 2>/dev/null | wc -c | tr -d ' ')" || \
  fail 'Gateway rate-limit HMAC secret is not valid Base64'
(( hmac_bytes >= 32 )) || fail 'Gateway rate-limit HMAC secret is shorter than 32 bytes'

redis_host="${REDIS_HOST:-127.0.0.1}"
nacos_host="127.0.0.1"
smoke_id="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:16])')"
gateway_environment="m02-smoke-${smoke_id}"
[[ "$gateway_environment" =~ ^[a-z0-9-]{1,32}$ ]] || fail 'EDUCLOUD_ENVIRONMENT is invalid'
work_dir="$(mktemp -d /tmp/educloud-gateway-smoke.XXXXXX)"
chmod 700 "$work_dir"
pid_file="$work_dir/gateway.pid"
log_file="$work_dir/gateway.log"
headers_file="$work_dir/headers"
gateway_pid=''
gateway_started=0
cleanup_material=0

redis_call() {
  REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli \
    -h "$redis_host" -p "$REDIS_PORT" --no-auth-warning "$@"
}

nacos_call() {
  local method="$1"
  local path="$2"
  shift 2
  {
    printf 'silent\nshow-error\nfail\n'
    printf 'request = "%s"\n' "$method"
    printf 'url = "http://%s:%s/nacos%s"\n' "$nacos_host" "$NACOS_HTTP_PORT" "$path"
    [[ "$method" == 'GET' ]] && printf 'get\n'
    local field
    for field in "$@"; do
      printf 'data-urlencode = "%s"\n' "$field"
    done
  } | curl --config -
}

http_status() {
  local path="$1"
  shift
  {
    printf 'silent\nshow-error\n'
    printf 'connect-timeout = 2\nmax-time = 5\n'
    printf 'output = "/dev/null"\n'
    printf 'write-out = "%%{http_code}"\n'
    printf 'dump-header = "%s"\n' "$headers_file"
    printf 'url = "http://127.0.0.1:8080%s"\n' "$path"
    local header
    for header in "$@"; do
      printf 'header = "%s"\n' "$header"
    done
  } | curl --config -
}

authorized_status() {
  local path="$1"
  {
    printf 'silent\nshow-error\n'
    printf 'connect-timeout = 2\nmax-time = 5\n'
    printf 'output = "/dev/null"\n'
    printf 'write-out = "%%{http_code}"\n'
    printf 'url = "http://127.0.0.1:8080%s"\n' "$path"
    printf 'header = "Authorization: Bearer %s"\n' "$GATEWAY_TEST_JWT"
  } | curl --config -
}

capture_headers() {
  local path="$1"
  shift
  : >"$headers_file"
  {
    printf 'silent\nshow-error\n'
    printf 'connect-timeout = 2\nmax-time = 5\n'
    printf 'output = "/dev/null"\n'
    printf 'dump-header = "%s"\n' "$headers_file"
    printf 'url = "http://127.0.0.1:8080%s"\n' "$path"
    local header
    for header in "$@"; do
      printf 'header = "%s"\n' "$header"
    done
  } | curl --config -
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
    if kill -0 "$gateway_pid" 2>/dev/null; then
      kill -KILL "$gateway_pid" 2>/dev/null
    fi
    wait "$gateway_pid" 2>/dev/null
  fi

  if (( gateway_started == 1 )); then
    session_key="educloud:{${gateway_environment}:auth}:session:rocky-session"
    redis_call DEL "$session_key" >/dev/null 2>&1
    while IFS= read -r rate_key; do
      [[ -n "$rate_key" ]] && redis_call DEL "$rate_key" >/dev/null 2>&1
    done < <(redis_call --scan --pattern "educloud:{${gateway_environment}:ratelimit}:*" 2>/dev/null)
  fi

  unset GATEWAY_TEST_JWT GATEWAY_RATE_LIMIT_HMAC_SECRET NACOS_GATEWAY_PASSWORD REDIS_PASSWORD \
    gateway_nacos_token
  case "$work_dir" in
    /tmp/educloud-gateway-smoke.*) rm -rf -- "$work_dir" ;;
  esac
  if (( cleanup_material == 1 )); then
    case "$material_dir" in
      /tmp/educloud-gateway.*) rm -rf -- "$material_dir" ;;
    esac
  fi
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

[[ "$(redis_call PING 2>/dev/null)" == 'PONG' ]] || fail 'Redis is not reachable with the configured credential'
curl --fail --silent --show-error --max-time 5 \
  "http://${nacos_host}:${NACOS_HTTP_PORT}/nacos/v1/console/health/readiness" >/dev/null || \
  fail 'Nacos readiness endpoint is unavailable'

gateway_login="$(nacos_call POST /v1/auth/login \
  "username=$NACOS_GATEWAY_USERNAME" "password=$NACOS_GATEWAY_PASSWORD")" || \
  fail 'Dedicated Gateway Nacos login failed'
gateway_nacos_token="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken", ""))' \
  <<<"$gateway_login" | tr -d '\r')"
(( ${#gateway_nacos_token} >= 8 && ${#gateway_nacos_token} <= 4096 )) \
  && [[ "$gateway_nacos_token" =~ ^[A-Za-z0-9._-]+$ ]] || \
  fail 'Dedicated Gateway Nacos login returned an invalid token'
unset gateway_login

python3 - <<'PY' || fail 'Ports 8080 or 8081 are already occupied'
import socket
for port in (8080, 8081):
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", port))
PY

export SERVER_PORT=8080
export GATEWAY_MANAGEMENT_ADDRESS=127.0.0.1
export GATEWAY_MANAGEMENT_PORT=8081
export REDIS_HOST="$redis_host"
export NACOS_SERVER_ADDR="${nacos_host}:${NACOS_HTTP_PORT}"
export EDUCLOUD_ENVIRONMENT="$gateway_environment"
export GATEWAY_ALLOWED_ORIGINS='https://educloud.local'
export SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1
cleanup_material=1
: >"$log_file"
chmod 600 "$log_file"
java -jar "$jar_path" >"$log_file" 2>&1 &
gateway_pid=$!
printf '%s\n' "$gateway_pid" >"$pid_file"
chmod 600 "$pid_file"
gateway_started=1

for _ in {1..60}; do
  kill -0 "$gateway_pid" 2>/dev/null || fail 'Gateway process exited during startup'
  business_status="$(http_status /api/v1/courses 2>/dev/null || true)"
  [[ "$business_status" =~ ^[1-5][0-9]{2}$ ]] && break
  sleep 1
done
[[ "$business_status" =~ ^[1-5][0-9]{2}$ ]] || fail 'Gateway business port did not become reachable'

for endpoint in liveness readiness; do
  ready=0
  for _ in {1..60}; do
    if curl --fail --silent --show-error --max-time 3 \
      "http://127.0.0.1:8081/actuator/health/$endpoint" >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  (( ready == 1 )) || fail "Gateway $endpoint probe did not become healthy"
done

nacos_instances="$(nacos_call GET /v1/ns/instance/list \
  "accessToken=$gateway_nacos_token" \
  "namespaceId=$NACOS_GATEWAY_NAMESPACE" \
  "groupName=$NACOS_GATEWAY_DISCOVERY_GROUP" \
  'serviceName=educloud-gateway' \
  'healthyOnly=true')" || fail 'Unable to query the Gateway Nacos registration'
python3 -c '
import json,sys
root=json.load(sys.stdin)
hosts=root.get("hosts", [])
assert any(item.get("healthy") and item.get("ip") == "127.0.0.1"
           and int(item.get("port", 0)) == 8080 for item in hosts)
' <<<"$nacos_instances" || fail 'Nacos does not contain a healthy Gateway instance'
unset nacos_instances

[[ "$(http_status /api/v1/users/me)" == '401' ]] || fail 'Protected route without Token did not return 401'
[[ "$(http_status /internal/v1/secret)" == '404' ]] || fail 'Internal route did not return 404'
[[ "$(http_status /api/v1/courses/rocky-smoke)" == '503' ]] || \
  fail 'Known route without a healthy downstream did not return 503'

capture_headers /api/v1/courses/rocky-smoke \
  'Origin: https://educloud.local' \
  'X-Request-Id: rocky-smoke-request'
grep -Eiq '^access-control-allow-origin:[[:space:]]*https://educloud.local[[:space:]]*$' "$headers_file" || \
  fail 'Allowed Origin did not receive the CORS response header'
grep -Eiq '^x-request-id:[[:space:]]*rocky-smoke-request[[:space:]]*$' "$headers_file" || \
  fail 'Gateway did not preserve the valid request ID'
grep -Eiq '^x-content-type-options:[[:space:]]*nosniff[[:space:]]*$' "$headers_file" || \
  fail 'Gateway security headers are missing'
[[ "$(http_status /api/v1/courses/rocky-smoke \
  'Origin: https://educloud.local.evil.example')" == '403' ]] || \
  fail 'Rejected Origin did not return 403'

session_key="educloud:{${gateway_environment}:auth}:session:rocky-session"
redis_call HSET "$session_key" \
  subject rocky-user status ACTIVE tokenVersion 1 >/dev/null
redis_call PEXPIRE "$session_key" 900000 >/dev/null
[[ "$(authorized_status /api/v1/users/rocky-user)" == '503' ]] || \
  fail 'Valid JWT and ACTIVE session did not reach the known route boundary'

rate_limited=0
for _ in {1..100}; do
  status="$(http_status /api/v1/courses)"
  if [[ "$status" == '429' ]]; then
    rate_limited=1
    grep -Eiq '^retry-after:[[:space:]]*[1-9][0-9]*[[:space:]]*$' "$headers_file" || \
      fail 'Rate-limit response omitted a positive Retry-After header'
    break
  fi
done
(( rate_limited == 1 )) || fail 'Gateway did not enforce the Redis rate limit'

kill -TERM "$gateway_pid"
for _ in {1..40}; do
  kill -0 "$gateway_pid" 2>/dev/null || break
  sleep 0.5
done
kill -0 "$gateway_pid" 2>/dev/null && fail 'Gateway did not stop within the graceful shutdown window'
wait "$gateway_pid" 2>/dev/null || true
gateway_pid=''
if curl --silent --max-time 1 http://127.0.0.1:8080/ >/dev/null 2>&1; then
  fail 'A process still responds on Gateway port 8080 after shutdown'
fi

nacos_deregistered=0
for _ in {1..40}; do
  nacos_instances="$(nacos_call GET /v1/ns/instance/list \
    "accessToken=$gateway_nacos_token" \
    "namespaceId=$NACOS_GATEWAY_NAMESPACE" \
    "groupName=$NACOS_GATEWAY_DISCOVERY_GROUP" \
    'serviceName=educloud-gateway' \
    'healthyOnly=true')" || fail 'Unable to verify Gateway deregistration'
  if python3 -c '
import json,sys
root=json.load(sys.stdin)
hosts=root.get("hosts", [])
assert not any(item.get("healthy") and item.get("ip") == "127.0.0.1"
               and int(item.get("port", 0)) == 8080 for item in hosts)
' <<<"$nacos_instances"; then
    nacos_deregistered=1
    break
  fi
  sleep 0.5
done
(( nacos_deregistered == 1 )) || fail 'Nacos still contains the stopped Gateway instance'
unset nacos_instances gateway_nacos_token

[[ "$(redis_call PING 2>/dev/null)" == 'PONG' ]] || fail 'Shared Redis is unhealthy after Gateway shutdown'
curl --fail --silent --show-error --max-time 5 \
  "http://${nacos_host}:${NACOS_HTTP_PORT}/nacos/v1/console/health/readiness" >/dev/null || \
  fail 'Shared Nacos is unhealthy after Gateway shutdown'

printf 'All Rocky Linux Gateway smoke checks passed\n'
