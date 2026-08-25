import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
cmds = [
    "tail -25 /tmp/educloud-live/start-dev-m08c.log",
    "ps -eo pid,args | grep 'educloud-.*1.0.0' | grep -v grep",
    "tail -12 /tmp/educloud-live/payment.log",
    "tail -8 /tmp/educloud-live/gateway.log",
]
for c in cmds:
    print('===', c[:70])
    _, o, _ = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace')[-2200:])
ssh.close()
