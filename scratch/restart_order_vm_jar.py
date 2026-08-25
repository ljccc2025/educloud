import paramiko, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=60):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

print(run("pkill -f 'educloud-orde[r]-1.0.0-SNAPSHOT.jar'; sleep 2; echo KILLED"))
print(run("setsid /tmp/restart_order.sh > /dev/null 2>&1; echo LAUNCHED", timeout=20))

up = False
for i in range(40):
    time.sleep(3)
    r = run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health", timeout=15)
    if '200' in r:
        up = True
        break
print("order UP:", up)

# 快速冒烟：登录 + 下单（用另一门可购课程，避免 115 已报名）
import json
login = json.loads(run("""curl -s -m 8 -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"loginName":"fe_demo_10","password":"FeDemo@2026","portal":"STUDENT"}'"""))
token = login['data']['accessToken']
st = run("curl -s -o /dev/null -w '%{http_code}' -m 5 http://127.0.0.1:8080/api/v1/courses?page=1&pageSize=1 -H 'Authorization: Bearer %s'" % token)
print("courses api smoke:", st)
ssh.close()
