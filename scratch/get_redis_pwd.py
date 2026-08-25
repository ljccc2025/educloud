import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=30):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')

# 从 payment 进程取环境变量里的 Redis 密码
pid = run("pgrep -f 'educloud-paymen[t]-1.0.0-SNAPSHOT.jar' | head -1").strip()
print("payment pid:", pid)
env = run(f"tr '\\0' '\\n' < /proc/{pid}/environ | grep -E '^REDIS_'")
print(env)
ssh.close()
