"""M08 支付中心部署到 VM：上传 → 建库迁移 → V008 权限 → 编译 → 重启 → 健康检查。"""
import os
import posixpath
import time
import paramiko

VM_HOST = "192.168.100.136"
VM_USER = "root"
VM_PASS = "1"
LOCAL_ROOT = r"D:\microservice"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"
MYSQL_ROOT_PW = "39c3df909277146fa5a381c6cb98752c5570a23724ec14a8"

# M08 涉及的 modified 文件（payment 整目录单独递归上传）
FILES = [
    "educloud-backend/pom.xml",
    "educloud-backend/educloud-gateway/src/main/resources/application.yml",
    "educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/GatewayRouteContractTest.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/PaymentRefundListener.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/service/EnrollmentService.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/PaymentEventsConsumer.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/service/OrderService.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/OrderServiceImpl.java",
    "educloud-backend/educloud-order/src/main/resources/application.yml",
    "deploy/scripts/start-dev.sh",
    "deploy/sql/payment/V000__technical_tables.sql",
    "deploy/sql/payment/V001__init_payment_schema.sql",
    "deploy/sql/payment/V002__payment_seed_data.sql",
    "deploy/sql/user/V008__payment_permissions.sql",
]


def run(ssh, cmd, timeout=900, show=True):
    if show:
        print(f"\n[EXEC] {cmd[:220]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    if show and out.strip():
        print(out.strip()[-2500:])
    if show and err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-1200:]}")
    if show:
        print(f"[exit={status}]")
    return status, out


