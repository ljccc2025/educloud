import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=900):
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace') + e.read().decode('utf-8', errors='replace')
repo = '/root/educloud/.worktrees/educloud-backend-foundation/educloud-backend'
r = run(f"cd {repo} && mvn -pl educloud-order -am package -DskipTests 2>&1 | grep -E 'BUILD|Building Edu|Tests' | tail -8")
print(r)
print(run(f"ls -la {repo}/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar"))
ssh.close()
