#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/check-prerequisites.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

passed=0
failed=0

make_executable() {
  local path="$1"
  shift
  printf '%s\n' '#!/usr/bin/env bash' "$@" >"$path"
  chmod +x "$path"
}

create_fixture() {
  local name="$1"
  local os_version="$2"
  local java_version="$3"
  local maven_version="$4"
  local docker_present="$5"
  local compose_version="$6"
  local fixture="$fixture_root/$name"

  mkdir -p "$fixture/bin"
  printf 'ID=rocky\nVERSION_ID="%s"\n' "$os_version" >"$fixture/os-release"

  make_executable "$fixture/bin/java" \
    "printf '%s\\n' 'openjdk version \"$java_version\" 2026-01-01' >&2"
  make_executable "$fixture/bin/mvn" \
    "printf '%s\\n' 'Apache Maven $maven_version (fixture)'"
  make_executable "$fixture/bin/git" \
    "printf '%s\\n' 'git version 2.43.0'"

  if [[ "$docker_present" == "yes" ]]; then
    make_executable "$fixture/bin/docker" \
      'if [[ "${1:-}" == "--version" ]]; then' \
      "  printf '%s\\n' 'Docker version 29.0.0, build fixture'" \
      'elif [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then' \
      "  printf '%s\\n' 'Docker Compose version v$compose_version'" \
      'elif [[ "${1:-}" == "info" ]]; then' \
      '  exit 0' \
      'else' \
      '  exit 1' \
      'fi'
  fi

  printf '%s\n' "$fixture"
}

run_case() {
  local name="$1"
  local fixture="$2"
  local expected_status="$3"
  local expected_text="$4"
  local output
  local status

  set +e
  output="$({
    EDUCLOUD_OS_RELEASE_FILE="$fixture/os-release" \
    EDUCLOUD_JAVA_BIN="$fixture/bin/java" \
    EDUCLOUD_MAVEN_BIN="$fixture/bin/mvn" \
    EDUCLOUD_GIT_BIN="$fixture/bin/git" \
    EDUCLOUD_DOCKER_BIN="$fixture/bin/docker" \
    EDUCLOUD_SKIP_DOCKER_DAEMON=1 \
      bash "$script_under_test"
  } 2>&1)"
  status=$?
  set -e

  if [[ "$status" -eq "$expected_status" && "$output" == *"$expected_text"* ]]; then
    printf 'PASS: %s\n' "$name"
    passed=$((passed + 1))
  else
    printf 'FAIL: %s\nexpected status: %s\nactual status: %s\nexpected text: %s\noutput:\n%s\n' \
      "$name" "$expected_status" "$status" "$expected_text" "$output"
    failed=$((failed + 1))
  fi
}

success_fixture="$(create_fixture success 8.9 17.0.12 3.9.16 yes 5.4.0)"
java_21_fixture="$(create_fixture java-21 8.9 21.0.8 3.9.16 yes 5.4.0)"
unsupported_java_fixture="$(create_fixture java-18 8.9 18.0.2 3.9.16 yes 5.4.0)"
missing_docker_fixture="$(create_fixture missing-docker 8.9 17.0.12 3.9.16 no 5.4.0)"
wrong_os_fixture="$(create_fixture rocky-9 9.0 17.0.12 3.9.16 yes 5.4.0)"
old_compose_fixture="$(create_fixture compose-1 8.9 17.0.12 3.9.16 yes 1.29.2)"
space_path_fixture="$(create_fixture 'space path' 8.9 17.0.12 3.9.16 yes 5.4.0)"

run_case 'accepts Rocky 8.9 with the required toolchain' "$success_fixture" 0 'Prerequisite check passed'
run_case 'accepts Java 21 as a supported build JDK' "$java_21_fixture" 0 'Prerequisite check passed'
run_case 'rejects Java versions other than 17 or 21' "$unsupported_java_fixture" 1 'Java 17 or 21 is required'
run_case 'rejects a missing Docker command' "$missing_docker_fixture" 1 'docker command not found'
run_case 'rejects Rocky Linux versions other than 8.9' "$wrong_os_fixture" 1 'Rocky Linux 8.9 is required'
run_case 'rejects the legacy Compose v1 command line' "$old_compose_fixture" 1 'Docker Compose plugin version 2 or newer is required'
run_case 'supports tool paths containing spaces' "$space_path_fixture" 0 'Prerequisite check passed'

printf '%s passed, %s failed\n' "$passed" "$failed"
[[ "$failed" -eq 0 ]]
