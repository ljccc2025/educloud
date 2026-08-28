#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/scripts/provision-gateway-nacos.sh [--env-file PATH]

Provision the exact local Nacos namespace, user, role, and permissions required
by educloud-gateway. Set NACOS_ADMIN_USERNAME and optionally
NACOS_ADMIN_PASSWORD. If the password is omitted it is read without echo.
USAGE
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_root/deploy/docker-compose/.env"
while (($# > 0)); do
  case "$1" in
    --env-file)
      (($# >= 2)) || fail '--env-file requires a path'
      env_file="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -f "$env_file" && -r "$env_file" ]] || fail "Environment file is not readable: $env_file"
for command_name in curl python3 awk comm sort mktemp tr; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found"
done
duplicate_keys="$(awk -F= '/^[A-Z0-9_]+=/ { count[$1]++ } END { for (key in count) if (count[key] != 1) print key }' \
  "$env_file")"
[[ -z "$duplicate_keys" ]] || fail 'Environment file contains duplicate keys'
grep -Fq 'ChangeMe' "$env_file" && fail 'Environment file contains a ChangeMe placeholder'

nacos_port=''
namespace=''
config_group=''
discovery_group=''
gateway_username=''
gateway_password=''
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
  key="${BASH_REMATCH[1]}"
  value="${BASH_REMATCH[2]}"
  case "$key" in
    NACOS_HTTP_PORT) nacos_port="$value" ;;
    NACOS_GATEWAY_NAMESPACE) namespace="$value" ;;
    NACOS_GATEWAY_CONFIG_GROUP) config_group="$value" ;;
    NACOS_GATEWAY_DISCOVERY_GROUP) discovery_group="$value" ;;
    NACOS_GATEWAY_USERNAME) gateway_username="$value" ;;
    NACOS_GATEWAY_PASSWORD) gateway_password="$value" ;;
  esac
done <"$env_file"

[[ "$nacos_port" =~ ^[0-9]{1,5}$ ]] || fail 'NACOS_HTTP_PORT is invalid'
[[ "$namespace" == 'educloud-local' ]] || fail 'NACOS_GATEWAY_NAMESPACE does not match educloud-local'
[[ "$config_group" == 'EDUCLOUD_GATEWAY' ]] || fail 'NACOS_GATEWAY_CONFIG_GROUP does not match EDUCLOUD_GATEWAY'
[[ "$discovery_group" == 'EDUCLOUD_SERVICES' ]] || fail 'NACOS_GATEWAY_DISCOVERY_GROUP does not match EDUCLOUD_SERVICES'
[[ "$gateway_username" == 'educloud_gateway' ]] || fail 'NACOS_GATEWAY_USERNAME does not match educloud_gateway'
[[ "$gateway_password" =~ ^[A-Za-z0-9_.:@%+=-]{16,128}$ ]] || fail 'NACOS_GATEWAY_PASSWORD is invalid'

admin_username="${NACOS_ADMIN_USERNAME:-}"
[[ "$admin_username" =~ ^[A-Za-z0-9_.-]{1,64}$ ]] || fail 'NACOS_ADMIN_USERNAME is required and invalid'
admin_password="${NACOS_ADMIN_PASSWORD:-}"
if [[ -z "$admin_password" ]]; then
  printf 'Nacos administrator password: ' >&2
  IFS= read -r -s admin_password
  printf '\n' >&2
fi
[[ "$admin_password" =~ ^[A-Za-z0-9_.:@%+=-]{5,128}$ ]] || fail 'Nacos administrator password is invalid'

