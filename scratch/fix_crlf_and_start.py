import sys
import time
import paramiko

sys.stdout.reconfigure(encoding='utf-8')

VM_HOST = "192.168.100.136"
VM_PORT = 22
VM_USER = "root"
VM_PASS = "1"
MYSQL_ROOT_PASS = "39c3df909277146fa5a381c6cb98752c5570a23724ec14a8"

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=VM_PORT, username=VM_USER, password=VM_PASS, timeout=30)
    
    print("=== [1] 转换 dos2unix 换行符 ===")
    ssh.exec_command("find /root/educloud/.worktrees/educloud-backend-foundation/deploy/scripts/ -type f -name '*.sh' -exec sed -i 's/\\r$//' {} +")
    
    print("=== [2] 执行 MySQL 表结构安全更新 (kind 列) ===")
    sql = """
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'educloud_notification' AND TABLE_NAME = 'sys_user_notification' AND COLUMN_NAME = 'kind');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE educloud_notification.sys_user_notification ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT \\'SYSTEM\\' COMMENT \\'通知分类冗余字段\\' AFTER notification_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
"""
    cmd_sql = f'docker exec -i educloud-mysql-1 mysql -u root -p{MYSQL_ROOT_PASS} << \'EOF\'\n{sql}\nEOF'
    stdin, stdout, stderr = ssh.exec_command(cmd_sql)
    print("SQL OUT:", stdout.read().decode('utf-8', errors='replace'))
    print("SQL ERR:", stderr.read().decode('utf-8', errors='replace'))
    
    print("=== [3] 重启服务并就绪检查 ===")
    ssh.exec_command("pkill -9 -f educloud-notification || true; rm -f /tmp/educloud-live/notification.log")
    time.sleep(1)
    
    cmd_start = "cd /root/educloud/.worktrees/educloud-backend-foundation && bash deploy/scripts/start-dev.sh"
    stdin, stdout, stderr = ssh.exec_command(cmd_start, get_pty=True)
    for line in stdout:
        print(line, end="")
        
    ssh.close()

if __name__ == "__main__":
    main()
