import time
import paramiko


def connect():
    for i in range(5):
        try:
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect('192.168.100.136', 22, 'root', '1',
                        timeout=30, banner_timeout=30, auth_timeout=30)
            return ssh
        except Exception as e:
            print(f"[SSH] 第 {i+1} 次失败: {e}")
            time.sleep(5)
    raise RuntimeError("SSH 连接失败")


ssh = connect()
targets = [
    ("user", "http://127.0.0.1:8083/actuator/health"),
    ("gateway", "http://127.0.0.1:8081/actuator/health"),
    ("course", "http://127.0.0.1:8090/actuator/health"),
    ("file", "http://127.0.0.1:8088/actuator/health"),
    ("content", "http://127.0.0.1:8086/actuator/health"),
    ("order", "http://127.0.0.1:8092/actuator/health"),
    ("payment", "http://127.0.0.1:8094/actuator/health"),
]
pending = dict(targets)
deadline = time.time() + 360
while pending and time.time() < deadline:
    time.sleep(8)
    for name, url in list(pending.items()):
        try:
            _, o, _ = ssh.exec_command(f"curl -s -m 4 {url}")
            body = o.read().decode("utf-8", errors="replace")
            if '"UP"' in body:
                print(f"[UP] {name}")
                del pending[name]
        except Exception as e:
            print(f"[WARN] {name}: {e}")
            ssh = connect()

if pending:
    print(f"!!! 未就绪: {list(pending.keys())}")
    for name in pending:
        _, o, _ = ssh.exec_command(f"tail -30 /tmp/educloud-live/{name}.log")
        print(f"===== {name}.log =====")
        print(o.read().decode('utf-8', errors='replace')[-2500:])
else:
    print("[DONE] 全部 7 个服务 UP")
    _, o, _ = ssh.exec_command("curl -s -o /dev/null -w '%{http_code}' -m 5 http://127.0.0.1:5173; echo; "
                               "curl -s -o /dev/null -w '%{http_code}' -m 5 http://127.0.0.1:5174; echo; "
                               "curl -s -o /dev/null -w '%{http_code}' -m 5 http://127.0.0.1:5175")
    print("前端三端:", o.read().decode().strip())
ssh.close()
