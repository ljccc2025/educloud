import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=600):
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace') + e.read().decode('utf-8', errors='replace')
repo = '/root/educloud/.worktrees/educloud-backend-foundation'
print(run(f"cd {repo} && mvn -pl educloud-order -am package -DskipTests 2>&1 | head -30"))
print("--- java/mvn env ---")
print(run("which mvn java; java -version 2>&1 | head -2; echo $JAVA_HOME"))
ssh.close()
