#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/scripts/prepare-gateway-local-env.sh [--env-file PATH]

Add missing Gateway-specific Nacos values to an existing generated local .env.
Existing keys and values are preserved. The generated password is not printed.
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

[[ -f "$env_file" && -r "$env_file" ]] || fail "Environment file is not a readable regular file: $env_file"
command -v awk >/dev/null 2>&1 || fail 'awk command not found'
command -v od >/dev/null 2>&1 || fail 'od command not found'
command -v stat >/dev/null 2>&1 || fail 'stat command not found'

file_mode="$(stat -c '%a' "$env_file")"
[[ "$file_mode" =~ ^[0-7]{3,4}$ ]] || fail 'Unable to determine environment file permissions'
(( (8#$file_mode & 0444) != 0 )) || fail "Environment file has no read permission bits: $env_file"

duplicate_keys="$(awk -F= '
  /^[A-Z0-9_]+=/ { counts[$1]++ }
  END { for (key in counts) if (counts[key] != 1) print key }
' "$env_file" | sort)"
[[ -z "$duplicate_keys" ]] || fail 'Environment file contains duplicate keys'
if grep -Fq 'ChangeMe' "$env_file"; then
  fail 'Environment file still contains a ChangeMe placeholder; generate it before adding Gateway values'
fi

declare -A additions=(
  [NACOS_GATEWAY_NAMESPACE]='educloud-local'
  [NACOS_GATEWAY_CONFIG_GROUP]='EDUCLOUD_GATEWAY'
  [NACOS_GATEWAY_DISCOVERY_GROUP]='EDUCLOUD_SERVICES'
  [NACOS_GATEWAY_USERNAME]='educloud_gateway'
)

missing=()
for key in \
  NACOS_GATEWAY_NAMESPACE \
  NACOS_GATEWAY_CONFIG_GROUP \
  NACOS_GATEWAY_DISCOVERY_GROUP \
  NACOS_GATEWAY_USERNAME \
  NACOS_GATEWAY_PASSWORD
do
  if ! grep -Eq "^${key}=" "$env_file"; then
    missing+=("$key")
  fi
done

if ((${#missing[@]} == 0)); then
  chmod 600 "$env_file"
  printf 'Gateway local environment is already prepared: %s\n' "$env_file"
  exit 0
fi

umask 077
temporary_file="$(mktemp "${env_file}.tmp.XXXXXX")"
trap 'rm -f -- "$temporary_file"' EXIT
cp -- "$env_file" "$temporary_file"
printf '\n# EduCloud Gateway local Nacos client identity\n' >>"$temporary_file"
for key in "${missing[@]}"; do
  if [[ "$key" == 'NACOS_GATEWAY_PASSWORD' ]]; then
    value="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  else
    value="${additions[$key]}"
  fi
  printf '%s=%s\n' "$key" "$value" >>"$temporary_file"
done

chmod 600 "$temporary_file"
mv -f -- "$temporary_file" "$env_file"
trap - EXIT
printf 'Prepared Gateway local environment: %s\n' "$env_file"
printf 'Existing values were preserved; generated secrets were not printed.\n'