base_url="${NACOS_BASE_URL:-http://127.0.0.1:${nacos_port}/nacos}"
[[ "$base_url" =~ ^https?://[A-Za-z0-9.:-]+/nacos$ ]] || fail 'NACOS_BASE_URL is invalid'
token=''
temporary_directory="$(mktemp -d)"
cleanup() {
  unset admin_password gateway_password token NACOS_ADMIN_PASSWORD
  rm -rf -- "$temporary_directory"
}
trap cleanup EXIT

nacos_call() {
  local method="$1"
  local path="$2"
  shift 2
  {
    printf 'silent\nshow-error\nfail\n'
    printf 'request = "%s"\n' "$method"
    printf 'url = "%s%s"\n' "$base_url" "$path"
    [[ "$method" == 'GET' ]] && printf 'get\n'
    local field
    for field in "$@"; do
      printf 'data-urlencode = "%s"\n' "$field"
    done
  } | curl --config -
}

login_response="$(nacos_call POST /v1/auth/login \
  "username=$admin_username" "password=$admin_password")" || fail 'Nacos administrator login failed'
token="$(python3 -c 'import json,sys; value=json.load(sys.stdin).get("accessToken", ""); print(value)' \
  <<<"$login_response" | tr -d '\r')"
(( ${#token} >= 8 && ${#token} <= 4096 )) && [[ "$token" =~ ^[A-Za-z0-9._-]+$ ]] || \
  fail 'Nacos administrator login returned no valid access token'
unset login_response admin_password NACOS_ADMIN_PASSWORD

namespace_response="$(nacos_call GET /v1/console/namespaces "accessToken=$token")" || \
  fail 'Unable to read Nacos namespaces'
namespace_names="$(python3 -c '
import json,sys
target=sys.argv[1]
root=json.load(sys.stdin)
items=root.get("data", root) if isinstance(root, dict) else root
for item in items if isinstance(items, list) else []:
    if item.get("namespace") == target:
        print(item.get("namespaceShowName", ""))
' "$namespace" <<<"$namespace_response" | tr -d '\r')"
if [[ -z "$namespace_names" ]]; then
  nacos_call POST /v1/console/namespaces \
    "accessToken=$token" \
    "customNamespaceId=$namespace" \
    "namespaceName=$namespace" \
    'namespaceDesc=EduCloud local development' >/dev/null || fail 'Unable to create Nacos namespace'
elif [[ "$namespace_names" != "$namespace" ]]; then
  fail 'Existing Nacos namespace metadata does not match the required state'
fi

users_response="$(nacos_call GET /v1/auth/users \
  "accessToken=$token" 'pageNo=1' 'pageSize=100' 'search=accurate' "username=$gateway_username")" || \
  fail 'Unable to read Nacos users'
users="$(python3 -c '
import json,sys
root=json.load(sys.stdin)
items=root.get("pageItems", []) if isinstance(root, dict) else []
print("\n".join(str(item.get("username", "")) for item in items if item.get("username")))
' <<<"$users_response" | tr -d '\r')"
if [[ -z "$users" ]]; then
  nacos_call POST /v1/auth/users \
    "accessToken=$token" "username=$gateway_username" "password=$gateway_password" >/dev/null || \
    fail 'Unable to create Nacos Gateway user'
elif [[ "$users" != "$gateway_username" ]]; then
  fail 'Nacos Gateway user state is inconsistent'
fi

roles_response="$(nacos_call GET /v1/auth/roles \
  "accessToken=$token" 'pageNo=1' 'pageSize=100' 'search=accurate' \
  "username=$gateway_username" 'role=')" || fail 'Unable to read Nacos roles'
roles="$(python3 -c '
import json,sys
root=json.load(sys.stdin)
items=root.get("pageItems", []) if isinstance(root, dict) else []
print("\n".join(str(item.get("role", "")) for item in items if item.get("role")))
' <<<"$roles_response" | tr -d '\r')"
if [[ -z "$roles" ]]; then
  nacos_call POST /v1/auth/roles \
    "accessToken=$token" "role=$gateway_username" "username=$gateway_username" >/dev/null || \
    fail 'Unable to bind the Nacos Gateway role'
elif [[ "$roles" != "$gateway_username" ]]; then
  fail 'Nacos Gateway user has an unexpected role binding'
fi

expected_permissions="$temporary_directory/expected-permissions"
actual_permissions="$temporary_directory/actual-permissions"
cat >"$expected_permissions" <<EOF
r	${namespace}:${config_group}:config/educloud-gateway.yaml
r	${namespace}:${discovery_group}:naming/educloud-ai
r	${namespace}:${discovery_group}:naming/educloud-analytics
r	${namespace}:${discovery_group}:naming/educloud-content
r	${namespace}:${discovery_group}:naming/educloud-course
r	${namespace}:${discovery_group}:naming/educloud-file
r	${namespace}:${discovery_group}:naming/educloud-live
r	${namespace}:${discovery_group}:naming/educloud-notification
r	${namespace}:${discovery_group}:naming/educloud-order
r	${namespace}:${discovery_group}:naming/educloud-payment
r	${namespace}:${discovery_group}:naming/educloud-recommendation
r	${namespace}:${discovery_group}:naming/educloud-search
r	${namespace}:${discovery_group}:naming/educloud-user
r	${namespace}:${discovery_group}:naming/educloud-gateway
w	${namespace}:${discovery_group}:naming/educloud-gateway
EOF
sort -u "$expected_permissions" -o "$expected_permissions"

read_permissions() {
  local response
  response="$(nacos_call GET /v1/auth/permissions \
    "accessToken=$token" 'pageNo=1' 'pageSize=100' 'search=accurate' "role=$gateway_username")" || \
    fail 'Unable to read Nacos permissions'
  python3 -c '
import json,sys
root=json.load(sys.stdin)
items=root.get("pageItems", []) if isinstance(root, dict) else []
for item in items:
    action=item.get("action")
    resource=item.get("resource")
    if action and resource:
        print(f"{action}\t{resource}")
' <<<"$response" | tr -d '\r' | sort -u >"$actual_permissions"
}

read_permissions
if [[ -n "$(comm -13 "$expected_permissions" "$actual_permissions")" ]]; then
  fail 'Nacos Gateway role has unexpected extra permissions'
fi
while IFS=$'\t' read -r action resource; do
  [[ -n "$action" ]] || continue
  nacos_call POST /v1/auth/permissions \
    "accessToken=$token" "role=$gateway_username" "resource=$resource" "action=$action" >/dev/null || \
    fail 'Unable to add a required Nacos permission'
done < <(comm -23 "$expected_permissions" "$actual_permissions")

read_permissions
cmp -s "$expected_permissions" "$actual_permissions" || fail 'Final Nacos Gateway permission set is inconsistent'

gateway_login="$(nacos_call POST /v1/auth/login \
  "username=$gateway_username" "password=$gateway_password")" || fail 'Nacos Gateway credential verification failed'
gateway_token="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken", ""))' \
  <<<"$gateway_login" | tr -d '\r')"
[[ -n "$gateway_token" ]] || fail 'Nacos Gateway credential verification returned no token'
unset gateway_login gateway_token gateway_password token

printf 'Nacos Gateway namespace, identity, role, and exact permissions are ready.\n'
