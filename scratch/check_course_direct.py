import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
_, o, _ = ssh.exec_command("curl -s -m 8 http://127.0.0.1:8089/api/v1/courses/9000000000000000115 | head -c 900")
print(o.read().decode('utf-8', errors='replace'))
ssh.close()
