import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
cmds = [
    "cat /tmp/educloud-live/start-dev-m08b.log",
    "ps -eo pid,lstart,args | grep 'educloud-.*1.0.0' | grep -v grep",
    "ss -tlnp | grep -E ':(808[0-9]|809[0-9])' | head -20",
]
for c in cmds:
    print('===', c[:80])
    _, o, _ = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace')[-4000:])
ssh.close()
