#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="$repo_root/deploy/docker-compose/compose.yml"
env_file="$repo_root/deploy/docker-compose/.env.example"
init_script="$repo_root/deploy/docker-compose/mysql/init/001-create-databases.sh"
failed=0

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failed=$((failed + 1))
}

require_file() {
  local path="$1"
  local label="$2"
  if [[ -f "$path" ]]; then
    pass "$label exists"
  else
    fail "$label is missing: $path"
  fi
}

require_file "$compose_file" 'Compose file'
require_file "$env_file" 'environment example'
require_file "$init_script" 'MySQL initialization script'

if ((failed > 0)); then
  exit 1
fi

actual_services="$({
  awk '
    $0 == "services:" { in_services = 1; next }
    in_services && /^[^[:space:]]/ { exit }
    in_services && /^  [a-zA-Z0-9_-]+:$/ {
      service = $0
      sub(/^  /, "", service)
      sub(/:$/, "", service)
      print service
    }
  ' "$compose_file" | sort
})"

expected_services="$({
  printf '%s\n' \
    educloud-user elasticsearch grafana minio mysql nacos prometheus rabbitmq redis zipkin | sort
})"

if [[ "$actual_services" == "$expected_services" ]]; then
  pass 'Compose contains the shared infrastructure plus exactly educloud-user'
else
  fail "unexpected service set; actual: $(tr '\n' ' ' <<<"$actual_services")"
fi

service_block() {
  local service="$1"
  awk -v service="$service" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [a-zA-Z0-9_-]+:$/ { exit }
    in_service { print }
  ' "$compose_file"
}

for service in mysql redis rabbitmq nacos minio elasticsearch educloud-user; do
  if service_block "$service" | grep -q '^    healthcheck:'; then
    pass "$service has a health check"
  else
    fail "$service is missing a health check"
  fi
done

for service in mysql redis rabbitmq nacos minio elasticsearch zipkin prometheus grafana educloud-user; do
  image_reference="$(service_block "$service" | awk '$1 == "image:" { print $2; exit }')"
  image_name="${image_reference##*/}"
  image_tag="${image_name#*:}"
  if [[ -n "$image_reference"
      && "$image_name" == *:*
      && -n "$image_tag"
      && "$image_tag" != 'latest' ]]; then
    pass "$service image uses an explicit non-latest tag"
  else
    fail "$service image must use an explicit non-latest tag"
  fi
done

if grep -Eq 'image:[[:space:]]+[^#[:space:]]*:latest([[:space:]]|$)' "$compose_file"; then
  fail 'Compose uses a latest image tag'
else
  pass 'Compose does not use latest image tags'
fi

if grep -Eq '^  educloud-(course|content|order|payment|live|file|notification|analytics|search|recommendation):' "$compose_file"; then
  fail 'Compose contains an M04+ backend business service'
else
  pass 'Compose contains no M04+ backend business services (educloud-user is allowed)'
fi

actual_volumes="$({
  awk '
    $0 == "volumes:" { in_volumes = 1; next }
    in_volumes && /^[^[:space:]]/ { exit }
    in_volumes && /^  [a-zA-Z0-9_-]+:$/ {
      volume = $0
      sub(/^  /, "", volume)
      sub(/:$/, "", volume)
      print volume
    }
  ' "$compose_file" | sort
})"

expected_volumes="$({
  printf '%s\n' \
    elasticsearch-data grafana-data minio-data mysql-data nacos-data \
    prometheus-data rabbitmq-data redis-data | sort
})"

if [[ "$actual_volumes" == "$expected_volumes" ]]; then
  pass 'persistent services use the expected named volumes'
else
  fail "unexpected volume set; actual: $(tr '\n' ' ' <<<"$actual_volumes")"
fi

if grep -Fq './mysql/init/001-create-databases.sh:/docker-entrypoint-initdb.d/001-create-databases.sh:ro' "$compose_file"; then
  pass 'MySQL initialization script is mounted read-only'
else
  fail 'MySQL initialization script mount is missing'
fi

required_env=(
  MYSQL_ROOT_PASSWORD REDIS_PASSWORD RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS
  MINIO_ROOT_USER MINIO_ROOT_PASSWORD NACOS_AUTH_IDENTITY_KEY
  NACOS_AUTH_IDENTITY_VALUE NACOS_AUTH_TOKEN GF_SECURITY_ADMIN_USER
  GF_SECURITY_ADMIN_PASSWORD
)

for variable in "${required_env[@]}"; do
  if grep -Eq "^${variable}=" "$env_file"; then
    pass "$variable is documented"
  else
    fail "$variable is missing from .env.example"
  fi
done

