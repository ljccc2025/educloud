import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

def run(cmd, timeout=600):
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    return o.read().decode('utf-8', errors='replace') + e.read().decode('utf-8', errors='replace')

repo = '/root/educloud/.worktrees/educloud-backend-foundation'

# 同步本次唯一改动的源码文件
sftp = ssh.open_sftp()
local = r'd:\microservice\educloud-backend\educloud-order\src\main\java\com\educloud\order\service\impl\OrderServiceImpl.java'
remote = repo + '/educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/OrderServiceImpl.java'
sftp.put(local, remote)
sftp.close()
print("source synced")

# VM 上编译 order 模块验证
r = run(f"cd {repo} && mvn -pl educloud-order -am package -DskipTests 2>&1 | grep -E 'BUILD|ERROR' | tail -6; echo EXIT=$?")
print(r)
ssh.close()
