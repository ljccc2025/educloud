import paramiko, json, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def curl(cmd):
    _, o, _ = ssh.exec_command(cmd, timeout=40)
    return o.read().decode('utf-8', errors='replace')

# 1. 学生登录拿 token（正确载荷：loginName + portal）
login = json.loads(curl("""curl -s -m 8 -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"loginName":"fe_demo_10","password":"FeDemo@2026","portal":"STUDENT"}'"""))
token = login['data']['accessToken']
print("login OK")

# 2. 取幂等 token
idem_resp = json.loads(curl(f"""curl -s -m 8 http://127.0.0.1:8080/api/v1/orders/idempotency-token -H 'Authorization: Bearer {token}'"""))
idem = idem_resp['data']['token'] if isinstance(idem_resp.get('data'), dict) else idem_resp['data']
print("idem:", idem)

# 3. 标记日志位置并触发下单（正确载荷：items 数组）
ssh.exec_command("wc -l /tmp/educloud-live/order.log /tmp/educloud-live/course.log > /tmp/mark.txt", timeout=15)
import time as _t; _t.sleep(0.5)
print("=== ORDER RESP ===")
print(curl(f"""curl -s -m 15 -X POST http://127.0.0.1:8080/api/v1/orders -H 'Content-Type: application/json' -H 'Authorization: Bearer {token}' -d '{{"idempotencyToken":"{idem}","items":[{{"courseId":9000000000000000115,"quantity":1}}]}}'"""))

time.sleep(2)

# 4. order 日志新增部分
_, o, _ = ssh.exec_command("cat /tmp/mark.txt; tail -n +$(($(cut -d' ' -f1 /tmp/mark.txt | head -1)+1)) /tmp/educloud-live/order.log | grep -Ev 'Nacos|heartbeat' | tail -40", timeout=30)
print("=== ORDER LOG NEW ===")
print(o.read().decode('utf-8', errors='replace')[-4000:])

# 5. course 日志是否收到请求
_, o, _ = ssh.exec_command("tail -n 40 /tmp/educloud-live/course.log | grep -Ev 'Nacos|heartbeat'", timeout=30)
print("=== COURSE LOG TAIL ===")
print(o.read().decode('utf-8', errors='replace')[-3000:])
ssh.close()
