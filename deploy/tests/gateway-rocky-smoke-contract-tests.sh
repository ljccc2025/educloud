#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
smoke_script="$repository_root/deploy/tests/gateway-rocky-smoke-tests.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

[[ -f "$smoke_script" ]] || fail 'Gateway Rocky smoke script is missing'

grep -Fq 'gateway_environment="m02-smoke-${smoke_id}"' "$smoke_script" || \
  fail 'Smoke rate-limit/session keys are not isolated by a unique environment'
if grep -Fq 'gateway_environment="${EDUCLOUD_ENVIRONMENT:-local}"' "$smoke_script"; then
  fail 'Smoke script may delete shared local rate-limit state'
fi
grep -Fq 'educloud:{${gateway_environment}:ratelimit}:*' "$smoke_script" || \
  fail 'Smoke cleanup is not scoped to its isolated environment'
grep -Fq 'export SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1' "$smoke_script" || \
  fail 'Smoke Nacos registration is not pinned to a uniquely checkable local address'
grep -Fq "export GATEWAY_ALLOWED_ORIGINS='https://educloud.local'" "$smoke_script" || \
  fail 'Smoke script does not pin an exact HTTPS allowed origin for its non-local environment'
grep -Fq "fail 'Nacos still contains the stopped Gateway instance'" "$smoke_script" || \
  fail 'Smoke script does not verify Nacos deregistration after shutdown'
grep -Fq "NACOS_GATEWAY_PASSWORD is invalid" "$smoke_script" || \
  fail 'Smoke script does not reject curl-config metacharacters in the Nacos password'
grep -Fq "Dedicated Gateway Nacos login returned an invalid token" "$smoke_script" || \
  fail 'Smoke script does not validate the Nacos token before curl-config reuse'

printf 'All Gateway Rocky smoke contract tests passed\n'
