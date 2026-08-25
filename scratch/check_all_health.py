import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
cmd = """
for pair in user:8083 gateway:8081 course:8090 file:8088 content:8086 order:8092; do
  name=${pair%%:*}; port=${pair##*:}
  printf "%s: " "$name"; curl -s -m 3 http://127.0.0.1:$port/actuator/health/readiness; echo
done
for p in 5173 5174 5175; do
  printf "vite %s: " "$p"; curl -s -o /dev/null -w "%{http_code}" -m 5 http://127.0.0.1:$p; echo
done
"""
_, o, _ = ssh.exec_command(cmd)
print(o.read().decode('utf-8', errors='replace'))
ssh.close()
