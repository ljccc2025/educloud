import os
import sys
import time
import paramiko

VM_HOST = "192.168.100.136"
VM_PORT = 22
VM_USER = "root"
VM_PASS = "1"
LOCAL_ROOT = "D:\\microservice"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"

def remote_makedirs(sftp, remote_path):
    dirs = []
    head = remote_path
    while head not in ("", "/", "."):
        dirs.append(head)
        head = os.path.dirname(head).replace("\\", "/")
    for d in reversed(dirs):
        try:
            sftp.mkdir(d)
        except Exception:
            pass

def sftp_upload_dir(sftp, local_dir, remote_dir):
    if not os.path.exists(local_dir):
        return
    for root, dirs, files in os.walk(local_dir):
        if any(x in root for x in ["target", "node_modules", ".git", ".idea"]):
            continue
        rel_path = os.path.relpath(root, local_dir)
        if rel_path == ".":
            target_remote_dir = remote_dir
        else:
            target_remote_dir = f"{remote_dir}/{rel_path}".replace("\\", "/")
            
        remote_makedirs(sftp, target_remote_dir)
        
        for file in files:
            local_file = os.path.join(root, file)
            remote_file = f"{target_remote_dir}/{file}".replace("\\", "/")
            sftp.put(local_file, remote_file)

def sftp_upload_file(sftp, local_file, remote_file):
    if not os.path.exists(local_file):
        return
    remote_makedirs(sftp, os.path.dirname(remote_file).replace("\\", "/"))
    sftp.put(local_file, remote_file)

def main():
    print(f"Connecting SSH & SFTP ({VM_HOST})...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=VM_PORT, username=VM_USER, password=VM_PASS, timeout=15)
    sftp = ssh.open_sftp()
    
    # 1. Sync backend modules
    print("\n--- Syncing Backend Modules ---")
    sftp_upload_file(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "pom.xml"), f"{REMOTE_ROOT}/educloud-backend/pom.xml")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-live"), f"{REMOTE_ROOT}/educloud-backend/educloud-live")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-gateway"), f"{REMOTE_ROOT}/educloud-backend/educloud-gateway")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-payment"), f"{REMOTE_ROOT}/educloud-backend/educloud-payment")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-order"), f"{REMOTE_ROOT}/educloud-backend/educloud-order")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-course"), f"{REMOTE_ROOT}/educloud-backend/educloud-course")
    
    # 2. Sync deploy scripts & SQL migrations
    print("\n--- Syncing Deploy & SQL ---")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "deploy", "sql", "live"), f"{REMOTE_ROOT}/deploy/sql/live")
    sftp_upload_file(sftp, os.path.join(LOCAL_ROOT, "deploy", "sql", "user", "V009__live_permissions.sql"), f"{REMOTE_ROOT}/deploy/sql/user/V009__live_permissions.sql")
    sftp_upload_file(sftp, os.path.join(LOCAL_ROOT, "deploy", "scripts", "start-dev.sh"), f"{REMOTE_ROOT}/deploy/scripts/start-dev.sh")
    
    # 3. Sync frontend src
    print("\n--- Syncing Frontend src ---")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-frontend", "student-portal", "src"), f"{REMOTE_ROOT}/educloud-frontend/student-portal/src")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-frontend", "teacher-portal", "src"), f"{REMOTE_ROOT}/educloud-frontend/teacher-portal/src")
    sftp_upload_dir(sftp, os.path.join(LOCAL_ROOT, "educloud-frontend", "admin-portal", "src"), f"{REMOTE_ROOT}/educloud-frontend/admin-portal/src")
    
    sftp.close()
    print("Files uploaded successfully via SFTP!")
    
    # Run migrations, build backend and restart
    commands = [
        # 1. Database & SQL migrations
        "mysql -uroot -p1 -h127.0.0.1 -e 'CREATE DATABASE IF NOT EXISTS educloud_live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_live < {REMOTE_ROOT}/deploy/sql/live/V000__technical_tables.sql",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_live < {REMOTE_ROOT}/deploy/sql/live/V001__live_control_plane.sql",
        f"mysql -uroot -p1 -h127.0.0.1 educloud_user < {REMOTE_ROOT}/deploy/sql/user/V009__live_permissions.sql",
        
        # 2. Stop old services before rebuilding
        "pkill -f 'educloud-live' || true",
        "pkill -f 'educloud-gateway' || true",
        
        # 3. Build backend module educloud-live & parent
        f"cd {REMOTE_ROOT}/educloud-backend && mvn clean package -DskipTests=true",
        
        # 4. Start all dev services
        f"cd {REMOTE_ROOT} && bash deploy/scripts/start-dev.sh"
    ]
    
    for cmd in commands:
        print(f"\n[EXEC] {cmd}")
        stdin, stdout, stderr = ssh.exec_command(cmd, timeout=300)
        out = stdout.read().decode('utf-8', errors='replace')
        err = stderr.read().decode('utf-8', errors='replace')
        status = stdout.channel.recv_exit_status()
        print(out)
        if status != 0:
            print(f"Error ({status}): {err}")
            
    print("\n--- Health Check on VM ---")
    services = [
        ("Gateway", "http://127.0.0.1:8081/actuator/health/readiness"),
        ("User", "http://127.0.0.1:8083/actuator/health/readiness"),
        ("Course", "http://127.0.0.1:8090/actuator/health/readiness"),
        ("Payment", "http://127.0.0.1:8094/actuator/health/readiness"),
        ("Order", "http://127.0.0.1:8092/actuator/health/readiness"),
        ("Live", "http://127.0.0.1:8096/actuator/health/readiness"),
    ]
    for name, url in services:
        stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 5 {url}")
        res = stdout.read().decode('utf-8')
        print(f"[{name}] {url} -> {res.strip()}")
        
    ssh.close()
    print("\nVM deployment and verification completed!")

if __name__ == "__main__":
    main()
