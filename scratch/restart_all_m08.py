"""M08 恢复：上传修复版 start-dev.sh → 安全全量重启 → 健康轮询。"""
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
            ssh.connect(VM[0], port=VM[1], username=VM[2], password=VM[3],
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
        print(out.strip()[-2000:])
    if err.strip() and st != 0:
        print("[STDERR]", err.strip()[-600:])
    print(f"[exit={st}]")
    return st, out


ssh = connect()
sftp = ssh.open_sftp()

# 1. 上传修复版 start-dev.sh（wait_ready 双地址轮询）
with open(LOCAL_SH, "rb") as f:
    data = f.read().replace(b"\r\n", b"\n")
with sftp.open(posixpath.join(REMOTE_ROOT, "deploy/scripts/start-dev.sh"), "wb") as rf:
    rf.write(data)
run(ssh, f"sed -i 's/\\r$//' {REMOTE_ROOT}/deploy/scripts/start-dev.sh")

# 2. 安全停止全部后端服务（[x] 字符类技巧避免 pkill 自匹配）
run(ssh, "for n in use[r] gatewa[y] cours[e] fil[e] conten[t] orde[r] paymen[t]; do "
         "pkill -f \"educloud-$n-1.0.0\" || true; done; sleep 5; "
         "ps -ef | grep 'educloud-.*1.0.0' | grep -v grep | wc -l")

# 3. 重新执行 start-dev.sh（全部端口空闲 → 全量启动）
try:
    run(ssh, f"cd {REMOTE_ROOT} && setsid nohup bash deploy/scripts/start-dev.sh "
             "> /tmp/educloud-live/start-dev-m08b.log 2>&1 < /dev/null &", timeout=15)
except Exception as e:
    print(f"[INFO] 启动已发出（{e} 为正常分离）")

# 4. 轮询全部服务
targets = {
    "user": "http://127.0.0.1:8083/actuator/health",
    "gateway": "http://127.0.0.1:8081/actuator/health",
    "course": "http://127.0.0.1:8090/actuator/health",
    "file": "http://127.0.0.1:8088/actuator/health",
    "content": "http://127.0.0.1:8086/actuator/health",
    "order": "http://127.0.0.1:8092/actuator/health",
    "payment": "http://127.0.0.1:8094/actuator/health",
}
pending = dict(targets)
deadline = time.time() + 480
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
    for name in pending:
        _, o, _ = ssh.exec_command(f"tail -25 /tmp/educloud-live/{name}.log")
        print(f"===== {name}.log =====")
        print(o.read().decode('utf-8', errors='replace')[-2500:])
else:
    print("[DONE] 全部 7 服务 UP")

sftp.close()
ssh.close()
