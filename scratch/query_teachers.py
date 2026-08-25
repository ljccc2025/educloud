import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
sql = ("SELECT id, username, email, user_type, status FROM educloud_user.sys_user "
       "WHERE user_type='TEACHER' LIMIT 10;")
_, o, e = ssh.exec_command('mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e "%s"' % sql)
print(o.read().decode('utf-8', errors='replace'))
print(e.read().decode('utf-8', errors='replace')[:400])
ssh.close()
