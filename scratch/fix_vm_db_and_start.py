import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1')

def exec_cmd(cmd):
    print(f"\n[CMD] {cmd}")
    _, out, err = ssh.exec_command(cmd)
    o = out.read().decode('utf-8', errors='replace')
    e = err.read().decode('utf-8', errors='replace')
    if o:
        print(o)
    if e:
        print(f"ERR: {e}")

MYSQL_ROOT_PWD = "39c3df909277146fa5a381c6cb98752c5570a23724ec14a8"
LIVE_APP_PWD = "906c9b675fef499a974de8412b2a9599c7f5c64812af42bb"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"

# 1. Initialize MySQL database, tables, and grant permissions
exec_cmd(f"""docker exec -i $(docker ps -q -f name=mysql) mysql -uroot -p"{MYSQL_ROOT_PWD}" <<'SQL'
CREATE DATABASE IF NOT EXISTS `educloud_live` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'live_app'@'%' IDENTIFIED BY '{LIVE_APP_PWD}';
ALTER USER 'live_app'@'%' IDENTIFIED BY '{LIVE_APP_PWD}';
GRANT ALL PRIVILEGES ON `educloud_live`.* TO 'live_app'@'%';
FLUSH PRIVILEGES;
SQL
""")

# 2. Run Live DDL and Permissions
exec_cmd(f'mysql -uroot -p"{MYSQL_ROOT_PWD}" -h127.0.0.1 educloud_live < {REMOTE_ROOT}/deploy/sql/live/V000__technical_tables.sql')
exec_cmd(f'mysql -uroot -p"{MYSQL_ROOT_PWD}" -h127.0.0.1 educloud_live < {REMOTE_ROOT}/deploy/sql/live/V001__live_control_plane.sql')
exec_cmd(f'mysql -uroot -p"{MYSQL_ROOT_PWD}" -h127.0.0.1 educloud_user < {REMOTE_ROOT}/deploy/sql/user/V009__live_permissions.sql')

# 3. Kill old live service and restart
exec_cmd("pkill -f 'educloud-live' || true")
exec_cmd(f"cd {REMOTE_ROOT} && bash deploy/scripts/start-dev.sh")

# 4. Probe live service
exec_cmd("curl -s http://127.0.0.1:8096/actuator/health/readiness")
exec_cmd("curl -s http://127.0.0.1:8096/actuator/health")

ssh.close()
