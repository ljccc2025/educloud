import paramiko, json, time, os

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=60):
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace') + e.read().decode('utf-8', errors='replace')

# 1. 上传新 order jar
sftp = ssh.open_sftp()
local_jar = r'd:\microservice\educloud-backend\educloud-order\target\educloud-order-1.0.0-SNAPSHOT.jar'
remote_jar = '/root/educloud/.worktrees/educloud-backend-foundation/educloud-backend/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar'
sftp.put(local_jar, remote_jar)
sftp.close()
print("jar uploaded:", os.path.getsize(local_jar), "bytes")

# 2. 停掉旧 order（字符类规避自匹配）
print(run("pkill -f 'educloud-orde[r]-1.0.0-SNAPSHOT.jar'; sleep 3; pgrep -af 'educloud-order' | grep -v pgrep || echo ORDER_STOPPED"))

# 3. 以与 start-dev.sh 相同的环境变量重启
start_cmd = """cd /root/educloud/.worktrees/educloud-backend-foundation && \
SERVER_PORT=8091 ORDER_MANAGEMENT_PORT=8092 \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 EDUCLOUD_ORDER_DB_PASSWORD=b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95 \
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD= \
RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=5672 \
RABBITMQ_DEFAULT_USER=educloud_local RABBITMQ_DEFAULT_PASS=14451aa84db1b5ac47576ea9058d287c8e5ef5cb58675f42 \
RABBITMQ_DEFAULT_VHOST=educloud \
NACOS_SERVER_ADDR=127.0.0.1:8848 \
EDUCLOUD_ORDER_NACOS_USERNAME=nacos EDUCLOUD_ORDER_NACOS_PASSWORD=nacos \
ORDER_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
EDUCLOUD_ORDER_JWT_ISSUER=https://issuer.educloud.local \
EDUCLOUD_ORDER_JWT_AUDIENCE=educloud-api \
EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
setsid nohup java -jar educloud-backend/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar \
> /tmp/educloud-live/order.log 2>&1 < /dev/null &"""
print(run(start_cmd))
print("order launched")

# 4. 等待就绪
up = False
for i in range(40):
    time.sleep(3)
    r = run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health")
    if '200' in r:
        up = True
        break
print("order UP:", up)
if not up:
    print(run("tail -30 /tmp/educloud-live/order.log"))
    raise SystemExit(1)

# 5. 登录 + 下单复现
login = json.loads(run("""curl -s -m 8 -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"loginName":"fe_demo_10","password":"FeDemo@2026","portal":"STUDENT"}'"""))
token = login['data']['accessToken']
idem = json.loads(run(f"""curl -s -m 8 http://127.0.0.1:8080/api/v1/orders/idempotency-token -H 'Authorization: Bearer {token}'"""))['data']
if isinstance(idem, dict):
    idem = idem['token']
resp = run(f"""curl -s -m 15 -X POST http://127.0.0.1:8080/api/v1/orders -H 'Content-Type: application/json' -H 'Authorization: Bearer {token}' -d '{{"idempotencyToken":"{idem}","items":[{{"courseId":9000000000000000115,"quantity":1}}]}}'""")
print("=== ORDER RESP ===")
print(resp)

time.sleep(2)
print("=== ORDER WARN/ERROR (真实根因) ===")
print(run("grep -A 15 '获取课程详情失败' /tmp/educloud-live/order.log | head -40"))
ssh.close()
