import sys
import paramiko

sys.stdout.reconfigure(encoding='utf-8')

VM_HOST = "192.168.100.136"
VM_USER = "root"
VM_PASS = "1"

pub_key_path = r"C:\Users\leijianchu\.ssh\id_ed25519.pub"
with open(pub_key_path, "r", encoding="utf-8") as f:
    pub_key = f.read().strip()

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(VM_HOST, 22, VM_USER, VM_PASS, timeout=10)

cmd = (
    "mkdir -p ~/.ssh && chmod 700 ~/.ssh && "
    "touch ~/.ssh/authorized_keys && "
    "grep -qF '{}' ~/.ssh/authorized_keys || echo '{}' >> ~/.ssh/authorized_keys; "
    "chmod 600 ~/.ssh/authorized_keys && echo KEY_INSTALLED"
).format(pub_key, pub_key)

stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode('utf-8', errors='replace'))
err = stderr.read().decode('utf-8', errors='replace')
if err:
    print("STDERR:", err)
ssh.close()
