#!/usr/bin/env bash

set -euo pipefail

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

resolve_command() {
  local configured="$1"
  local label="$2"

  if [[ "$configured" == */* ]]; then
    [[ -x "$configured" ]] || fail "$label command not found: $configured"
    printf '%s\n' "$configured"
    return
  fi

  command -v "$configured" 2>/dev/null || fail "$label command not found: $configured"
}

read_os_value() {
  local key="$1"
  local file="$2"

  awk -F= -v expected_key="$key" '
    $1 == expected_key {
      value = substr($0, index($0, "=") + 1)
      gsub(/^"|"$/, "", value)
      print value
      exit
    }
  ' "$file"
}

os_release_file="${EDUCLOUD_OS_RELEASE_FILE:-/etc/os-release}"
[[ -r "$os_release_file" ]] || fail "OS release file is not readable: $os_release_file"

os_id="$(read_os_value ID "$os_release_file")"
os_version="$(read_os_value VERSION_ID "$os_release_file")"
[[ "$os_id" == "rocky" && "$os_version" == "8.9" ]] || \
  fail "Rocky Linux 8.9 is required; detected ${os_id:-unknown} ${os_version:-unknown}"

java_bin="$(resolve_command "${EDUCLOUD_JAVA_BIN:-java}" java)"
maven_bin="$(resolve_command "${EDUCLOUD_MAVEN_BIN:-mvn}" maven)"
git_bin="$(resolve_command "${EDUCLOUD_GIT_BIN:-git}" git)"
docker_bin="$(resolve_command "${EDUCLOUD_DOCKER_BIN:-docker}" docker)"

java_output="$("$java_bin" -version 2>&1)"
if [[ "$java_output" =~ version[[:space:]]+\"([0-9]+)(\.[0-9]+)* ]]; then
  java_major="${BASH_REMATCH[1]}"
else
  fail "Unable to determine Java version"
fi
case "$java_major" in
  17|21)
    ;;
  *)
    fail "Java 17 or 21 is required; detected Java $java_major"
    ;;
esac

maven_output="$("$maven_bin" -version 2>&1)"
if [[ "$maven_output" =~ Apache[[:space:]]+Maven[[:space:]]+([0-9]+)\.([0-9]+) ]]; then
  maven_major="${BASH_REMATCH[1]}"
  maven_minor="${BASH_REMATCH[2]}"
else
  fail "Unable to determine Maven version"
fi
if ((maven_major < 3 || (maven_major == 3 && maven_minor < 9))); then
  fail "Maven 3.9 or newer is required; detected $maven_major.$maven_minor"
fi

git_output="$("$git_bin" --version 2>&1)"
[[ "$git_output" == git\ version\ * ]] || fail "Unable to determine Git version"

docker_output="$("$docker_bin" --version 2>&1)"
[[ "$docker_output" == Docker\ version\ * ]] || fail "Unable to determine Docker version"

compose_output="$("$docker_bin" compose version 2>&1)"
if [[ "$compose_output" =~ Docker[[:space:]]+Compose[[:space:]]+version[[:space:]]+v?([0-9]+) ]]; then
  compose_major="${BASH_REMATCH[1]}"
else
  fail "Unable to determine Docker Compose plugin version"
fi
((compose_major >= 2)) || fail "Docker Compose plugin version 2 or newer is required; detected $compose_major"

if [[ "${EDUCLOUD_SKIP_DOCKER_DAEMON:-0}" != "1" ]]; then
  "$docker_bin" info >/dev/null 2>&1 || fail "Docker daemon is not reachable by the current user"
fi

printf 'Rocky Linux: %s\n' "$os_version"
printf 'Java: %s\n' "$java_major"
printf 'Maven: %s.%s\n' "$maven_major" "$maven_minor"
printf 'Git: %s\n' "$git_output"
printf 'Docker: %s\n' "$docker_output"
printf 'Compose: %s\n' "$compose_output"
printf 'Prerequisite check passed\n'
