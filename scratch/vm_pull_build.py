import paramiko, time
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=120):
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace') + e.read().decode('utf-8', errors='replace')

repo = '/root/educloud/.worktrees/educloud-backend-foundation'
print(run(f"cd {repo} && git pull origin main 2>&1 | tail -3"))
print(run(f"cd {repo} && git log --oneline -2"))

# VM 上编译 order 模块（本次唯一改动），验证 BUILD SUCCESS
print(run(f"cd {repo} && mvn -q -pl educloud-order -am package -DskipTests -Dmaven.repo.local=/root/.m2/repository 2>&1 | tail -5; echo EXIT=$?", timeout=600))
print(run(f"ls -la {repo}/educloud-backend/educloud-order/target/educloud-order-1.0.0-SNAPSHOT.jar"))
ssh.close()
