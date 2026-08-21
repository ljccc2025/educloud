#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/generate-local-env.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT

failed=0

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failed=$((failed + 1))
}

generated_env="$fixture_root/generated.env"
if bash "$script_under_test" --output "$generated_env" >"$fixture_root/generate.log" 2>&1; then
  pass 'generates an environment file at an explicit path'
else
  fail 'does not generate an environment file at an explicit path'
fi

if [[ -f "$generated_env" ]]; then
  expected_keys="$(grep -E '^[A-Z0-9_]+=' "$repo_root/deploy/docker-compose/.env.example" | cut -d= -f1 | sort)"
  actual_keys="$(grep -E '^[A-Z0-9_]+=' "$generated_env" | cut -d= -f1 | sort)"
  if [[ "$actual_keys" == "$expected_keys" ]]; then
    pass 'preserves the complete environment key contract'
  else
    fail 'generated environment keys differ from .env.example'
  fi

  if grep -Fq 'ChangeMe' "$generated_env"; then
    fail 'generated environment retains placeholder credentials'
  else
    pass 'replaces every placeholder credential'
  fi

  secret_values="$(grep -E '(_PASSWORD|_PASS|_TOKEN|_IDENTITY_VALUE)=' "$generated_env" | cut -d= -f2-)"
  secret_count="$(wc -l <<<"$secret_values" | tr -d ' ')"
  unique_secret_count="$(sort -u <<<"$secret_values" | wc -l | tr -d ' ')"
  if [[ "$secret_count" == "$unique_secret_count" ]]; then
    pass 'generates distinct secret values'
  else
    fail 'generated secret values are not distinct'
  fi

  invalid_secret_count="$(grep -E '(_PASSWORD|_PASS|_IDENTITY_VALUE)=' "$generated_env" | cut -d= -f2- | grep -Evc '^[A-Za-z0-9_.:@%+=-]{16,128}$' || true)"
  if [[ "$invalid_secret_count" == '0' ]]; then
    pass 'generated passwords satisfy the MySQL initialization character contract'
  else
    fail 'a generated password violates the approved character contract'
  fi

  if [[ "$(uname -s)" == 'Linux' ]]; then
    file_mode="$(stat -c '%a' "$generated_env")"
    if [[ "$file_mode" == '600' ]]; then
      pass 'restricts the environment file to its owner on Linux'
    else
      fail "unexpected Linux file mode: $file_mode"
    fi
  fi

  if (set -a; . "$generated_env" >/dev/null 2>&1; set +a); then
    pass 'generated environment is sourceable in a shell'
  else
    fail 'generated environment cannot be sourced by the shell'
  fi
fi

printf 'DO_NOT_OVERWRITE\n' >"$generated_env"
if bash "$script_under_test" --output "$generated_env" >"$fixture_root/refuse.log" 2>&1; then
  fail 'overwrites an existing environment file without --force'
elif [[ "$(<"$generated_env")" == 'DO_NOT_OVERWRITE' ]]; then
  pass 'refuses to overwrite an existing environment file by default'
else
  fail 'changes an existing environment file while reporting refusal'
fi

if bash "$script_under_test" --force --output "$generated_env" >"$fixture_root/force.log" 2>&1 && \
   ! grep -Fq 'DO_NOT_OVERWRITE' "$generated_env"; then
  pass 'replaces an existing placeholder file only with --force'
else
  fail 'does not replace an existing file with --force'
fi

if grep -Eq '(PASSWORD|PASS|TOKEN)=' "$fixture_root/generate.log" "$fixture_root/force.log"; then
  fail 'prints a generated secret to standard output'
else
  pass 'does not print generated secrets'
fi

if ((failed > 0)); then
  printf '%s local environment generation checks failed\n' "$failed" >&2
  exit 1
fi

printf 'All local environment generation checks passed\n'
