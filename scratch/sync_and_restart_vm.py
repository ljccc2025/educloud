import os
import sys
import time
import paramiko

VM_HOST = "192.168.100.136"
VM_PORT = 22
VM_USER = "root"
VM_PASS = "1"
REMOTE_WORKTREE = "/root/educloud/.worktrees/educloud-backend-foundation"

def run_ssh_command(client, cmd, timeout=180):
    print(f"\n[SSH Command] {cmd}")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    
    # Stream output
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    exit_status = stdout.channel.recv_exit_status()
    
    if out:
        print(f"[STDOUT]\n{out.strip()}")
    if err and exit_status != 0:
        print(f"[STDERR]\n{err.strip()}")
    print(f"[Exit Status] {exit_status}")
    return exit_status, out, err

def main():
    print(f"Connecting to VM {VM_HOST}...")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(VM_HOST, port=VM_PORT, username=VM_USER, password=VM_PASS, timeout=10)
    print("SSH Connected successfully!")
    
    # 1. Pull latest code in worktree / repo
    cmds = [
        f"cd {REMOTE_WORKTREE} && git remote set-url origin https://github.com/ljccc2025/educloud.git && git fetch origin && git checkout main && git reset --hard origin/main",
        # 2. Database migrations for educloud_order and user
        f"mysql -uroot -p1 -h127.0.0.1 -e 'CREATE DATABASE IF NOT EXISTS educloud_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_order < {REMOTE_WORKTREE}/deploy/sql/order/V000__technical_tables.sql",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_order < {REMOTE_WORKTREE}/deploy/sql/order/V001__init_order_schema.sql",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_order < {REMOTE_WORKTREE}/deploy/sql/order/V002__order_seed_data.sql",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_user < {REMOTE_WORKTREE}/deploy/sql/user/V007__order_permissions.sql",
        # 3. Build Backend
        f"cd {REMOTE_WORKTREE}/educloud-backend && mvn clean package -DskipTests=true",
        # 4. Restart dev environment
        f"cd {REMOTE_WORKTREE} && bash deploy/scripts/start-dev.sh"
    ]
    
    for cmd in cmds:
        status, out, err = run_ssh_command(client, cmd, timeout=300)
        if status != 0 and "start-dev.sh" not in cmd:
            print(f"Failed at command: {cmd}")
            # sys.exit(1)
            
    print("\nChecking readiness of all services...")
    time.sleep(5)
    run_ssh_command(client, "curl -s http://127.0.0.1:8092/actuator/health/readiness")
    run_ssh_command(client, "curl -s http://127.0.0.1:8081/actuator/health/readiness")
    
    client.close()
    print("\nSync and restart completed!")

if __name__ == "__main__":
    main()
