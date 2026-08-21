#!/usr/bin/env bash

# EduCloud 通用数据库迁移执行器
#
# 依据：docs/superpowers/specs/2026-08-18-educloud-data-design.md 第 17 节「迁移规则」与 17.1「迁移历史与并发保护」：
#   - 通过 MySQL GET_LOCK('<service>_migration', timeout) 获取单服务迁移锁；
#   - 按 VNNN__description.sql 顺序读取未应用脚本并计算 SHA-256；
#   - 已成功版本的 checksum 不一致时立即失败，禁止修改已发布脚本；
#   - 成功后写 SUCCESS；DDL 部分执行失败时写 FAILED 并停止，后续运行必须先由受审计修复流程处理；
#   - 应用实例启动时不并发执行 DDL。
#
# 用法：
#   bash deploy/scripts/run-migrations.sh --service user
#
# 环境变量：
#   MYSQL_HOST / MYSQL_PORT                    MySQL 地址（默认 127.0.0.1:3306）
#   EDUCLOUD_<SERVICE>_MIGRATION_PASSWORD      迁移账号密码（如 EDUCLOUD_USER_MIGRATION_PASSWORD）
#   MIGRATION_LOCK_TIMEOUT                     GET_LOCK 超时秒数（默认 60）

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: bash deploy/scripts/run-migrations.sh --service SERVICE
USAGE
}

service=''
while (($# > 0)); do
  case "$1" in
    --service)
      (($# >= 2)) || fail '--service requires a value'
      service="$2"
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

[[ "$service" =~ ^[a-z][a-z0-9-]*$ ]] || fail 'SERVICE must match [a-z][a-z0-9-]*'
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
sql_dir="$repo_root/deploy/sql/$service"
[[ -d "$sql_dir" ]] || fail "SQL directory does not exist: $sql_dir"

service_flat="$(printf '%s' "$service" | tr -d '-')"
database_name="educloud_$service_flat"
migration_user="$service_flat"'_migration'
password_var="EDUCLOUD_$(printf '%s' "$service_flat" | tr '[:lower:]' '[:upper:]')_MIGRATION_PASSWORD"
migration_password="$(printenv "$password_var" 2>/dev/null || true)"
[[ -n "$migration_password" ]] || fail "Required environment variable $password_var is empty"

mysql_host="$(printenv MYSQL_HOST 2>/dev/null || true)"
[[ -z "$mysql_host" ]] && mysql_host='127.0.0.1'
mysql_port="$(printenv MYSQL_PORT 2>/dev/null || true)"
[[ -z "$mysql_port" ]] && mysql_port='3306'
lock_timeout="$(printenv MIGRATION_LOCK_TIMEOUT 2>/dev/null || true)"
[[ -z "$lock_timeout" ]] && lock_timeout='60'
lock_name="educloud_$service_flat"'_migration'

mysql_call() {
  MYSQL_PWD="$migration_password" mysql --protocol=TCP -h "$mysql_host" -P "$mysql_port" \
    -u "$migration_user" --batch --skip-column-names "$database_name" "$@"
}

command -v mysql >/dev/null 2>&1 || fail 'mysql client is required'
command -v sha256sum >/dev/null 2>&1 || fail 'sha256sum is required'

# 1) 引导 schema_migration_history（幂等；V000 中也保留同 DDL 供文档对照）
mysql_call <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migration_history (
  version VARCHAR(32) NOT NULL,
  description VARCHAR(255) NOT NULL,
  script_name VARCHAR(255) NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  installed_by VARCHAR(64) NOT NULL,
  installed_at DATETIME(3) NOT NULL,
  execution_ms BIGINT NOT NULL,
  error_summary VARCHAR(1024) NULL,
  PRIMARY KEY (version),
  UNIQUE KEY uk_schema_migration_script (script_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
SQL

# 2) 获取迁移锁
lock_result="$(mysql_call -N -e "SELECT GET_LOCK('$lock_name', $lock_timeout);")"
[[ "$lock_result" == '1' ]] || fail "Unable to acquire migration lock $lock_name"
release_lock() {
  mysql_call -N -e "SELECT RELEASE_LOCK('$lock_name');" >/dev/null 2>&1 || true
}
trap release_lock EXIT

# 3) 读取已应用版本与 checksum
applied_file="$(mktemp)"
trap 'rm -f "$applied_file"; release_lock' EXIT
mysql_call -e "SELECT version, checksum_sha256 FROM schema_migration_history WHERE status = 'SUCCESS';" >"$applied_file" 2>/dev/null || true

applied_versions="$(awk '{print $1}' "$applied_file" | tr '\n' ' ')"

installed_by="$(whoami 2>/dev/null || printf 'unknown')"

# 4) 按版本顺序执行未应用脚本
shopt -s nullglob
for script in "$sql_dir"/V[0-9]*__*.sql; do
  [[ -f "$script" ]] || continue
  script_name="$(basename "$script")"
  version="$(printf '%s' "$script_name" | cut -d_ -f1)"
  description="$(printf '%s' "$script_name" | sed -E 's/^V[0-9]+__//; s/\.sql$//')"
  [[ "$version" =~ ^V[0-9]+$ ]] || fail "Invalid migration file name: $script_name"

  checksum="$(sha256sum "$script" | awk '{print $1}')"

  if grep -Fq "$version" <<<"$applied_versions"; then
    stored="$(awk -v v="$version" '$1 == v {print $2}' "$applied_file")"
    if [[ "$stored" != "$checksum" ]]; then
      fail "Checksum mismatch for applied migration $version ($script_name); published migrations must never change"
    fi
    continue
  fi

  printf 'Applying %s\n' "$script_name"
  started_ms="$(date +%s%3N)"
  if mysql_call <"$script"; then
    execution_ms="$(( $(date +%s%3N) - started_ms ))"
    mysql_call <<SQL
INSERT INTO schema_migration_history
  (version, description, script_name, checksum_sha256, status, installed_by, installed_at, execution_ms)
VALUES ('$version', '$description', '$script_name', '$checksum', 'SUCCESS', '$installed_by', NOW(3), $execution_ms);
SQL
  else
    execution_ms="$(( $(date +%s%3N) - started_ms ))"
    mysql_call <<SQL || true
INSERT INTO schema_migration_history
  (version, description, script_name, checksum_sha256, status, installed_by, installed_at, execution_ms, error_summary)
VALUES ('$version', '$description', '$script_name', '$checksum', 'FAILED', '$installed_by', NOW(3), $execution_ms, 'migration script failed');
SQL
    fail "Migration $script_name failed; inspect schema_migration_history before retrying"
  fi
done

printf 'All %s migrations are up to date\n' "$database_name"
