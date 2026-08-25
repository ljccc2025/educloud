import paramiko, json, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=60):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

REDIS_PWD = 'c4d463f0a8cd8a0daf3558ec08e772f2cb2d26c4aabd52b6'

# 1. 停掉失败/残留的 order
print(run("pkill -f 'educloud-orde[r]-1.0.0-SNAPSHOT.jar'; sleep 2; echo KILLED"))

# 2. 写启动脚本到 VM（规避 paramiko channel 不关闭问题）
start_sh = f"""#!/bin/bash
cd /root/educloud/.worktrees/educloud-backend-foundation
SERVER_PORT=8091 ORDER_MANAGEMENT_PORT=8092 \\
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 EDUCLOUD_ORDER_DB_PASSWORD=b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95 \\
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD={REDIS_PWD} \\
RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=5672 \\
RABBITMQ_DEFAULT_USER=educloud_local RABBITMQ_DEFAULT_PASS=14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42 \\
RABBITMQ_DEFAULT_VHOST=educloud \\
NACOS_SERVER_ADDR=127.0.0.1:8848 \\
EDUCLOUD_ORDER_NACOS_USERNAME=nacos EDUCLOUD_ORDER_NACOS_PASSWORD=nacos \\
ORDER_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \\
EDUCLOUD_ORDER_JWT_ISSUER=https://issuer.educloud.local \\
EDUCLOUD_ORDER_JWT_AUDIENCE=educloud-api \\
EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \\
setsid nohup java -jar educloud-backend/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar \\
> /tmp/educloud-live/order.log 2>&1 < /dev/null &
"""
sftp = ssh.open_sftp()
with sftp.open('/tmp/restart_order.sh', 'w') as f:
    f.write(start_sh)
sftp.close()
run("chmod +x /tmp/restart_order.sh")
print(run("setsid /tmp/restart_order.sh > /dev/null 2>&1; echo LAUNCH_DONE", timeout=20))

# 3. 等待就绪
up = False
for i in range(50):
    time.sleep(3)
    r = run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health", timeout=15)
    if '200' in r:
        up = True
        break
print("order UP:", up, "(waited", (i + 1) * 3, "s)")
if not up:
    print(run("tail -40 /tmp/educloud-live/order.log"))
    raise SystemExit(1)

# 4. 登录 + 下单复现
login = json.loads(run("""curl -s -m 8 -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"loginName":"fe_demo_10","password":"FeDemo@2026","portal":"STUDENT"}'"""))
token = login['data']['accessToken']
idem = json.loads(run(f"""curl -s -m 8 http://127.0.0.1:8080/api/v1/orders/idempotency-token -H 'Authorization: Bearer {token}'"""))['data']
if isinstance(idem, dict):
    idem = idem['token']
resp = run(f"""curl -s -m 20 -X POST http://127.0.0.1:8080/api/v1/orders -H 'Content-Type: application/json' -H 'Authorization: Bearer {token}' -d '{{"idempotencyToken":"{idem}","items":[{{"courseId":9000000000000000115,"quantity":1}}]}}'""")
print("=== ORDER RESP ===")
print(resp)

time.sleep(2)
print("=== 根因日志 ===")
print(run("grep -B2 -A 25 '获取课程详情失败' /tmp/educloud-live/order.log | head -60"))
ssh.close()
