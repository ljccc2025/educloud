import os
import hashlib
import paramiko

VM_HOST = "192.168.100.136"
VM_PORT = 22
VM_USER = "root"
VM_PASS = "1"
LOCAL_ROOT = "D:\\microservice"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"

def get_file_md5(path):
    with open(path, "rb") as f:
        return hashlib.md5(f.read().replace(b"\r\n", b"\n")).hexdigest()

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=VM_PORT, username=VM_USER, password=VM_PASS, timeout=15)
    sftp = ssh.open_sftp()
    
    print("=== 正在核对虚拟机与本地文件同步状态 ===")
    
    checked_count = 0
    synced_count = 0
    missing_files = []
    
    dirs_to_check = [
        "educloud-backend/educloud-live",
        "educloud-backend/educloud-gateway/src",
        "educloud-backend/educloud-course/src",
        "educloud-backend/educloud-file/src",
        "educloud-backend/educloud-payment/src",
        "educloud-backend/educloud-order/src",
        "deploy/sql/live",
        "deploy/sql/user",
        "deploy/scripts"
    ]
    
    for rel_dir in dirs_to_check:
        local_dir = os.path.join(LOCAL_ROOT, rel_dir.replace("/", "\\"))
        for root, dirs, files in os.walk(local_dir):
            if any(x in root for x in ["target", "node_modules", ".git", ".idea"]):
                continue
            for f in files:
                local_path = os.path.join(root, f)
                rel_f = os.path.relpath(local_path, LOCAL_ROOT).replace("\\", "/")
                remote_path = f"{REMOTE_ROOT}/{rel_f}"
                checked_count += 1
                try:
                    sftp.stat(remote_path)
                    synced_count += 1
                except FileNotFoundError:
                    missing_files.append(rel_f)
                    
    print(f"核对完成：已检查核心文件共 {checked_count} 个")
    print(f"已同步至虚拟机：{synced_count} 个")
    if missing_files:
        print(f"缺失文件 ({len(missing_files)}): {missing_files}")
    else:
        print(">>> 结论：所有核心代码、配置文件、数据库迁移脚本与部署脚本均已 100% 上传至虚拟机！ <<<")
        
    ssh.close()

if __name__ == "__main__":
    main()
