#!/usr/bin/env bash

# run-migrations.sh 契约测试（fixture 模式，不连接真实 MySQL）
# 依据：M03 实施计划任务 1 与数据设计第 17.1 节。

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
script_under_test="$repo_root/deploy/scripts/run-migrations.sh"
sql_dir="$repo_root/deploy/sql/user"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
mkdir -p "$fixture_root/bin"

failed=0
pass() { printf 'PASS: %s
' "$1"; }
fail() { printf 'FAIL: %s
' "$1" >&2; failed=$((failed + 1)); }

# 假 mysql：记录调用并模拟 GET_LOCK / 历史查询 / 指定失败标记。
cat >"$fixture_root/bin/mysql" <<'FAKE'
#!/usr/bin/env bash
set -eo pipefail
input="$(cat)"
{
  printf 'ARGS:%s
STDIN:%s
===END===
' "$*" "$input"
} >>"$FAKE_TRANSCRIPT"
case "$* $input" in
  *"GET_LOCK"*) printf '1
'; exit 0 ;;
  *"RELEASE_LOCK"*) exit 0 ;;
  *"SELECT version, checksum_sha256"*) cat "$FAKE_HISTORY" 2>/dev/null || true; exit 0 ;;
esac
if [[ -n "$FAKE_FAIL_MARKER" && "$input" == *"$FAKE_FAIL_MARKER"* ]]; then
  exit 1
fi
exit 0
FAKE
chmod +x "$fixture_root/bin/mysql"

run_migrations() {
  MYSQL_HOST=db.example MYSQL_PORT=3306 EDUCLOUD_USER_MIGRATION_PASSWORD=testmigrationpass     PATH="$fixture_root/bin:$PATH" bash "$script_under_test" --service user "$@"
}

# 1) 首次运行：按顺序应用 V000/V001/V002 并记录 SUCCESS，锁先于脚本。
FAKE_TRANSCRIPT="$fixture_root/transcript1" FAKE_HISTORY="$fixture_root/history1"   run_migrations >"$fixture_root/run1.log" 2>&1 || fail 'first migration run must succeed'
grep -Fq 'Applying V000__technical_tables.sql' "$fixture_root/run1.log" || fail 'V000 not applied in order'
grep -Fq 'Applying V001__user_identity_and_rbac.sql' "$fixture_root/run1.log" || fail 'V001 not applied in order'
grep -Fq 'Applying V002__session_and_platform.sql' "$fixture_root/run1.log" || fail 'V002 not applied in order'
grep -Fq "GET_LOCK('educloud_user_migration'" "$fixture_root/transcript1" || fail 'GET_LOCK not acquired'
grep -Fq 'RELEASE_LOCK' "$fixture_root/transcript1" || fail 'migration lock not released'
grep -Fq 'CREATE TABLE outbox_event' "$fixture_root/transcript1" || fail 'V000 content not executed'
grep -Fq 'CREATE TABLE sys_user' "$fixture_root/transcript1" || fail 'V001 content not executed'
grep -Fq 'CREATE TABLE refresh_session' "$fixture_root/transcript1" || fail 'V002 content not executed'
[[ "$(grep -c "'SUCCESS'" "$fixture_root/transcript1")" -ge 3 ]] || fail 'history SUCCESS rows not recorded for all versions'
lock_line="$(grep -n 'GET_LOCK' "$fixture_root/transcript1" | head -1 | cut -d: -f1)"
first_script_line="$(grep -n 'CREATE TABLE outbox_event' "$fixture_root/transcript1" | head -1 | cut -d: -f1)"
[[ -n "$lock_line" && -n "$first_script_line" && "$lock_line" -lt "$first_script_line" ]] ||   fail 'migration lock must be acquired before script execution'
pass 'applies pending migrations in order under the migration lock'

# 2) 幂等重跑：已应用版本不再执行。
: >"$fixture_root/history2"
for file in "$sql_dir"/V[0-9]*__*.sql; do
  version="$(basename "$file" | cut -d_ -f1)"
  checksum="$(sha256sum "$file" | awk '{print $1}')"
  printf '%s	%s
' "$version" "$checksum" >>"$fixture_root/history2"
done
FAKE_TRANSCRIPT="$fixture_root/transcript2" FAKE_HISTORY="$fixture_root/history2"   run_migrations >"$fixture_root/run2.log" 2>&1 || fail 'idempotent re-run must succeed'
if grep -Fq 'Applying' "$fixture_root/run2.log"; then
  fail 're-run reapplied an already applied migration'
fi
pass 'skips already applied migrations on re-run'

# 3) checksum 篡改拒绝：已应用脚本内容变更必须失败。
sed -E "s/^V001.*/V001	0000000000000000000000000000000000000000000000000000000000000000/"   "$fixture_root/history2" >"$fixture_root/history3"
if FAKE_TRANSCRIPT="$fixture_root/transcript3" FAKE_HISTORY="$fixture_root/history3"   run_migrations >"$fixture_root/run3.log" 2>&1; then
  fail 'checksum mismatch must fail the run'
fi
grep -Fq 'Checksum mismatch' "$fixture_root/run3.log" || fail 'checksum mismatch error message missing'
pass 'rejects checksum changes on published migrations'

# 4) DDL 失败：记录 FAILED 并停止。
: >"$fixture_root/history4"
if FAKE_TRANSCRIPT="$fixture_root/transcript4" FAKE_HISTORY="$fixture_root/history4"   FAKE_FAIL_MARKER='refresh_session' run_migrations >"$fixture_root/run4.log" 2>&1; then
  fail 'failing migration must fail the run'
fi
grep -Fq "'FAILED'" "$fixture_root/transcript4" || fail 'FAILED history row not recorded'
grep -Fq "'V002'" "$fixture_root/transcript4" || fail 'FAILED row must reference the failing version'
pass 'records FAILED and stops on migration failure'

# 5) 目录缺失与帮助。
if bash "$script_under_test" --service nosuch >/dev/null 2>&1; then
  fail 'missing sql directory must fail'
fi
bash "$script_under_test" --help >/dev/null 2>&1 || fail '--help must succeed'
pass 'rejects missing sql directory and supports --help'

if ((failed > 0)); then
  printf '%s migration runner checks failed
' "$failed" >&2
  exit 1
fi
printf 'All migration runner checks passed
'
