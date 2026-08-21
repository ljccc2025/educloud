#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
parent_pom="$repo_root/educloud-backend/pom.xml"
module_dir="$repo_root/educloud-backend/educloud-common"
module_pom="$module_dir/pom.xml"
main_source="$module_dir/src/main/java"
dependency_output="$module_dir/target/runtime-dependencies.txt"
expected_modules=$'educloud-common\neducloud-gateway'
failed=0

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failed=$((failed + 1))
}

if [[ ! -f "$module_pom" ]]; then
  fail "Common module POM is missing: $module_pom"
  exit 1
fi

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

if grep -Eq '<packaging>[[:space:]]*jar[[:space:]]*</packaging>' "$module_pom"; then
  pass 'Common is packaged as a regular JAR'
else
  fail 'Common packaging is not jar'
fi

if [[ -d "$main_source" ]]; then
  forbidden_source_pattern='main[[:space:]]*\(|@SpringBootApplication([^[:alnum:]_]|$)|@RestController([^[:alnum:]_]|$)|@Controller([^[:alnum:]_]|$)|@Entity([^[:alnum:]_]|$)'
  if grep -REn "$forbidden_source_pattern" "$main_source"; then
    fail 'Common contains a forbidden application, controller, or entity declaration'
  else
    pass 'Common has no application, controller, or entity declaration'
  fi

  unexpected_data_types="$(find "$main_source" -type f \
    \( -name '*Mapper.java' -o -name '*Repository.java' \) \
    ! -path "$main_source/com/educloud/common/id/WorkerLeaseRepository.java" \
    ! -path "$main_source/com/educloud/common/id/RedisWorkerLeaseRepository.java" \
    -print)"
  if [[ -n "$unexpected_data_types" ]]; then
    printf '%s\n' "$unexpected_data_types" >&2
    fail 'Common contains an unapproved Mapper or Repository type'
  else
    pass 'Common contains only the approved worker-lease repository boundary'
  fi
else
  pass 'Common has no main source yet'
fi

mvn -q -f "$parent_pom" \
  -pl educloud-common dependency:tree \
  -Dscope=runtime \
  -DoutputFile="$dependency_output"

for forbidden_dependency in \
  spring-boot-starter-web \
  spring-boot-starter-webflux \
  spring-boot-starter-jdbc \
  mysql-connector-j \
  mybatis-plus \
  spring-boot-starter-amqp \
  spring-cloud-starter-alibaba-nacos-discovery; do
  if grep -Fq ":${forbidden_dependency}:" "$dependency_output"; then
    fail "runtime dependency boundary contains $forbidden_dependency"
  else
    pass "runtime dependency boundary excludes $forbidden_dependency"
  fi
done

if ((failed > 0)); then
  printf '%s Common module contract checks failed\n' "$failed" >&2
  exit 1
fi

printf 'All Common module contract checks passed\n'
