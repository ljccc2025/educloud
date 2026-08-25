"""修复 order application.yml 后：重传 → 重打包 → 重启 order → 健康检查。"""
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
        print(out.strip()[-3000:])
    if err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-1500:]}")
    print(f"[exit={status}]")
    return status, out


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=22, username="root", password="1", timeout=15)
    sftp = ssh.open_sftp()

    # 上传（LF 行尾）
    with open(LOCAL_YML, "rb") as f:
        data = f.read().replace(b"\r\n", b"\n")
    tmp = LOCAL_YML + ".lf"
    with open(tmp, "wb") as f:
        f.write(data)
    sftp.put(tmp, f"{REMOTE_ROOT}/educloud-backend/educloud-order/src/main/resources/application.yml")
    os.remove(tmp)
    print("application.yml uploaded.")

    # 重打包 order（-am 包含 reactor 内 common 依赖）
    st, out = run(
        ssh,
        f"cd {REMOTE_ROOT}/educloud-backend && mvn -pl educloud-order -am package -DskipTests=true 2>&1 | tail -12; "
        "echo MVN_EXIT=${PIPESTATUS[0]}",
        timeout=900,
    )
    if "MVN_EXIT=0" not in out:
        print("!!! order package failed")
        sftp.close(); ssh.close()
        return

    # 重启 order（start-dev.sh 对已运行服务幂等跳过）
    run(ssh, "pkill -f 'educloud-order-1.0.0-SNAPSHOT.jar' || true")
    time.sleep(3)
    run(ssh, f"cd {REMOTE_ROOT} && nohup bash deploy/scripts/start-dev.sh > /tmp/educloud-live/start-dev-run2.log 2>&1 & echo STARTED")

    for i in range(24):
        time.sleep(10)
        st, out = run(ssh, "curl -s -m 3 http://127.0.0.1:8092/actuator/health/readiness", timeout=30)
        if '"status":"UP"' in out:
            print("\nORDER SERVICE UP.")
            # 全服务最终确认
            for name, url in [
                ("user", "http://127.0.0.1:8083/actuator/health/readiness"),
                ("gateway", "http://127.0.0.1:8081/actuator/health/readiness"),
                ("course", "http://127.0.0.1:8090/actuator/health/readiness"),
                ("file", "http://127.0.0.1:8088/actuator/health/readiness"),
                ("content", "http://127.0.0.1:8086/actuator/health/readiness"),
                ("order", "http://127.0.0.1:8092/actuator/health/readiness"),
            ]:
                run(ssh, f"echo -n '{name}: '; curl -s -m 3 {url}")
            run(ssh, "for p in 5173 5174 5175; do echo -n \"vite $p: \"; curl -s -o /dev/null -w '%{http_code}\\n' -m 3 http://127.0.0.1:$p; done")
            break
    else:
        print("!!! ORDER NOT READY")
        run(ssh, "tail -50 /tmp/educloud-live/order.log")
    ssh.close()


if __name__ == "__main__":
    main()
