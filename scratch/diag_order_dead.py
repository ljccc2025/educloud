import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
def run(cmd, timeout=30):
    _, o, _ = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace')
cmds = [
    "ls -la /tmp/educloud-live/order.log",
    "wc -l /tmp/educloud-live/order.log",
    "tail -25 /tmp/educloud-live/order.log",
    "ps -o pid,stat,rss,etime -p 1458457",
    "ss -tlnp | grep -E '8091|8092' || echo NO_LISTEN",
]
for c in cmds:
    print('===', c[:60])
    print(run(c))
ssh.close()
