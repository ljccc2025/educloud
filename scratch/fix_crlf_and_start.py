import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=10)

cmd = """
cd /root/educloud/.worktrees/educloud-backend-foundation
sed -i 's/\\r$//' deploy/scripts/start-dev.sh
bash deploy/scripts/start-dev.sh
"""

stdin, stdout, stderr = ssh.exec_command(cmd, timeout=120)
print(stdout.read().decode('utf-8'))
print(stderr.read().decode('utf-8'))

print("\n--- Checking Readiness ---")
_, o, _ = ssh.exec_command("curl -s http://127.0.0.1:8092/actuator/health/readiness; echo; curl -s http://127.0.0.1:8080/actuator/health")
print(o.read().decode('utf-8'))

ssh.close()
