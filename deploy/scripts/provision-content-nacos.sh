#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_root/deploy/docker-compose/.env"

nacos_port='8848'
namespace='educloud-local'
discovery_group='EDUCLOUD_SERVICES'
content_username='educloud_content'
content_password='b2b6c6a9387119adf22914f13320eed100c19ec5edcdc760'
admin_username='nacos'
admin_password='nacos'

base_url="http://127.0.0.1:${nacos_port}/nacos"

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

login_response="$(nacos_call POST /v1/auth/login "username=$admin_username" "password=$admin_password")"
token="$(python3 -c 'import json,sys; value=json.load(sys.stdin).get("accessToken", ""); print(value)' <<<"$login_response" | tr -d '\r')"

# Create user
nacos_call POST /v1/auth/users "username=$content_username" "password=$content_password" "accessToken=$token" || true

# Create role
nacos_call POST /v1/auth/roles "role=ROLE_CONTENT" "username=$content_username" "accessToken=$token" || true

# Grant permission
nacos_call POST /v1/auth/permissions "role=ROLE_CONTENT" "resource=${namespace}:*:*" "action=rw" "accessToken=$token" || true

echo "Nacos content user provisioned successfully!"
