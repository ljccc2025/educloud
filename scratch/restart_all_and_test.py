import time
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

# Kill backend services to load latest jars
exec_cmd("pkill -f 'educloud-course' || true")
exec_cmd("pkill -f 'educloud-file' || true")
exec_cmd("pkill -f 'educloud-live' || true")
exec_cmd("pkill -f 'educloud-gateway' || true")
time.sleep(2)

# Start dev services
exec_cmd("cd /root/educloud/.worktrees/educloud-backend-foundation && bash deploy/scripts/start-dev.sh")

ssh.close()
