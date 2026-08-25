import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
cmds = [
    "curl -s -o /dev/null -w 'gateway readiness: %{http_code}\\n' -m 4 http://127.0.0.1:8081/actuator/health/readiness",
    "curl -s -o /dev/null -w 'gateway business: %{http_code}\\n' -m 4 http://127.0.0.1:8080/api/v1/courses?page=1",
    "ps -eo pid,lstart,args | grep 'educloud-.*1.0.0' | grep -v grep | awk '{print $1, $2, $3, $4, $5, $6, $NF}'",
    "curl -s -m 4 http://127.0.0.1:8094/actuator/health | head -c 300",
]
for c in cmds:
    print('===', c[:70])
    _, o, _ = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace'))
ssh.close()
