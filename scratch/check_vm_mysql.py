import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1')

def exec_cmd(cmd):
    print(f"\n[CMD] {cmd}")
    _, out, err = ssh.exec_command(cmd)
    o = out.read().decode('utf-8', errors='replace')
    e = err.read().decode('utf-8', errors='replace')
    if o:
        print(o)
    if e:
        print(f"ERR: {e}")

exec_cmd("docker inspect $(docker ps -q -f name=mysql) --format '{{json .Config.Env}}'")

# Restart educloud-live
exec_cmd("pkill -f 'educloud-live' || true")
exec_cmd("cd /root/educloud/.worktrees/educloud-backend-foundation && bash deploy/scripts/start-dev.sh")

ssh.close()
