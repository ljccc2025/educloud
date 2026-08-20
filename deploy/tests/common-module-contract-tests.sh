#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
parent_pom="$repo_root/educloud-backend/pom.xml"
module_dir="$repo_root/educloud-backend/educloud-common"
module_pom="$module_dir/pom.xml"
main_source="$module_dir/src/main/java"
dependency_output="$module_dir/target/runtime-dependencies.txt"
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

if [[ "$actual_modules" == 'educloud-common' ]]; then
  pass 'parent POM declares only educloud-common'
else
  fail "unexpected parent modules: ${actual_modules:-<none>}"
fi

if grep -Eq '<packaging>[[:space:]]*jar[[:space:]]*</packaging>' "$module_pom"; then
  pass 'Common is packaged as a regular JAR'
else
  fail 'Common packaging is not jar'
fi

if [[ -d "$main_source" ]]; then
  forbidden_source_pattern='main[[:space:]]*\(|@SpringBootApplication|@RestController|@Controller|@Entity|(^|[^[:alnum:]_])(Mapper|Repository)([^[:alnum:]_]|$)'
  if grep -REn "$forbidden_source_pattern" "$main_source"; then
    fail 'Common contains forbidden application, web, domain, or data-access source'
  else
    pass 'Common main sources stay inside the approved library boundary'
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
