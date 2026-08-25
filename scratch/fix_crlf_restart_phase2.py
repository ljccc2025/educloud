"""修复 VM 上 start-dev.sh 的 CRLF 行尾并启动服务，轮询健康检查。"""
import time
import paramiko

VM_HOST = "192.168.100.136"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"


def run(ssh, cmd, timeout=600):
    print(f"\n[EXEC] {cmd[:200]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-3000:])
    if err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-1500:]}")
    print(f"[exit={status}]")
    return status, out


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=22, username="root", password="1", timeout=15)

    # 修复行尾 + 校验
    run(ssh, f"sed -i 's/\\r$//' {REMOTE_ROOT}/deploy/scripts/start-dev.sh")
    run(ssh, f"head -3 {REMOTE_ROOT}/deploy/scripts/start-dev.sh | cat -A | head -3")

    # 启动（nohup 后台，避免 exec 通道阻塞）
    run(ssh, f"cd {REMOTE_ROOT} && nohup bash deploy/scripts/start-dev.sh > /tmp/educloud-live/start-dev-run.log 2>&1 & echo STARTED_PID=$!")

    checks = [
        ("user", "http://127.0.0.1:8083/actuator/health/readiness"),
        ("gateway", "http://127.0.0.1:8081/actuator/health/readiness"),
        ("course", "http://127.0.0.1:8090/actuator/health/readiness"),
        ("content", "http://127.0.0.1:8086/actuator/health/readiness"),
        ("order", "http://127.0.0.1:8092/actuator/health/readiness"),
    ]
    pending = dict(checks)
    for i in range(30):
        time.sleep(10)
        for name in list(pending):
            st, out = run(ssh, f"curl -s -m 3 {pending[name]}", timeout=30)
            if '"status":"UP"' in out:
                print(f"  [UP] {name}")
                del pending[name]
        if not pending:
            break
    if pending:
        print(f"!!! NOT READY: {list(pending)}")
        run(ssh, "tail -30 /tmp/educloud-live/start-dev-run.log")
        for name in pending:
            run(ssh, f"tail -30 /tmp/educloud-live/{name}.log")
    else:
        print("\nALL SERVICES UP.")
        # 前端 vite 探活
        run(ssh, "curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:5173; echo; curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:5174; echo; curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:5175")
    ssh.close()


if __name__ == "__main__":
    main()
