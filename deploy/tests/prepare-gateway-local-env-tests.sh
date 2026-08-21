#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/prepare-gateway-local-env.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

base_env="$fixture_root/base.env"
cat >"$base_env" <<'ENV'
MYSQL_PORT=3306
MYSQL_ROOT_PASSWORD=0123456789abcdef0123456789abcdef
REDIS_PASSWORD=abcdef0123456789abcdef0123456789
NACOS_HTTP_PORT=8848
NACOS_AUTH_TOKEN=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=
ENV
chmod 600 "$base_env"
original="$(cat "$base_env")"

log_file="$fixture_root/prepare.log"
bash "$script_under_test" --env-file "$base_env" >"$log_file" 2>&1

[[ "$(head -n 5 "$base_env")" == "$original" ]] || fail 'changed an existing variable or value'
for expected in \
  'NACOS_GATEWAY_NAMESPACE=educloud-local' \
  'NACOS_GATEWAY_CONFIG_GROUP=EDUCLOUD_GATEWAY' \
  'NACOS_GATEWAY_DISCOVERY_GROUP=EDUCLOUD_SERVICES' \
  'NACOS_GATEWAY_USERNAME=educloud_gateway'
do
  grep -Fxq "$expected" "$base_env" || fail "missing fixed gateway value: $expected"
done

gateway_password="$(sed -n 's/^NACOS_GATEWAY_PASSWORD=//p' "$base_env")"
[[ "$gateway_password" =~ ^[A-Za-z0-9_.:@%+=-]{16,128}$ ]] || fail 'generated an invalid gateway password'
[[ "$gateway_password" != *ChangeMe* ]] || fail 'retained a placeholder gateway password'
if grep -Fq "$gateway_password" "$log_file"; then
  fail 'printed the generated gateway password'
fi

first_hash="$(sha256sum "$base_env" | cut -d' ' -f1)"
bash "$script_under_test" --env-file "$base_env" >"$fixture_root/repeat.log" 2>&1
second_hash="$(sha256sum "$base_env" | cut -d' ' -f1)"
[[ "$first_hash" == "$second_hash" ]] || fail 'is not idempotent'
if [[ "$(uname -s)" == 'Linux' ]]; then
  [[ "$(stat -c '%a' "$base_env")" == '600' ]] || fail 'did not enforce mode 0600'
else
  printf 'SKIP: exact POSIX file-mode checks require Linux\n'
fi

duplicate_env="$fixture_root/duplicate.env"
printf 'REDIS_PASSWORD=0123456789abcdef\nREDIS_PASSWORD=fedcba9876543210\n' >"$duplicate_env"
if bash "$script_under_test" --env-file "$duplicate_env" >/dev/null 2>&1; then
  fail 'accepted duplicate environment keys'
fi

placeholder_env="$fixture_root/placeholder.env"
printf 'REDIS_PASSWORD=LocalRedis_ChangeMe_2026\n' >"$placeholder_env"
if bash "$script_under_test" --env-file "$placeholder_env" >/dev/null 2>&1; then
  fail 'accepted a ChangeMe placeholder'
fi

if [[ "$(uname -s)" == 'Linux' ]]; then
  unreadable_env="$fixture_root/unreadable.env"
  printf 'REDIS_PASSWORD=0123456789abcdef\n' >"$unreadable_env"
  chmod 000 "$unreadable_env"
  if bash "$script_under_test" --env-file "$unreadable_env" >/dev/null 2>&1; then
    fail 'accepted an environment file with no read permission bits'
  fi
fi

if bash "$script_under_test" --env-file "$fixture_root" >/dev/null 2>&1; then
  fail 'accepted a non-regular environment input'
fi

printf 'All gateway local environment preparation checks passed\n'