def upload_tree(sftp, ssh, local_dir, remote_dir):
    """递归上传目录（LF 行尾转换仅对文本文件）。"""
    run(ssh, f"mkdir -p {remote_dir}", show=False)
    text_ext = {".java", ".xml", ".yml", ".yaml", ".sql", ".sh", ".md", ".properties", ".txt", ".factories", ".imports"}
    count = 0
    for root, dirs, files in os.walk(local_dir):
        rel = os.path.relpath(root, local_dir).replace("\\", "/")
        remote_sub = remote_dir if rel == "." else posixpath.join(remote_dir, rel)
        run(ssh, f"mkdir -p {remote_sub}", show=False)
        for name in files:
            local_path = os.path.join(root, name)
            ext = os.path.splitext(name)[1].lower()
            remote_path = posixpath.join(remote_sub, name)
            if ext in text_ext:
                with open(local_path, "rb") as f:
                    data = f.read().replace(b"\r\n", b"\n")
                with sftp.open(remote_path, "wb") as rf:
                    rf.write(data)
            else:
                sftp.put(local_path, remote_path)
            count += 1
    return count


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=22, username=VM_USER, password=VM_PASS, timeout=20)
    sftp = ssh.open_sftp()

    # 1. 上传 modified 文件
    for rel in FILES:
        local_path = os.path.join(LOCAL_ROOT, rel.replace("/", os.sep))
        remote_path = posixpath.join(REMOTE_ROOT, rel)
        run(ssh, "mkdir -p " + posixpath.dirname(remote_path), show=False)
        with open(local_path, "rb") as f:
            data = f.read().replace(b"\r\n", b"\n")
        with sftp.open(remote_path, "wb") as rf:
            rf.write(data)
        print(f"[UPLOAD] {rel}")

    # 2. 递归上传 educloud-payment 整模块
    n = upload_tree(
        sftp, ssh,
        os.path.join(LOCAL_ROOT, "educloud-backend", "educloud-payment"),
        posixpath.join(REMOTE_ROOT, "educloud-backend", "educloud-payment"))
    print(f"[UPLOAD] educloud-payment 模块 {n} 个文件")

    run(ssh, f"sed -i 's/\\r$//' {REMOTE_ROOT}/deploy/scripts/start-dev.sh")

    # 3. 建库 + 用户 + 迁移
    setup_sql = (
        "CREATE DATABASE IF NOT EXISTS educloud_payment DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci; "
        "CREATE USER IF NOT EXISTS 'payment_app'@'%' IDENTIFIED BY 'b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95'; "
        "CREATE USER IF NOT EXISTS 'payment_app'@'localhost' IDENTIFIED BY 'b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95'; "
        "GRANT ALL PRIVILEGES ON educloud_payment.* TO 'payment_app'@'%'; "
        "GRANT ALL PRIVILEGES ON educloud_payment.* TO 'payment_app'@'localhost'; "
        "FLUSH PRIVILEGES;"
    )
    st, _ = run(ssh, f"mysql -uroot -p{MYSQL_ROOT_PW} -h127.0.0.1 -e \"{setup_sql}\"")
    if st != 0:
        print("!!! 建库失败，中止")
        return

    for v in ["V000__technical_tables.sql", "V001__init_payment_schema.sql", "V002__payment_seed_data.sql"]:
        st, _ = run(ssh, f"mysql -uroot -p{MYSQL_ROOT_PW} -h127.0.0.1 --default-character-set=utf8mb4 educloud_payment "
                         f"< {REMOTE_ROOT}/deploy/sql/payment/{v}")
        if st != 0:
            print(f"!!! 迁移 {v} 失败，中止")
            return
    print("[MIGRATE] payment V000-V002 完成")

    # 4. V008 权限种子（user 库，幂等）
    st, _ = run(ssh, f"mysql -uroot -p{MYSQL_ROOT_PW} -h127.0.0.1 --default-character-set=utf8mb4 educloud_user "
                     f"< {REMOTE_ROOT}/deploy/sql/user/V008__payment_permissions.sql")
    if st != 0:
        print("!!! V008 权限迁移失败，中止")
        return
    print("[MIGRATE] V008 payment 权限完成")

    # 5. 全量编译（含新模块）
    st, out = run(
        ssh,
        f"cd {REMOTE_ROOT}/educloud-backend && mvn package -DskipTests=true 2>&1 | tail -20; "
        "echo MVN_EXIT=${PIPESTATUS[0]}",
        timeout=1500)
    if "MVN_EXIT=0" not in out:
        print("!!! VM 编译失败，中止")
        return
    print("[BUILD] VM 全量编译成功")

    # 6. 重启受影响服务（gateway 路由、order 消费、course 退款监听、payment 新服务）
    run(ssh, "pkill -f educloud-payment-1.0.0 || true; pkill -f educloud-gateway-1.0.0 || true; "
             "pkill -f educloud-order-1.0.0 || true; pkill -f educloud-course-1.0.0 || true; sleep 4")
    run(ssh, f"cd {REMOTE_ROOT} && setsid nohup bash deploy/scripts/start-dev.sh "
             "> /tmp/educloud-live/start-dev-m08.log 2>&1 < /dev/null &", timeout=30)
    print("[RESTART] start-dev.sh 已触发（端口检测补启缺失服务）")

    # 7. 健康检查
    targets = [
        ("gateway", "http://127.0.0.1:8081/actuator/health"),
        ("course", "http://127.0.0.1:8090/actuator/health"),
        ("order", "http://127.0.0.1:8092/actuator/health"),
        ("payment", "http://127.0.0.1:8094/actuator/health"),
    ]
    deadline = time.time() + 420
    pending = dict(targets)
    while pending and time.time() < deadline:
        time.sleep(8)
        for name, url in list(pending.items()):
            _, o, _ = ssh.exec_command(f"curl -s -m 4 {url}")
            body = o.read().decode("utf-8", errors="replace")
            if '"UP"' in body:
                print(f"[UP] {name}")
                del pending[name]
    if pending:
        print(f"!!! 未就绪: {list(pending.keys())}")
        for name in pending:
            _, o, _ = ssh.exec_command(f"tail -25 /tmp/educloud-live/{name}.log")
            print(o.read().decode("utf-8", errors="replace")[-2000:])
    else:
        print("[DONE] 全部服务 UP")

    sftp.close()
    ssh.close()


if __name__ == "__main__":
    main()
