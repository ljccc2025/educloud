import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=30):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

cmds = [
    "pgrep -af 'educloud-order' | grep -v pgrep | head -2",
    "curl -s -o /dev/null -w 'mgmt:%{http_code}\\n' -m 3 http://127.0.0.1:8092/actuator/health",
    "curl -s -o /dev/null -w 'biz:%{http_code}\\n' -m 3 http://127.0.0.1:8091/api/v1/orders/idempotency-token",
    "curl -s -o /dev/null -w 'gw:%{http_code}\\n' -m 5 http://127.0.0.1:8080/api/v1/orders/idempotency-token",
    # Nacos 实例列表
    "curl -s -m 5 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=educloud-order&groupName=EDUCLOUD_SERVICES&namespaceId=public' | head -c 500",
    # order 日志尾部错误
    "grep -E 'ERROR|WARN' /tmp/educloud-live/order.log | tail -6",
]
for c in cmds:
    print('===', c[:60])
    print(run(c))
ssh.close()
