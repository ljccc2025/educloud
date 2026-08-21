#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/provision-gateway-nacos.sh"
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
field() { sed -n "s/^data-urlencode = \"$1=\\(.*\\)\"$/\\1/p" <<<"$config" | tail -n 1; }
path="${url#*'/nacos'}"
state="$FAKE_NACOS_STATE"

case "$path:$method" in
  /v1/auth/login:POST)
    username="$(field username)"
    password="$(field password)"
    if [[ "$username" == "$FAKE_ADMIN_USERNAME" && "$password" == "$FAKE_ADMIN_PASSWORD" ]]; then
      printf '{"accessToken":"admin-token","tokenTtl":18000}'
    elif [[ -f "$state/user" && "$username" == 'educloud_gateway' && "$password" == "$(cat "$state/password")" ]]; then
      printf '{"accessToken":"gateway-token","tokenTtl":18000}'
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
      printf '{"totalItems":1,"pageItems":[{"username":"educloud_gateway"}]}'
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
      printf '{"totalItems":1,"pageItems":[{"username":"educloud_gateway","role":"%s"}]}' "$(cat "$state/role")"
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
        items.append({"role":"educloud_gateway", "resource":resource, "action":action})
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

env_file="$fixture_root/gateway.env"
cat >"$env_file" <<'ENV'
NACOS_HTTP_PORT=8848
NACOS_GATEWAY_NAMESPACE=educloud-local
NACOS_GATEWAY_CONFIG_GROUP=EDUCLOUD_GATEWAY
NACOS_GATEWAY_DISCOVERY_GROUP=EDUCLOUD_SERVICES
NACOS_GATEWAY_USERNAME=educloud_gateway
NACOS_GATEWAY_PASSWORD=GatewayPassword0123456789
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
[[ "$(cat "$fixture_root/state/role")" == 'educloud_gateway' ]] || fail 'did not bind the same-name role'
[[ "$(wc -l <"$fixture_root/state/permissions" | tr -d ' ')" == '14' ]] || fail 'did not create exactly 14 permissions'
if grep -Eq '(ROLE_ADMIN|\*:\*|\t\*)' "$fixture_root/state/permissions"; then
  fail 'created an administrative or wildcard permission'
fi
if grep -Fq "$FAKE_ADMIN_PASSWORD" "$fixture_root/first.log" || \
   grep -Fq 'GatewayPassword0123456789' "$fixture_root/first.log" || \
   grep -Fq 'admin-token' "$fixture_root/first.log"; then
  fail 'printed a credential or access token'
fi
if grep -Eq '(AdminPassword|GatewayPassword|accessToken)' "$fixture_root/curl-argv.log"; then
  fail 'placed a credential in curl process arguments'
fi

mutation_hash="$(sha256sum "$fixture_root/state/namespace" "$fixture_root/state/user" \
  "$fixture_root/state/password" "$fixture_root/state/role" "$fixture_root/state/permissions")"
bash "$script_under_test" --env-file "$env_file" >"$fixture_root/second.log" 2>&1
[[ "$mutation_hash" == "$(sha256sum "$fixture_root/state/namespace" "$fixture_root/state/user" \
  "$fixture_root/state/password" "$fixture_root/state/role" "$fixture_root/state/permissions")" ]] || \
  fail 'changed already-provisioned state on the second run'

printf 'w\teducloud-local:EDUCLOUD_GATEWAY:unexpected.yaml\n' >>"$fixture_root/state/permissions"
if bash "$script_under_test" --env-file "$env_file" >/dev/null 2>&1; then
  fail 'accepted an unexpected extra permission'
fi
sed -i '/unexpected.yaml/d' "$fixture_root/state/permissions"

unset NACOS_ADMIN_PASSWORD
printf '%s\n' "$FAKE_ADMIN_PASSWORD" | \
  bash "$script_under_test" --env-file "$env_file" >"$fixture_root/prompt.log" 2>&1 || \
  fail 'did not accept an administrator password from a no-echo stdin prompt'

if grep -Eq 'set[[:space:]]+-[^#\n]*x' "$script_under_test"; then
  fail 'enables shell xtrace'
fi

printf 'All gateway Nacos provisioning checks passed\n'
