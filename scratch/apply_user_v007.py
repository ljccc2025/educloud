import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=10)

sql_cmd = """
mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 educloud_user < /root/educloud/.worktrees/educloud-backend-foundation/deploy/sql/user/V007__order_permissions.sql
"""

print("[Step 1] Applying V007 order permissions to educloud_user...")
_, stdout, stderr = ssh.exec_command(sql_cmd)
print(stdout.read().decode('utf-8'))
print(stderr.read().decode('utf-8'))

ssh.close()
print("Permissions applied!")
