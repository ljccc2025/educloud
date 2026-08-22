#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/scripts/generate-local-env.sh [--force] [--output PATH]

Generate a local Docker Compose environment file with random credentials.
The default output is deploy/docker-compose/.env. Existing files are only
replaced when --force is provided. Generated secrets are never printed.
USAGE
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
template_file="$repo_root/deploy/docker-compose/.env.example"
output_file="$repo_root/deploy/docker-compose/.env"
force=0

while (($# > 0)); do
  case "$1" in
    --force)
      force=1
      shift
      ;;
    --output)
      (($# >= 2)) || fail "--output requires a path"
      output_file="$2"
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

[[ -r "$template_file" ]] || fail "Environment template is not readable: $template_file"
[[ -r /dev/urandom ]] || fail "/dev/urandom is not readable"
command -v od >/dev/null 2>&1 || fail "od command not found"
command -v base64 >/dev/null 2>&1 || fail "base64 command not found"

output_directory="$(dirname "$output_file")"
[[ -d "$output_directory" ]] || fail "Output directory does not exist: $output_directory"
if [[ -e "$output_file" && "$force" != "1" ]]; then
  fail "Environment file already exists: $output_file (use --force to replace it)"
fi

random_hex() {
  od -An -N24 -tx1 /dev/urandom | tr -d ' \n'
}

random_nacos_token() {
  local token
  token="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
  printf '%s\n' "$token"
}

command -v head >/dev/null 2>&1 || fail "head command not found"

nacos_user_password=''

umask 077
temporary_file="$(mktemp "${output_file}.tmp.XXXXXX")"
trap 'rm -f "$temporary_file"' EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^([A-Z0-9_]+)= ]]; then
    variable_name="${BASH_REMATCH[1]}"
    case "$variable_name" in
      MYSQL_ROOT_PASSWORD|EDUCLOUD_*_DB_PASSWORD|EDUCLOUD_*_MIGRATION_PASSWORD|REDIS_PASSWORD|RABBITMQ_DEFAULT_PASS|NACOS_AUTH_IDENTITY_VALUE|NACOS_GATEWAY_PASSWORD|MINIO_ROOT_PASSWORD|GF_SECURITY_ADMIN_PASSWORD)
        printf '%s=%s\n' "$variable_name" "$(random_hex)" >>"$temporary_file"
        ;;
      NACOS_USER_PASSWORD)
        nacos_user_password="$(random_hex)"
        printf '%s=%s\n' "$variable_name" "$nacos_user_password" >>"$temporary_file"
        ;;
      EDUCLOUD_USER_NACOS_PASSWORD)
        # 与 NACOS_USER_PASSWORD 同值：provision-user-nacos.sh 用它创建身份，User 服务用它登录 Nacos。
        if [[ -z "$nacos_user_password" ]]; then
          nacos_user_password="$(random_hex)"
        fi
        printf '%s=%s\n' "$variable_name" "$nacos_user_password" >>"$temporary_file"
        ;;
      NACOS_FILE_PASSWORD)
        # M04: File 服务 Nacos 密码（provision-file-nacos.sh 用它创建身份，File 服务用它登录 Nacos）。
        printf '%s=%s\n' "$variable_name" "$(random_hex)" >>"$temporary_file"
        ;;
      EDUCLOUD_FILE_INTERNAL_BOOTSTRAP_KEY)
        # M04: File 内部 bootstrap 密钥；生成后从 .env 读取作为 bootstrap-service-clients.sh 的 BOOTSTRAP_KEY。
        printf '%s=%s\n' "$variable_name" "$(random_hex)" >>"$temporary_file"
        ;;
      EDUCLOUD_USER_FILE_CLIENT_SECRET)
        # M04: User 服务调用 File 内部接口的客户端 secret；须与 bootstrap-service-clients.sh 注册到 File 的 user-service 客户端 secret 一致。
        printf '%s=%s\n' "$variable_name" "$(random_hex)" >>"$temporary_file"
        ;;
      NACOS_AUTH_TOKEN)
        printf '%s=%s\n' "$variable_name" "$(random_nacos_token)" >>"$temporary_file"
        ;;
      *)
        printf '%s\n' "$line" >>"$temporary_file"
        ;;
    esac
  else
    printf '%s\n' "$line" >>"$temporary_file"
  fi
done <"$template_file"

chmod 600 "$temporary_file"
mv -f -- "$temporary_file" "$output_file"
trap - EXIT

printf 'Generated local environment file: %s\n' "$output_file"
printf 'File permissions restricted to the current user; generated secrets were not printed.\n'
