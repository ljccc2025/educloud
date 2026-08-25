import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
cmds = [
    "curl -si -m 5 http://127.0.0.1:8092/actuator/health/readiness | head -8",
    "curl -si -m 5 http://127.0.0.1:8092/actuator/health | head -12",
    "curl -s -o /dev/null -w 'gateway order route: %{http_code}\\n' -m 8 http://192.168.100.136:8080/api/v1/orders",
]
for c in cmds:
    print('===', c[:70])
    _, o, e = ssh.exec_command(c)
    print(o.read().decode('utf-8', errors='replace'))
ssh.close()
