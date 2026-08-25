import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
_, o, _ = ssh.exec_command('for p in 8081 8083 8086 8088 8090 8092 8094; do printf "%s:" $p; curl -s -o /dev/null -w "%{http_code}\n" -m 3 http://127.0.0.1:$p/actuator/health; done', timeout=40)
print(o.read().decode())
ssh.close()
