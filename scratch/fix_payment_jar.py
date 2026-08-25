"""修复 payment fat jar：上传修复版 pom → VM 重新打包 → 启动 payment → 续跑其余服务。"""
import posixpath
import time
import paramiko

VM = ("192.168.100.136", 22, "root", "1")
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"
LOCAL_POM = r"D:\microservice\educloud-backend\educloud-payment\pom.xml"


def connect():
    for i in range(5):
        try:
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect(VM[0], port=VM[1], username=VM[2], password=VM[3],
                        timeout=30, banner_timeout=30, auth_timeout=30)
            return ssh
        except Exception as e:
            print(f"[SSH] 第 {i+1} 次失败: {e}")
            time.sleep(5)
    raise RuntimeError("SSH fail")


def run(ssh, cmd, timeout=600):
    print(f"\n[EXEC] {cmd[:200]}")
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", errors="replace")
    err = e.read().decode("utf-8", errors="replace")
    st = o.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-2000:])
    if err.strip() and st != 0:
        print("[STDERR]", err.strip()[-600:])
    print(f"[exit={st}]")
    return st, out


ssh = connect()
sftp = ssh.open_sftp()
with open(LOCAL_POM, "rb") as f:
    data = f.read().replace(b"\r\n", b"\n")
with sftp.open(posixpath.join(REMOTE_ROOT, "educloud-backend/educloud-payment/pom.xml"), "wb") as rf:
    rf.write(data)
print("[UPLOAD] educloud-payment/pom.xml（补 repackage）")

st, out = run(ssh, f"cd {REMOTE_ROOT}/educloud-backend && mvn -pl educloud-payment package -DskipTests=true -o 2>&1 | tail -8; echo MVN_EXIT=${{PIPESTATUS[0]}}", timeout=900)
if "MVN_EXIT=0" not in out:
    # 离线失败则在线重试
    st, out = run(ssh, f"cd {REMOTE_ROOT}/educloud-backend && mvn -pl educloud-payment package -DskipTests=true 2>&1 | tail -8; echo MVN_EXIT=${{PIPESTATUS[0]}}", timeout=900)
    if "MVN_EXIT=0" not in out:
        print("!!! payment 打包失败")
        raise SystemExit(1)

# 验证 fat jar（应含 BOOT-INF）
run(ssh, f"ls -la {REMOTE_ROOT}/educloud-backend/educloud-payment/target/educloud-payment-1.0.0-SNAPSHOT.jar && "
         f"unzip -l {REMOTE_ROOT}/educloud-backend/educloud-payment/target/educloud-payment-1.0.0-SNAPSHOT.jar | grep -c BOOT-INF")

# 续跑 start-dev.sh（payment 段会重新拉起，其余服务补启）
try:
    ssh.exec_command(f"cd {REMOTE_ROOT} && setsid nohup bash deploy/scripts/start-dev.sh "
                     "> /tmp/educloud-live/start-dev-m08d.log 2>&1 < /dev/null &", timeout=15)
except Exception as e:
    print(f"[INFO] 启动已发出（{e}）")

targets = {
    "user": "http://127.0.0.1:8083/actuator/health",
    "gateway": "http://127.0.0.1:8081/actuator/health",
    "payment": "http://127.0.0.1:8094/actuator/health",
    "course": "http://127.0.0.1:8090/actuator/health",
    "file": "http://127.0.0.1:8088/actuator/health",
    "content": "http://127.0.0.1:8086/actuator/health",
    "order": "http://127.0.0.1:8092/actuator/health",
}
pending = dict(targets)
deadline = time.time() + 600
while pending and time.time() < deadline:
    time.sleep(10)
    for name, url in list(pending.items()):
        try:
            _, o, _ = ssh.exec_command(f"curl -s -m 4 {url}")
            body = o.read().decode("utf-8", errors="replace")
        except Exception:
            ssh = connect()
            continue
        if '"UP"' in body:
            print(f"[UP] {name}")
            del pending[name]

if pending:
    print(f"!!! 未就绪: {list(pending.keys())}")
    _, o, _ = ssh.exec_command("tail -20 /tmp/educloud-live/start-dev-m08d.log")
    print(o.read().decode('utf-8', errors='replace')[-2000:])
    for name in pending:
        _, o, _ = ssh.exec_command(f"tail -12 /tmp/educloud-live/{name}.log")
        print(f"===== {name}.log =====")
        print(o.read().decode('utf-8', errors='replace')[-1500:])
else:
    print("[DONE] 全部 7 服务 UP")

sftp.close()
ssh.close()
