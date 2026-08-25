import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
sql = """
SELECT cw.id, cw.course_id, cw.free_preview, cw.status, cw.content_revision_id,
       cc.published_revision_id
FROM educloud_content.courseware cw
LEFT JOIN educloud_content.course_content cc ON cc.course_id = cw.course_id
WHERE cw.status = 'ACTIVE'
LIMIT 20;
"""
cmd = 'mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e "%s"' % sql.replace('\n', ' ')
_, o, e = ssh.exec_command(cmd)
print(o.read().decode('utf-8', errors='replace'))
print(e.read().decode('utf-8', errors='replace')[:500])
ssh.close()
