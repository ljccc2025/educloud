import time
import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)

_, o, _ = ssh.exec_command("ps -ef | grep educloud-order-1.0.0 | grep -v grep | head -2")
print("process:", o.read().decode('utf-8', errors='replace').strip() or "(none)")

for i in range(36):
    time.sleep(5)
    _, o, _ = ssh.exec_command("curl -s -m 3 http://127.0.0.1:8092/actuator/health/readiness")
    body = o.read().decode("utf-8", errors="replace")
    print(f"[poll {i}] {body.strip()}")
    if '"UP"' in body:
        print("ORDER UP")
        break
else:
    _, o, _ = ssh.exec_command("tail -30 /tmp/educloud-live/order.log")
    print(o.read().decode('utf-8', errors='replace'))
ssh.close()
