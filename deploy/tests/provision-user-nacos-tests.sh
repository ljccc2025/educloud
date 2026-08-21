#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/provision-user-nacos.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
mkdir -p "$fixture_root/bin" "$fixture_root/state"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

cat >"$fixture_root/bin/curl" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == '--config -' ]] || exit 90
printf '%s\n' "$*" >>"$FAKE_CURL_ARGV_LOG"
config="$(cat)"
method="$(sed -n 's/^request = "\(.*\)"$/\1/p' <<<"$config")"
url="$(sed -n 's/^url = "\(.*\)"$/\1/p' <<<"$config")"
field() { sed -n "s/^data-urlencode = \"$1=\(.*\)\"$/\\1/p" <<<"$config" | tail -n 1; }
path="${url#*'/nacos'}"
state="$FAKE_NACOS_STATE"

case "$path:$method" in
  /v1/auth/login:POST)
    username="$(field username)"
    password="$(field password)"
    if [[ "$username" == "$FAKE_ADMIN_USERNAME" && "$password" == "$FAKE_ADMIN_PASSWORD" ]]; then
      printf '{"accessToken":"admin-token","tokenTtl":18000}'
    elif [[ -f "$state/user" && "$username" == 'educloud_user' && "$password" == "$(cat "$state/password")" ]]; then
      printf '{"accessToken":"user-token","tokenTtl":18000}'
    else
      exit 22
    fi
    ;;
  /v1/console/namespaces:GET)
    if [[ -f "$state/namespace" ]]; then
      printf '{"code":0,"data":[{"namespace":"educloud-local","namespaceShowName":"%s"}]}' "$(cat "$state/namespace")"
    else
      printf '{"code":0,"data":[]}'
    fi
    ;;
  /v1/console/namespaces:POST)
    printf '%s' "$(field namespaceName)" >"$state/namespace"
    printf 'true'
    ;;
  /v1/auth/users:GET)
    if [[ -f "$state/user" ]]; then
      printf '{"totalItems":1,"pageItems":[{"username":"educloud_user"}]}'
    else
      printf '{"totalItems":0,"pageItems":[]}'
    fi
    ;;
  /v1/auth/users:POST)
    touch "$state/user"
    printf '%s' "$(field password)" >"$state/password"
    printf '{"code":200,"data":"create user ok!"}'
    ;;
  /v1/auth/roles:GET)
    if [[ -f "$state/role" ]]; then
      printf '{"totalItems":1,"pageItems":[{"username":"educloud_user","role":"%s"}]}' "$(cat "$state/role")"
    else
      printf '{"totalItems":0,"pageItems":[]}'
    fi
    ;;
  /v1/auth/roles:POST)
    printf '%s' "$(field role)" >"$state/role"
    printf '{"code":200,"data":"add role ok!"}'
    ;;
  /v1/auth/permissions:GET)
    python3 - "$state/permissions" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
items = []
if path.exists():
    for line in path.read_text().splitlines():
        action, resource = line.split("\t", 1)
        items.append({"role":"educloud_user", "resource":resource, "action":action})
print(json.dumps({"totalItems":len(items), "pageItems":items}, separators=(",", ":")))
PY
    ;;
  /v1/auth/permissions:POST)
    printf '%s\t%s\n' "$(field action)" "$(field resource)" >>"$state/permissions"
    sort -u "$state/permissions" -o "$state/permissions"
    printf '{"code":200,"data":"add permission ok!"}'
    ;;
  *) exit 91 ;;
esac
FAKE
chmod +x "$fixture_root/bin/curl"

env_file="$fixture_root/user.env"
cat >"$env_file" <<'ENV'
NACOS_HTTP_PORT=8848
NACOS_USER_NAMESPACE=educloud-local
NACOS_GATEWAY_DISCOVERY_GROUP=EDUCLOUD_SERVICES
NACOS_USER_USERNAME=educloud_user
NACOS_USER_PASSWORD=UserPassword0123456789
ENV
chmod 600 "$env_file"

export FAKE_CURL_ARGV_LOG="$fixture_root/curl-argv.log"
export FAKE_NACOS_STATE="$fixture_root/state"
export FAKE_ADMIN_USERNAME=local_admin
export FAKE_ADMIN_PASSWORD=AdminPassword0123456789
export NACOS_ADMIN_USERNAME="$FAKE_ADMIN_USERNAME"
export NACOS_ADMIN_PASSWORD="$FAKE_ADMIN_PASSWORD"
export PATH="$fixture_root/bin:$PATH"

bash "$script_under_test" --env-file "$env_file" >"$fixture_root/first.log" 2>&1
[[ "$(cat "$fixture_root/state/namespace")" == 'educloud-local' ]] || fail 'did not create the namespace'
[[ "$(cat "$fixture_root/state/role")" == 'educloud_user' ]] || fail 'did not bind the same-name role'
[[ "$(wc -l <"$fixture_root/state/permissions" | tr -d ' ')" == '2' ]] || fail 'did not create exactly 2 permissions'
if grep -Eq '(ROLE_ADMIN|\*:\*|\t\*)' "$fixture_root/state/permissions"; then
  fail 'created an administrative or wildcard permission'
fi
if ! grep -Fq 'r	educloud-local:EDUCLOUD_SERVICES:naming/educloud-user' "$fixture_root/state/permissions"; then
  fail 'missing read permission for naming/educloud-user'
fi
if ! grep -Fq 'w	educloud-local:EDUCLOUD_SERVICES:naming/educloud-user' "$fixture_root/state/permissions"; then
  fail 'missing write permission for naming/educloud-user'
fi

before="$(cat "$fixture_root/state/permissions")"
bash "$script_under_test" --env-file "$env_file" >"$fixture_root/second.log" 2>&1
after="$(cat "$fixture_root/state/permissions")"
[[ "$before" == "$after" ]] || fail 'second run was not idempotent'
[[ "$(wc -l <"$fixture_root/state/permissions" | tr -d ' ')" == '2' ]] || fail 'second run duplicated permissions'

if [[ ! -f "$fixture_root/state/namespace" || ! -f "$fixture_root/state/role" ]]; then
  fail 'state was reset between runs'
fi
if grep -Eq 'NACOS_ADMIN_PASSWORD|UserPassword0123456789' "$fixture_root/curl-argv.log"; then
  fail 'a password leaked into a curl command line'
fi

printf 'w\teducloud-local:EDUCLOUD_SERVICES:naming/educloud-gateway\n' >>"$fixture_root/state/permissions"
if bash "$script_under_test" --env-file "$env_file" >/dev/null 2>&1; then
  fail 'accepted an unexpected extra permission (Gateway identity leak)'
fi
sed -i '/educloud-gateway/d' "$fixture_root/state/permissions"

if grep -Eq 'gateway|Gateway' "$fixture_root/first.log" "$fixture_root/second.log"; then
  fail 'User provisioning output mentions the Gateway identity'
fi

printf 'All User Nacos provisioning tests passed\n'