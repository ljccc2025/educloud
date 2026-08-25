import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
sql = ("SELECT c.id, c.lifecycle_status, c.pre_submit_lifecycle_status, "
       "c.published_version_id, c.draft_version_id, c.published_at "
       "FROM educloud_course.course c "
       "WHERE c.published_at >= '2026-08-24 00:00:00' ORDER BY c.published_at DESC LIMIT 5;")
_, o, e = ssh.exec_command('mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e "%s"' % sql)
print(o.read().decode('utf-8', errors='replace'))
print(e.read().decode('utf-8', errors='replace')[:300])
ssh.close()
