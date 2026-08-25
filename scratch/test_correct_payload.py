import paramiko, json
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=60):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

# 登录
login = json.loads(run("""curl -s -m 8 -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"loginName":"fe_demo_10","password":"FeDemo@2026","portal":"STUDENT"}'"""))
token = login['data']['accessToken']

# 幂等 token
idem = json.loads(run(f"""curl -s -m 8 http://127.0.0.1:8080/api/v1/orders/idempotency-token -H 'Authorization: Bearer {token}'"""))['data']
if isinstance(idem, dict):
    idem = idem['token']

# 正确载荷：courseId 直接购买
resp = run(f"""curl -s -m 20 -X POST http://127.0.0.1:8080/api/v1/orders -H 'Content-Type: application/json' -H 'Authorization: Bearer {token}' -d '{{"idempotencyToken":"{idem}","courseId":9000000000000000115}}'""")
print("=== ORDER RESP ===")
print(resp)
ssh.close()