for service in user course content order payment live file notification analytics search recommendation; do
  upper_service="$(tr '[:lower:]' '[:upper:]' <<<"$service")"
  app_password_variable="EDUCLOUD_${upper_service}_DB_PASSWORD"
  migration_password_variable="EDUCLOUD_${upper_service}_MIGRATION_PASSWORD"
  if ! grep -Eq "^${app_password_variable}=" "$env_file"; then
    fail "missing app password for $service"
  fi
  if ! grep -Eq "^${migration_password_variable}=" "$env_file"; then
    fail "missing migration password for $service"
  fi
  if ! service_block mysql | grep -Fq "${app_password_variable}:"; then
    fail "MySQL container does not receive the app password for $service"
  fi
  if ! service_block mysql | grep -Fq "${migration_password_variable}:"; then
    fail "MySQL container does not receive the migration password for $service"
  fi
  if ! grep -Fq "educloud_${service}:${service}_app:${service}_migration" "$init_script"; then
    fail "missing database and account mapping for $service"
  fi
done

if grep -Eq 'GRANT[[:space:]].*ON[[:space:]]+\*\.\*' "$init_script"; then
  fail 'MySQL initialization grants global privileges'
else
  pass 'MySQL initialization grants no global privileges'
fi

if grep -Eq 'GRANT[[:space:]].*TO[[:space:]].*_app' "$init_script"; then
  fail 'application accounts receive database-wide privileges before migrations'
else
  pass 'application accounts start without database-wide privileges'
fi

init_test_root="$(mktemp -d)"
trap 'rm -rf "$init_test_root"' EXIT
mkdir -p "$init_test_root/bin"
cat >"$init_test_root/bin/mysql" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$MYSQL_STUB_ARGS"
has_execute=0
for argument in "$@"; do
  [[ "$argument" == --execute=* ]] && has_execute=1
done
if ((has_execute == 0)); then
  cat >>"$MYSQL_STUB_SQL"
  printf '\n' >>"$MYSQL_STUB_SQL"
fi
STUB
chmod +x "$init_test_root/bin/mysql"

if (
  while IFS='=' read -r variable_name variable_value; do
    case "$variable_name" in
      MYSQL_ROOT_PASSWORD|EDUCLOUD_*_DB_PASSWORD|EDUCLOUD_*_MIGRATION_PASSWORD)
        export "$variable_name=$variable_value"
        ;;
    esac
  done <"$env_file"
  export MYSQL_STUB_ARGS="$init_test_root/mysql-args.log"
  export MYSQL_STUB_SQL="$init_test_root/mysql-statements.sql"
  PATH="$init_test_root/bin:$PATH" bash "$init_script"
); then
  pass 'MySQL initialization runs with the documented local environment'
else
  fail 'MySQL initialization does not run with the documented local environment'
fi

database_count="$(grep -c '^CREATE DATABASE IF NOT EXISTS' "$init_test_root/mysql-statements.sql" || true)"
migration_grant_count="$(grep -c '^GRANT SELECT, INSERT, UPDATE, DELETE' "$init_test_root/mysql-statements.sql" || true)"
if [[ "$database_count" == '11' && "$migration_grant_count" == '11' ]]; then
  pass 'MySQL initialization creates eleven databases and eleven scoped migration grants'
else
  fail "unexpected initialization counts: databases=$database_count migration_grants=$migration_grant_count"
fi

if grep -Eq "TO '[a-z]+_app'@'%'" "$init_test_root/mysql-statements.sql"; then
  fail 'rendered SQL grants privileges to an application account'
else
  pass 'rendered SQL grants no privileges to application accounts'
fi

if (
  while IFS='=' read -r variable_name variable_value; do
    case "$variable_name" in
      MYSQL_ROOT_PASSWORD|EDUCLOUD_*_DB_PASSWORD|EDUCLOUD_*_MIGRATION_PASSWORD)
        export "$variable_name=$variable_value"
        ;;
    esac
  done <"$env_file"
  export EDUCLOUD_USER_DB_PASSWORD=short
  export MYSQL_STUB_ARGS="$init_test_root/invalid-args.log"
  export MYSQL_STUB_SQL="$init_test_root/invalid-statements.sql"
  PATH="$init_test_root/bin:$PATH" bash "$init_script"
) >"$init_test_root/invalid-output.log" 2>&1; then
  fail 'MySQL initialization accepts a malformed password'
elif grep -Fq 'EDUCLOUD_USER_DB_PASSWORD must be 16-128 characters' "$init_test_root/invalid-output.log"; then
  pass 'MySQL initialization rejects malformed passwords before executing SQL'
else
  fail 'MySQL initialization rejects malformed input without the expected diagnostic'
fi

if ((failed > 0)); then
  printf '%s Compose contract checks failed\n' "$failed" >&2
  exit 1
fi

printf 'All Compose contract checks passed\n'
