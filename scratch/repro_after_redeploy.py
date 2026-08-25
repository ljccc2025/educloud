import paramiko, json, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=60):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

# 1. 等待就绪
up = False
for i in range(40):
    time.sleep(3)
    r = run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health")
    if '200' in r:
        up = True
        break
print("order UP:", up, "(waited", (i + 1) * 3, "s)")
if not up:
    print(run("tail -30 /tmp/educloud-live/order.log"))
    raise SystemExit(1)

# 2. 登录 + 下单复现
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
