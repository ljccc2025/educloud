import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', username='root', password='1', timeout=15)

stdin, stdout, stderr = ssh.exec_command("ss -tlpn | grep -E '5173|5174|5175|8080|8082|8089|8091|8093|8095|8097|8099|8101'")
print(stdout.read().decode('utf-8', errors='replace'))

stdin, stdout, stderr = ssh.exec_command("curl -s http://127.0.0.1:8102/actuator/health")
print("Analytics Health:", stdout.read().decode('utf-8', errors='replace'))

stdin, stdout, stderr = ssh.exec_command("curl -s http://127.0.0.1:8080/actuator/health")
print("Gateway Health:", stdout.read().decode('utf-8', errors='replace'))

ssh.close()
