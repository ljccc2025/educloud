import paramiko, time, json
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=30):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

print(run("pgrep -af 'educloud-order' | grep -v pgrep || echo NOT_RUNNING"))
print(run("curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8092/actuator/health"))
ssh.close()
