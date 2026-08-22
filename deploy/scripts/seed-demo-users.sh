#!/usr/bin/env bash

# 创建 M03 前端联调演示用户（教师/管理员）。
# 用法：bash deploy/scripts/seed-demo-users.sh [--teacher-password PW] [--admin-password PW]
# 依赖：mysql 客户端、htpasswd（httpd-tools）；调用前 source deploy/docker-compose/.env。
# 密码仅写入数据库（BCrypt），不会打印；重复执行幂等。

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

teacher_password='EduCloud@2026'
admin_password='EduCloud@2026'
while (($# > 0)); do
  case "$1" in
    --teacher-password)
      (($# >= 2)) || fail '--teacher-password requires a value'
      teacher_password="$2"
      shift 2
      ;;
    --admin-password)
      (($# >= 2)) || fail '--admin-password requires a value'
      admin_password="$2"
      shift 2
      ;;
    --help|-h)
      printf 'Usage: bash deploy/scripts/seed-demo-users.sh [--teacher-password PW] [--admin-password PW]\n'
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

for command_name in htpasswd mysql; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name command not found (dnf install -y httpd-tools mysql)"
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_root/deploy/docker-compose/.env"
[[ -f "$env_file" && -r "$env_file" ]] || fail "Environment file is not readable: $env_file"

mysql_host='127.0.0.1'
mysql_port='3306'
mysql_root_password=''
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
  case "${BASH_REMATCH[1]}" in
    MYSQL_HOST) mysql_host="${BASH_REMATCH[2]}" ;;
    MYSQL_PORT) mysql_port="${BASH_REMATCH[2]}" ;;
    MYSQL_ROOT_PASSWORD) mysql_root_password="${BASH_REMATCH[2]}" ;;
  esac
done <"$env_file"
[[ -n "$mysql_root_password" ]] || fail 'MYSQL_ROOT_PASSWORD is empty in the environment file'

bcrypt_of() {
  htpasswd -bnBC 10 "" "$1" | tr -d ':\n'
}

teacher_hash="$(bcrypt_of "$teacher_password")"
admin_hash="$(bcrypt_of "$admin_password")"

MYSQL_PWD="$mysql_root_password" mysql --protocol=TCP -h "$mysql_host" -P "$mysql_port" -u root educloud_user <<SQL
INSERT INTO sys_user (id, username, email, phone, password_hash, user_type, status, token_version, created_at, updated_at)
VALUES
  (9000000000000000001, 'demo_teacher', 'demo_teacher@educloud.cn', '13800000001', '$teacher_hash', 'TEACHER', 'ACTIVE', 0, NOW(3), NOW(3)),
  (9000000000000000002, 'demo_admin', 'demo_admin@educloud.cn', '13800000002', '$admin_hash', 'ADMIN', 'ACTIVE', 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  user_type = VALUES(user_type),
  status = VALUES(status),
  updated_at = NOW(3);

INSERT INTO sys_user_role (id, user_id, role_id, assigned_by, assigned_at)
VALUES
  (9000000000000000101, 9000000000000000001, 2, 9000000000000000002, NOW(3)),
  (9000000000000000102, 9000000000000000002, 6, 9000000000000000002, NOW(3))
ON DUPLICATE KEY UPDATE assigned_at = NOW(3);
SQL

printf 'Demo users ready: demo_teacher / demo_admin (passwords as configured, never printed).\n'