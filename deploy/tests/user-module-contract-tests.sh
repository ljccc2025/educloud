#!/usr/bin/env bash

# educloud-user 模块契约测试
# 依据：M03 实施计划任务 4、开发规范第 2/3 节、数据设计第 17 节。

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
parent_pom="$repo_root/educloud-backend/pom.xml"
user_dir="$repo_root/educloud-backend/educloud-user"
user_pom="$user_dir/pom.xml"
sql_dir="$repo_root/deploy/sql/user"
main_dir="$user_dir/src/main/java/com/educloud/user"
readme="$repo_root/educloud-backend/README.md"

failed=0
pass() { printf 'PASS: %s
' "$1"; }
fail() { printf 'FAIL: %s
' "$1" >&2; failed=$((failed + 1)); }

require_file() {
  [[ -f "$1" ]] || fail "missing file: $1"
}

require_file "$parent_pom"
require_file "$user_pom"
require_file "$user_dir/src/main/resources/application.yml"
require_file "$readme"

# 1) 父 POM 模块顺序：common -> gateway -> user
common_order="$(grep -n '<module>educloud-common</module>' "$parent_pom" | head -1 | cut -d: -f1)"
gateway_order="$(grep -n '<module>educloud-gateway</module>' "$parent_pom" | head -1 | cut -d: -f1)"
user_order="$(grep -n '<module>educloud-user</module>' "$parent_pom" | head -1 | cut -d: -f1)"
[[ -n "$common_order" && -n "$gateway_order" && -n "$user_order" &&   "$common_order" -lt "$gateway_order" && "$gateway_order" -lt "$user_order" ]] ||   fail 'parent POM module order must be common -> gateway -> user'
pass 'parent POM declares educloud-common then educloud-gateway then educloud-user'

# 2) 依赖边界：允许基础设施依赖；禁止业务模块与 gateway 依赖
for forbidden in educloud-course educloud-content educloud-order educloud-payment   educloud-live educloud-file educloud-notification educloud-analytics   educloud-search educloud-recommendation educloud-gateway; do
  if grep -Fq "<artifactId>$forbidden</artifactId>" "$user_pom"; then
    fail "user module must not depend on $forbidden"
  fi
done
for required in spring-boot-starter-web spring-boot-starter-security   spring-boot-starter-oauth2-resource-server spring-boot-starter-data-redis   spring-boot-starter-amqp mybatis-plus-spring-boot3-starter   mysql-connector-j springdoc-openapi-starter-webmvc-ui; do
  grep -Fq "<artifactId>$required</artifactId>" "$user_pom" ||     fail "user module must declare $required"
done
pass 'user dependency boundary excludes business modules and gateway'

# 3) 经典分层结构存在
for package in controller dto service mapper entity messaging security config exception support; do
  [[ -d "$main_dir/$package" ]] || fail "missing package: $package"
done
pass 'classic layered package structure exists'

# 4) 数据库迁移存在且版本有序
[[ -d "$sql_dir" ]] || fail 'deploy/sql/user directory missing'
first="$(ls "$sql_dir"/V[0-9]*__*.sql 2>/dev/null | sort | head -1)"
last="$(ls "$sql_dir"/V[0-9]*__*.sql 2>/dev/null | sort | tail -1)"
[[ -n "$first" ]] || fail 'no V migrations under deploy/sql/user'
[[ "$(basename "$first")" == 'V000__technical_tables.sql' ]] ||   fail 'V000 technical tables migration must come first'
grep -Fq 'schema_migration_history' "$sql_dir/V000__technical_tables.sql" ||   fail 'V000 must define schema_migration_history'
pass 'migrations exist and start with V000 technical tables'

# 5) 无私钥/私钥参数提交
# Java 源码中对 PEM 头字符串的引用（JwtKeyProvider 解析、测试构造测试密钥）不是密钥材料，予以排除；
# 非源码文件（配置/迁移/文档）若含 PEM 私钥块则判为违规。
if grep -rIl --exclude='*.java' 'BEGIN PRIVATE KEY' "$user_dir" "$sql_dir" 2>/dev/null | grep -q .; then
  fail 'private key material must not be committed'
fi
if grep -rIn '"d"' "$sql_dir" 2>/dev/null | grep -q 'BEGIN|PRIVATE|RSA PRIVATE'; then
  fail 'private JWK parameters must not appear in migrations'
fi
pass 'no private key material in module or migrations'

# 6) 应用名与集成测试门禁
grep -Fq 'spring:' "$user_dir/src/main/resources/application.yml" ||   fail 'application.yml must declare spring section'
grep -Fq 'name: educloud-user' "$user_dir/src/main/resources/application.yml" ||   fail 'application name must be educloud-user'
grep -Fq '<skipITs>true</skipITs>' "$user_pom" ||   fail 'user module must default skipITs to true'
grep -Fq '<id>integration</id>' "$user_pom" ||   fail 'user module must declare an integration profile'
pass 'application name and integration gate are configured'

# 7) README 不得提前宣称 M03 验收完成（允许如实记录"已实现、等待验收"）
if grep -Eq 'M03 已验收|M03 已完成并验收|M03 交付完成|M03 已实现并验证，等待用户验收.*[^。]*已验收' "$readme"; then
  fail 'README must not claim M03 acceptance before user acceptance'
fi
pass 'README does not prematurely claim M03 acceptance'

# 8) M03 e2e 契约：脚本存在、覆盖关键场景、独立 m03-e2e-* 环境前缀、结束后清理
e2e_script="$repo_root/deploy/tests/user-gateway-e2e-tests.sh"
provision_script="$repo_root/deploy/scripts/provision-user-nacos.sh"
require_file "$e2e_script"
require_file "$provision_script"
for marker in 'register' 'login' '/api/v1/me' 'refresh' 'logout'   'password/change' 'DISABLED' 'REFRESH_ALREADY_ROTATED'; do
  grep -Fq "$marker" "$e2e_script" || fail "e2e script must cover $marker"
done
grep -Eq 'm03-e2e-\$\{e2e_id\}' "$e2e_script" || fail 'e2e script must use the isolated m03-e2e-* environment prefix'
grep -Eq 'DELETE FROM sys_user' "$e2e_script" ||   fail 'e2e script must clean up its test users'
grep -Eq 'redis_call DEL|redis_call --scan' "$e2e_script" ||   fail 'e2e script must clean up its Redis session keys'
grep -Fq 'NACOS_USER_USERNAME' "$provision_script" ||   fail 'provision-user-nacos.sh must provision the educloud_user identity'
pass 'M03 e2e contract is covered'

if ((failed > 0)); then
  printf '%s user module contract checks failed
' "$failed" >&2
  exit 1
fi
printf 'All User module contract checks passed
'
