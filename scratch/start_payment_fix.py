"""修复：重传新版 start-dev.sh 并手动启动 payment（8093/8094）。"""
import os
import posixpath
import time
import paramiko

VM = ("192.168.100.136", 22, "root", "1")
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"
LOCAL_SH = r"D:\microservice\deploy\scripts\start-dev.sh"


def connect():
    for i in range(5):
        try:
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect(*VM[:2], username=VM[2], password=VM[3],
                        timeout=30, banner_timeout=30, auth_timeout=30)
            return ssh
        except Exception as e:
            print(f"[SSH] 第 {i+1} 次失败: {e}")
            time.sleep(5)
    raise RuntimeError("SSH fail")


def run(ssh, cmd, timeout=300):
    print(f"\n[EXEC] {cmd[:200]}")
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", errors="replace")
    err = e.read().decode("utf-8", errors="replace")
    st = o.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-2500:])
    if err.strip() and st != 0:
        print("[STDERR]", err.strip()[-800:])
    print(f"[exit={st}]")
    return st, out


ssh = connect()
sftp = ssh.open_sftp()

# 1. 重传新版 start-dev.sh（含 [8/9] payment 段）
with open(LOCAL_SH, "rb") as f:
    data = f.read().replace(b"\r\n", b"\n")
remote_sh = posixpath.join(REMOTE_ROOT, "deploy/scripts/start-dev.sh")
with sftp.open(remote_sh, "wb") as rf:
    rf.write(data)
run(ssh, f"sed -i 's/\\r$//' {remote_sh} && grep -c 'educloud-payment' {remote_sh}")

# 2. 确认 jar 存在
run(ssh, f"ls -la {REMOTE_ROOT}/educloud-backend/educloud-payment/target/educloud-payment-1.0.0-SNAPSHOT.jar")

# 3. 手动启动 payment（与 start-dev.sh [8/9] 段相同环境变量）
run(ssh, "pkill -f educloud-payment-1.0.0 || true; sleep 2")
launch = (
    f"cd {REMOTE_ROOT} && "
    "SERVER_PORT=8093 PAYMENT_MANAGEMENT_PORT=8094 "
    "MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 "
    "EDUCLOUD_PAYMENT_DB_PASSWORD=0776b911c75c80efcb36c841c888e285a73e46c7ad721be0 "
    "REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD= "
    "RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=5672 "
    "RABBITMQ_DEFAULT_USER=educloud_local RABBITMQ_DEFAULT_PASS=14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42 "
    "RABBITMQ_DEFAULT_VHOST=educloud "
    "NACOS_SERVER_ADDR=127.0.0.1:8848 "
    "PAYMENT_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json "
    "EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 "
    "setsid nohup java -jar educloud-backend/educloud-payment/target/educloud-payment-1.0.0-SNAPSHOT.jar "
    "> /tmp/educloud-live/payment.log 2>&1 < /dev/null &"
)
try:
    run(ssh, launch, timeout=20)
except Exception as e:
    print(f"[INFO] 启动命令已发出（{e} 属正常分离现象）")

# 4. 轮询 payment 就绪
for i in range(40):
    time.sleep(6)
    try:
        _, o, _ = ssh.exec_command("curl -s -m 4 http://127.0.0.1:8094/actuator/health")
        body = o.read().decode("utf-8", errors="replace")
    except Exception:
        ssh = connect()
        continue
    print(f"[poll {i}] {body.strip()[:80]}")
    if '"UP"' in body:
        print("[DONE] payment UP")
        break
else:
    _, o, _ = ssh.exec_command("tail -40 /tmp/educloud-live/payment.log")
    print(o.read().decode("utf-8", errors="replace")[-3000:])

sftp.close()
ssh.close()
