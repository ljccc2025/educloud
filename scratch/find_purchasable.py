import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
sql = (
    "SELECT c.id, cv.title, c.lifecycle_status "
    "FROM educloud_course.course c "
    "JOIN educloud_course.course_version cv ON cv.id = c.published_version_id "
    "WHERE c.lifecycle_status='PUBLISHED' "
    "AND c.id NOT IN (SELECT course_id FROM educloud_course.course_enrollment "
    " WHERE student_id=2091029641632157697 AND status='ACTIVE') "
    "LIMIT 15;"
)
_, o, e = ssh.exec_command('mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e "%s"' % sql)
print(o.read().decode('utf-8', errors='replace'))
print(e.read().decode('utf-8', errors='replace')[:300])
ssh.close()
