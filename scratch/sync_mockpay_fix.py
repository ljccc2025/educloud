"""同步 mock-pay fail-closed 默认值修复：上传 order application.yml → 重打包 → 重启 → 健康检查。"""
import os
import posixpath
import time
import paramiko

VM_HOST = "192.168.100.136"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"
LOCAL_YML = r"D:\microservice\educloud-backend\educloud-order\src\main\resources\application.yml"


def run(ssh, cmd, timeout=600):
    print(f"\n[EXEC] {cmd[:250]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-2500:])
    if err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-1000:]}")
    print(f"[exit={status}]")
    return status, out


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=22, username="root", password="1", timeout=15)
    sftp = ssh.open_sftp()

    remote_yml = posixpath.join(REMOTE_ROOT, "educloud-backend/educloud-order/src/main/resources/application.yml")
    with open(LOCAL_YML, "rb") as f:
        data = f.read().replace(b"\r\n", b"\n")
    with sftp.open(remote_yml, "wb") as rf:
        rf.write(data)
    print("[UPLOAD] application.yml (%d bytes, LF)" % len(data))

    run(ssh, f"sed -i 's/\\r$//' {remote_yml} && grep -n 'EDUCLOUD_ENVIRONMENT' {remote_yml}")

    st, out = run(
        ssh,
        f"cd {REMOTE_ROOT}/educloud-backend && mvn -pl educloud-order -am package -DskipTests=true 2>&1 | tail -6; "
        "echo MVN_EXIT=${PIPESTATUS[0]}",
        timeout=900,
    )
    if "MVN_EXIT=0" not in out:
        print("!!! order package failed")
        sftp.close(); ssh.close()
        return

    # 重启 order：kill 后重跑 start-dev.sh（端口检测只会补启缺失的 order，
    # 环境变量与生产启动路径完全一致，EDUCLOUD_ENVIRONMENT=local 由其注入）
    run(ssh, "pkill -f educloud-order-1.0.0 || true; sleep 3")
    run(
        ssh,
        f"cd {REMOTE_ROOT} && setsid nohup bash deploy/scripts/start-dev.sh "
        "> /tmp/educloud-live/start-dev-rerun.log 2>&1 < /dev/null &",
        timeout=30,
    )

    for i in range(30):
        time.sleep(5)
        _, o, _ = ssh.exec_command("curl -s -m 3 http://127.0.0.1:8092/actuator/health/readiness")
        body = o.read().decode("utf-8", errors="replace")
        print(f"[poll {i}] {body.strip()}")
        if '"UP"' in body:
            print("ORDER UP")
            break
    sftp.close(); ssh.close()


if __name__ == "__main__":
    main()
