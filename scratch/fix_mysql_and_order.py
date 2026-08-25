import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=10)

sql_cmd = """
mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 << 'EOF'
CREATE DATABASE IF NOT EXISTS educloud_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'order_app'@'%' IDENTIFIED BY 'b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95';
ALTER USER 'order_app'@'%' IDENTIFIED BY 'b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95';
GRANT ALL PRIVILEGES ON educloud_order.* TO 'order_app'@'%';
FLUSH PRIVILEGES;
EOF
mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 educloud_order < /root/educloud/.worktrees/educloud-backend-foundation/deploy/sql/order/V000__technical_tables.sql
mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 educloud_order < /root/educloud/.worktrees/educloud-backend-foundation/deploy/sql/order/V001__init_order_schema.sql
mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 educloud_order < /root/educloud/.worktrees/educloud-backend-foundation/deploy/sql/order/V002__order_seed_data.sql
"""

print("[Step 1] Running SQL Grant and Migration...")
_, stdout, stderr = ssh.exec_command(sql_cmd)
print(stdout.read().decode('utf-8'))
print(stderr.read().decode('utf-8'))

ssh.close()
print("Grant done!")
