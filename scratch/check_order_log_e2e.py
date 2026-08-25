import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
_, o, _ = ssh.exec_command("grep -E 'COURSE_NOT_ON_SALE|无法获取课程|courseClient|FeignException|createOrder' /tmp/educloud-live/order.log | tail -20")
print(o.read().decode('utf-8', errors='replace')[-3000:])
_, o, _ = ssh.exec_command("grep -B2 -A12 'ERROR' /tmp/educloud-live/order.log | tail -40")
print(o.read().decode('utf-8', errors='replace')[-3000:])
ssh.close()
