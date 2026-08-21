#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
parent_pom="$repo_root/educloud-backend/pom.xml"
module_dir="$repo_root/educloud-backend/educloud-gateway"
module_pom="$module_dir/pom.xml"
main_dir="$module_dir/src/main"
test_source="$module_dir/src/test/java"
image_helper="$test_source/com/educloud/gateway/integration/TestContainerImages.java"
application_yml="$main_dir/resources/application.yml"
dependency_output="$module_dir/target/runtime-dependencies.txt"
expected_modules=$'educloud-common\neducloud-gateway\neducloud-user'
failed=0

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failed=$((failed + 1))
}

actual_modules="$({
  awk '
    /<modules>/ { in_modules = 1; next }
    /<\/modules>/ { in_modules = 0; exit }
    in_modules && /<module>/ {
      module = $0
      sub(/^.*<module>[[:space:]]*/, "", module)
      sub(/[[:space:]]*<\/module>.*$/, "", module)
      print module
    }
  ' "$parent_pom"
})"

if [[ "$actual_modules" == "$expected_modules" ]]; then
  pass 'parent POM declares educloud-common then educloud-gateway'
else
  fail "unexpected parent modules: ${actual_modules:-<none>}"
fi

if [[ ! -f "$module_pom" ]]; then
  fail "Gateway module POM is missing: $module_pom"
  printf '%s Gateway module contract checks failed\n' "$failed" >&2
  exit 1
fi

if grep -Eq '<packaging>[[:space:]]*jar[[:space:]]*</packaging>' "$module_pom"; then
  pass 'Gateway is packaged as a regular JAR'
else
  fail 'Gateway packaging is not jar'
fi

if grep -A12 -F '<artifactId>spring-boot-maven-plugin</artifactId>' "$module_pom" \
    | grep -Fq '<goal>repackage</goal>'; then
  pass 'Spring Boot repackage execution is configured'
else
  fail 'Spring Boot repackage execution is missing'
fi

if grep -A20 -F '<artifactId>maven-failsafe-plugin</artifactId>' "$module_pom" \
    | grep -Fq '<classesDirectory>${project.build.outputDirectory}</classesDirectory>'; then
  pass 'Gateway Failsafe uses the unpacked main classes directory'
else
  fail 'Gateway Failsafe does not use the unpacked main classes directory'
fi

if [[ -d "$main_dir" ]]; then
  for forbidden_source in '@Entity' '@Mapper' 'jakarta.persistence' 'javax.persistence'; do
    if grep -REn --exclude-dir=target -- "$forbidden_source" "$main_dir"; then
      fail "Gateway main source contains forbidden marker $forbidden_source"
    else
      pass "Gateway main source excludes $forbidden_source"
    fi
  done
fi

for forbidden_directory in db migration migrations; do
  if find "$main_dir" -type d -iname "$forbidden_directory" -print -quit 2>/dev/null \
      | grep -q .; then
    fail "Gateway contains forbidden $forbidden_directory directory"
  else
    pass "Gateway contains no $forbidden_directory directory"
  fi
done

sensitive_file="$(find "$module_dir" -path "$module_dir/target" -prune -o -type f \
  \( -iname '*.pem' -o -iname '*.key' \) -print -quit)"
if [[ -n "$sensitive_file" ]]; then
  fail "Gateway contains forbidden key material: $sensitive_file"
else
  pass 'Gateway contains no PEM or key files'
fi

if grep -REn --exclude-dir=target -- 'BEGIN ([A-Z]+ )?PRIVATE KEY|"(d|p|q|dp|dq|qi|oth)"[[:space:]]*:' "$module_dir"; then
  fail 'Gateway contains private key material or private JWK parameters'
else
  pass 'Gateway contains no private key material or private JWK parameters'
fi

if [[ -f "$application_yml" ]] \
    && grep -Eq '^[[:space:]]+locator:[[:space:]]*$' "$application_yml" \
    && grep -Eq '^[[:space:]]+enabled:[[:space:]]*false[[:space:]]*$' "$application_yml"; then
  pass 'Gateway discovery locator is explicitly disabled'
else
  fail 'Gateway discovery locator is not explicitly disabled'
fi

if [[ -f "$image_helper" ]] \
    && grep -Fq 'EDUCLOUD_TEST_REDIS_IMAGE' "$image_helper" \
    && grep -Fq 'EDUCLOUD_TEST_NACOS_IMAGE' "$image_helper" \
    && grep -Fq 'redis:7.2.5-alpine' "$image_helper" \
    && grep -Fq 'nacos/nacos-server:v2.3.2' "$image_helper"; then
  pass 'Gateway Testcontainers images have pinned overridable sources'
else
  fail 'Gateway Testcontainers image override contract is missing'
fi

if grep -RFn --include='*IT.java' \
    -e 'DockerImageName.parse("redis:7.2.5-alpine")' \
    -e 'DockerImageName.parse("nacos/nacos-server:v2.3.2")' \
    "$test_source"; then
  fail 'Gateway integration tests contain scattered image references'
else
  pass 'Gateway integration tests use the shared test image resolver'
fi

mvn -q -f "$parent_pom" -pl educloud-gateway dependency:tree \
  -Dscope=runtime -DoutputFile="$dependency_output"

for forbidden_dependency in \
  spring-boot-starter-web \
  spring-boot-starter-jdbc \
  mysql-connector-j \
  mybatis-plus \
  spring-boot-starter-amqp; do
  if grep -Fq ":${forbidden_dependency}:" "$dependency_output"; then
    fail "runtime dependency boundary contains $forbidden_dependency"
  else
    pass "runtime dependency boundary excludes $forbidden_dependency"
  fi
done

executable_jar="$(find "$module_dir/target" -maxdepth 1 -type f \
  -name 'educloud-gateway-*.jar' ! -name '*.original' -print -quit 2>/dev/null || true)"
if [[ -z "$executable_jar" ]]; then
  fail 'executable Gateway JAR is missing; run Maven package first'
else
  manifest_dir="$(mktemp -d)"
  manifest="$(cd "$manifest_dir" \
    && jar -xf "$executable_jar" META-INF/MANIFEST.MF \
    && cat META-INF/MANIFEST.MF)"
  rm -rf -- "$manifest_dir"
  if grep -Fq 'Start-Class: com.educloud.gateway.GatewayApplication' <<<"$manifest"; then
    pass 'executable JAR declares the Gateway start class'
  else
    fail 'executable JAR manifest has no Gateway start class'
  fi
fi

if ((failed > 0)); then
  printf '%s Gateway module contract checks failed\n' "$failed" >&2
  exit 1
fi

printf 'All Gateway module contract checks passed\n'
