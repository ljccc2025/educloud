import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
_, o, e = ssh.exec_command('mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e "DESCRIBE educloud_course.course;"')
print(o.read().decode('utf-8', errors='replace'))
print(e.read().decode('utf-8', errors='replace')[:300])
ssh.close()
