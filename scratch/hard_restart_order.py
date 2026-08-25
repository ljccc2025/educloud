import paramiko, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=60):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

# 1. 强杀所有 order 进程
print(run("pkill -9 -f 'educloud-orde[r]-1.0.0-SNAPSHOT.jar'; sleep 2; pgrep -af 'educloud-order' | grep -v pgrep || echo ALL_KILLED"))

# 2. 确认端口释放
print(run("ss -tlnp | grep -E '8091|8092' || echo PORTS_FREE"))

# 3. 重启（VM 编译的完整 jar）
print(run("setsid /tmp/restart_order.sh > /dev/null 2>&1; echo LAUNCHED", timeout=20))

# 4. 等就绪 + 双端口验证
up = False
for i in range(50):
    time.sleep(3)
    r = run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health", timeout=15)
    if '200' in r:
        up = True
        break
print("order UP:", up, f"(waited {(i+1)*3}s)")
print(run("ss -tlnp | grep -E '8091|8092' | head -4"))
print(run("curl -s -o /dev/null -w 'biz_port:%{http_code}\\n' -m 3 http://127.0.0.1:8091/api/v1/orders/idempotency-token"))
ssh.close()
