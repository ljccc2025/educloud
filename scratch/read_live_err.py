import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1')

_, out, _ = ssh.exec_command('grep -C 5 "Failed to query course enrollment" /tmp/educloud-live/live.log')
print(out.read().decode('utf-8', errors='replace'))

_, out2, _ = ssh.exec_command('grep -C 5 "Student not actively" /tmp/educloud-live/live.log')
print(out2.read().decode('utf-8', errors='replace'))

ssh.close()
